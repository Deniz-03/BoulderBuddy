package com.boulderbuddy.sync

import android.database.sqlite.SQLiteDatabase
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.entity.StandMetaEntity
import com.boulderbuddy.sync.nearby.Anweisungspaket
import com.boulderbuddy.sync.nearby.Nachricht
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Was in einem Stand steckt — Zahlen statt Fachbegriffen, für die Erstbegegnung (E10). */
data class Bestandszahlen(
    val hallen: Int,
    val sessions: Int,
    val boulder: Int,
    val trainings: Int,
    val analysen: Int,
) {
    val leer: Boolean get() = hallen + sessions + boulder + trainings + analysen == 0
}

/** Was der Abgleich mit einem fremden Stand vorschlägt. */
sealed interface Abgleichvorschlag {

    /**
     * Der Normalfall: es gibt einen gemeinsamen Vorfahren, also wird zusammengeführt.
     * Sind [Abgleichplan.konflikte] leer, braucht es keine Rückfrage.
     */
    data class Zusammenfuehren(
        val plan: Abgleichplan,
        /**
         * Die Herkunft, die **beide** Geräte anschließend tragen. Öffentlich, weil das
         * rechnende Gerät sie der Gegenseite mitschicken muss — unverändert (E3, Ablauf 32).
         */
        val neueMeta: StandMetaEntity,
    ) : Abgleichvorschlag {
        val konflikte: List<Konflikt> get() = plan.konflikte
    }

    /**
     * Erste Begegnung — es gibt keinen gemeinsamen Stand, gegen den sich „neu" von „dort
     * gelöscht" unterscheiden ließe. Hier wird nicht zusammengeführt, sondern **ein Stand
     * gewählt** (E10). Die Zahlen sagen dem Nutzer, was er dabei aufgibt.
     */
    data class Erstbegegnung(
        val meine: Bestandszahlen,
        val fremde: Bestandszahlen,
        internal val datei: File,
        internal val fremdeGeraeteId: String,
    ) : Abgleichvorschlag

    /** Es gibt nichts zu tun — beide Stände sind gleich. */
    data object NichtsZuTun : Abgleichvorschlag

    /** Der Abgleich läuft nicht, weil er nicht sauber laufen kann (E9). */
    data class Abgelehnt(val grund: String) : Abgleichvorschlag
}

/**
 * Der Ablauf eines Abgleichs, unabhängig vom Transportweg (Sync-Plan S6/S8).
 *
 * Ob der fremde Stand über Nearby ankommt oder als Datei ausgewählt wurde, ändert nichts an
 * dem, was danach passiert. Deshalb kennt diese Klasse keinen Transport — sie bekommt eine
 * Datei und macht daraus einen Vorschlag, und auf Zuruf das Ergebnis.
 */
@Singleton
class Abgleicher @Inject constructor(
    private val datenbank: BoulderBuddyDatabase,
    private val dateien: AbgleichDateien,
    private val identitaet: GeraeteIdentitaet,
    private val sessionDao: SessionDao,
) {

    private val eigeneDb get() = datenbank.openHelper.writableDatabase

    /**
     * Prüft den fremden Stand und schlägt vor, was zu tun ist. Ändert **nichts**.
     *
     * Die Vorprüfungen stehen bewusst am Anfang (E9): ein Abgleich, der mittendrin an
     * fehlendem Platz scheitert, hinterlässt mehr Schaden als einer, der gar nicht erst
     * beginnt.
     */
    suspend fun pruefe(fremdeDatei: File): Abgleichvorschlag = withContext(Dispatchers.IO) {
        if (!fremdeDatei.exists()) {
            return@withContext Abgleichvorschlag.Abgelehnt("Die Datei ist nicht lesbar.")
        }
        // Eine laufende Session würde mitten im Anwenden weiterschreiben.
        if (sessionDao.observeActive().first() != null) {
            return@withContext Abgleichvorschlag.Abgelehnt(
                "Es läuft gerade eine Session. Beende sie zuerst.",
            )
        }
        if (!dateien.genugPlatz(fremdeDatei.length())) {
            return@withContext Abgleichvorschlag.Abgelehnt(
                "Auf dem Gerät ist zu wenig Platz frei.",
            )
        }

        // Erst hier zeigt sich, ob die gewählte Datei überhaupt eine Datenbank ist. Der Nutzer
        // wählt sie im System-Dateidialog aus, und der lässt jede Datei zu — ein Bild, ein PDF,
        // das eigene CSV. Ohne diesen Auffang stand die rohe SQLite-Meldung auf dem Bildschirm
        // („file is not a database (code 26 SQLITE_NOTADB)"), am Gerät gemessen und ausgerechnet
        // auf dem einen Bildschirm, der bewusst ohne Fachbegriffe auskommt.
        val fremdeDb = runCatching { oeffneNurLesend(fremdeDatei) }
            .getOrElse { return@withContext Abgleichvorschlag.Abgelehnt(KEINE_STANDDATEI) }
        fremdeDb.use { fremd ->
            val abfrage: Abfrage = { sql -> fremd.rawQuery(sql, null) }

            // Die eigene Version wird abgefragt, nicht als Konstante gepflegt — eine
            // Konstante, die jemand beim Schema-Update zu ändern vergisst, wäre schlimmer
            // als gar keine Prüfung.
            val meinSchema = liesSchemaVersion { eigeneDb.query(it) }
            // Die ERSTE Abfrage auf der fremden Datei, und damit die Stelle, an der sich
            // zeigt, ob es überhaupt eine Datenbank ist: `openDatabase` öffnet faul und
            // wirft noch nicht. Bewusst nur dieser eine Aufruf im Auffang — ein Fehler der
            // eigenen Datenbank soll nicht als „falsche Datei gewählt" durchgehen.
            val fremdesSchema = runCatching { liesSchemaVersion(abfrage) }
                .getOrElse { return@withContext Abgleichvorschlag.Abgelehnt(KEINE_STANDDATEI) }
            val schemaPasst = darfIchLesen(meinSchema, fremdesSchema)
            if (schemaPasst != Schemapruefung.Passt) {
                // E7: abbrechen statt raten — und dabei sagen, WELCHES Gerät zu
                // aktualisieren ist. „Versionen passen nicht" hilft niemandem weiter.
                val grund = when (schemaPasst) {
                    Schemapruefung.DiesesGeraetAktualisieren ->
                        "Der andere Stand kommt aus einer neueren Version der App. " +
                            "Aktualisiere zuerst dieses Gerät."
                    Schemapruefung.KeineBoulderBuddyDatei -> KEINE_STANDDATEI
                    else ->
                        "Der andere Stand kommt aus einer älteren Version der App. " +
                            "Aktualisiere zuerst das andere Gerät."
                }
                return@withContext Abgleichvorschlag.Abgelehnt(grund)
            }

            val meineMeta = liesStandMeta(eigeneDb)
            val fremdeMeta = liesStandMeta(abfrage)
            val ich = identitaet.eigeneId()

            when (val wo = lage(meineMeta, fremdeMeta)) {
                Lage.Erstbegegnung, Lage.KeinGemeinsamerStand ->
                    return@withContext Abgleichvorschlag.Erstbegegnung(
                        meine = zaehle { eigeneDb.query(it) },
                        fremde = zaehle(abfrage),
                        datei = fremdeDatei,
                        fremdeGeraeteId = fremdeMeta?.erzeugtVon ?: ich,
                    )

                Lage.IchBinWeiter -> return@withContext Abgleichvorschlag.Abgelehnt(
                    "Dieser Stand ist älter als der auf diesem Gerät. " +
                        "Gib stattdessen von hier aus ab.",
                )

                is Lage.GemeinsamerStand, is Lage.GegenseiteWeiter -> {
                    // Ohne Gedächtnis ließe sich „neu hinzugefügt" nicht von „dort gelöscht"
                    // unterscheiden — dann ist es trotz Herkunft eine Erstbegegnung.
                    if (!dateien.hatGedaechtnis()) {
                        return@withContext Abgleichvorschlag.Erstbegegnung(
                            meine = zaehle { eigeneDb.query(it) },
                            fremde = zaehle(abfrage),
                            datei = fremdeDatei,
                            fremdeGeraeteId = fremdeMeta?.erzeugtVon ?: ich,
                        )
                    }

                    val basis = oeffneNurLesend(dateien.basisDatei).use { b ->
                        liesStand { sql -> b.rawQuery(sql, null) }
                    }
                    val plan = abgleich(basis, liesStand(eigeneDb), liesStand(abfrage))

                    if (plan.konflikte.isEmpty() &&
                        plan.veraenderung == Veraenderung.KEINE &&
                        wo is Lage.GemeinsamerStand
                    ) {
                        return@withContext Abgleichvorschlag.NichtsZuTun
                    }

                    // Die Herkunft beschreibt den GEMEINSAMEN Stand — nach dem Abgleich
                    // müssen beide Geräte dieselben drei Werte tragen (E3, Ablauf 32).
                    //
                    // Daraus folgen zwei verschiedene Fälle, und sie zu vermischen kostet
                    // Daten: Bei `GegenseiteWeiter` hat die Gegenseite den Stand bereits
                    // gerechnet und trägt schon die neue Herkunft. Würde hier eine WEITERE
                    // Generation vergeben, stünden hinterher zwei Geräte mit gleichen Daten
                    // und verschiedener Herkunft da — und der nächste Abgleich läse daraus
                    // „auseinandergelaufen" und böte eine Erstbegegnung an, also den Verlust
                    // eines der beiden Stände.
                    val herkunft = neueHerkunft(wo, meineMeta, fremdeMeta, ich)
                    Abgleichvorschlag.Zusammenfuehren(
                        plan = plan,
                        neueMeta = StandMetaEntity(
                            generation = herkunft.generation,
                            erzeugtVon = herkunft.erzeugtVon,
                            basiertAuf = herkunft.basiertAuf,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Führt zusammen: Rückweg sichern → anwenden → **danach** das Gedächtnis auffrischen.
     *
     * Die Reihenfolge ist die Lehre aus Ablauf 15: das Gedächtnis darf der Wahrheit nie
     * voraus sein. Bricht es dazwischen ab, sehen die Zeilen beim nächsten Mal „neu" aus —
     * und werden als „gleiche Nummer, gleicher Inhalt, der Basis unbekannt" richtig als
     * einig erkannt.
     */
    suspend fun fuehreZusammen(
        vorschlag: Abgleichvorschlag.Zusammenfuehren,
        wahl: Seite,
    ): Bilanz = withContext(Dispatchers.IO) {
        val anweisungen = anwenden(vorschlag.plan, wahl)
        // Das Band folgt aus der NEUEN Herkunft, nicht aus einem gespeicherten Wert: dieses
        // Gerät hat gerechnet, also steht seine ID in `erzeugtVon` — es zählt im unteren
        // Fenster weiter, die Gegenseite im oberen (E8, korrigiert).
        val band = NummernBand.ausHerkunft(vorschlag.neueMeta.erzeugtVon, identitaet.eigeneId())

        dateien.merkeAlsVorher()
        wendeAn(eigeneDb, anweisungen.fuerMich, vorschlag.neueMeta, band)
        dateien.merkeAlsBasis()

        anweisungen.bilanz
    }

    /**
     * Übernimmt den fremden Stand ganz (Erstbegegnung, E10).
     *
     * Danach ist die App **nicht mehr benutzbar**, bis der Prozess neu startet — Room hält
     * noch die alte Datei offen. Der Aufrufer muss neu starten.
     */
    suspend fun uebernimmGanz(
        vorschlag: Abgleichvorschlag.Erstbegegnung,
        /**
         * Geräte-ID der Seite, deren Stand gewinnt. Über Nearby ist sie aus dem Handschlag
         * bekannt; über die Datei nicht — dort trägt der Empfänger sich selbst ein und
         * bekommt beim nächsten Durchgang das richtige Band (siehe [NummernBand.ausHerkunft]).
         */
        herkunftVon: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            val ich = identitaet.eigeneId()
            val fremdeMeta = oeffneNurLesend(vorschlag.datei).use { f ->
                liesStandMeta { sql -> f.rawQuery(sql, null) }
            }

            dateien.ersetzeStand(vorschlag.datei)

            // Room ist zu, die Datei ist ersetzt — jetzt die Herkunft geradeziehen, bevor
            // irgendwer sie liest. In der empfangenen Datei steht die des anderen Geräts
            // (Ablauf 33); übernommen wird sie unverändert, weil sie den GEMEINSAMEN Stand
            // beschreibt (E3). Das Band steht bewusst nicht darin und wird lokal vergeben.
            SQLiteDatabase.openDatabase(
                dateien.standDatei.path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                val meta = fremdeMeta ?: StandMeta(1, herkunftVon ?: ich, null)
                db.execSQL(
                    "INSERT OR REPLACE INTO stand_meta (id, generation, erzeugtVon, basiertAuf) " +
                        "VALUES (?, ?, ?, ?)",
                    arrayOf(
                        StandMetaEntity.EINZIGE_ZEILE,
                        meta.generation,
                        meta.erzeugtVon,
                        meta.basiertAuf,
                    ),
                )
            }

            identitaet.koppele(vorschlag.fremdeGeraeteId)
            // Das Gedächtnis ist ab jetzt genau der übernommene Stand.
            dateien.basisDatei.delete()
            vorschlag.datei.copyTo(dateien.basisDatei, overwrite = true)
        }

    /**
     * Die **Gewinner**-Seite einer Erstbegegnung: der eigene Stand bleibt, bekommt aber eine
     * Herkunft, damit beide Geräte danach dieselbe tragen (E3).
     *
     * Ohne das stünde der Gewinner ohne `stand_meta` da — der nächste Abgleich läse wieder
     * „Erstbegegnung", und beim Nummernband fiele er auf 0 zurück, dasselbe Fenster wie der
     * Verlierer (Ablauf 7).
     */
    suspend fun merkeErstbegegnungGewonnen(partnerId: String) = withContext(Dispatchers.IO) {
        val ich = identitaet.eigeneId()
        schreibeStandMeta(
            eigeneDb,
            StandMetaEntity(generation = 1, erzeugtVon = ich, basiertAuf = null),
        )
        identitaet.koppele(partnerId)
        // Ab jetzt ist genau dieser Stand der gemeinsame — also auch das Gedächtnis.
        dateien.merkeAlsBasis()
    }

    /**
     * Nimmt zurück, was der letzte Abgleich auf **diesem** Gerät geändert hat (E13).
     *
     * Zurückgesetzt werden nur die Datentabellen. `stand_meta` und die Generation bleiben
     * stehen: aus Sicht des Modells ist das Rückgängigmachen **eine neue Änderung, keine
     * Rückkehr** — nur so wandert sie beim nächsten Abgleich weiter, statt dort wieder
     * aufgehoben zu werden (Ablauf 24).
     */
    suspend fun machRueckgaengig(): Boolean = withContext(Dispatchers.IO) {
        if (!dateien.kannRueckgaengig()) return@withContext false
        val band = NummernBand.ausHerkunft(
            liesStandMeta(eigeneDb)?.erzeugtVon,
            identitaet.eigeneId(),
        )
        val vorher = oeffneNurLesend(dateien.vorherDatei).use { v ->
            liesStand { sql -> v.rawQuery(sql, null) }
        }
        setzeDatentabellenZurueck(eigeneDb, vorher, band)
        dateien.verwirfVorher()
        true
    }

    /** Der eigene Stand als Datei, fertig zum Abgeben. */
    suspend fun gibStandAb(nach: File): File = dateien.kopiereStand(nach)

    /**
     * Alles, was die Gegenseite über dieses Gerät wissen muss, um den Handschlag zu prüfen
     * und die Rolle zu bestimmen (S5).
     *
     * In **einer** Nachricht, damit beide Seiten gleichzeitig entscheiden können und niemand
     * auf eine Rückfrage wartet.
     */
    suspend fun beschreibeMich(hatGedrueckt: Boolean, geraeteId: String): Nachricht.Hallo =
        withContext(Dispatchers.IO) {
            dateien.checkpoint()
            val meta = liesStandMeta(eigeneDb)
            Nachricht.Hallo(
                geraeteId = geraeteId,
                schemaVersion = liesSchemaVersion { eigeneDb.query(it) },
                laufendeSession = sessionDao.observeActive().first() != null,
                generation = meta?.generation,
                erzeugtVon = meta?.erzeugtVon,
                basiertAuf = meta?.basiertAuf,
                hatGedrueckt = hatGedrueckt,
                standGroesse = dateien.standDatei.length(),
                freierPlatz = dateien.freierPlatz(),
            )
        }

    /**
     * Wendet das fertige Ergebnis des **anderen** Geräts an (E12, Ablauf 17).
     *
     * Dieses Gerät rechnet dabei nichts nach. Beide rechnen zu lassen hieße, sich darauf zu
     * verlassen, dass zweimal dasselbe herauskommt — bei gleichem Code stimmt das auch, bis
     * es einmal nicht stimmt und zwei Geräte mit verschiedenen Daten dastehen.
     *
     * Die Herkunft wird **unverändert** übernommen: sie beschreibt den gemeinsamen Stand, und
     * ein eigenes `erzeugtVon` ließe die nächste Lagebestimmung „auseinandergelaufen" lesen,
     * wo Einigkeit herrscht (E3, Ablauf 32).
     */
    suspend fun wendeFremdesPaketAn(paket: Anweisungspaket): Bilanz = withContext(Dispatchers.IO) {
        val meta = StandMetaEntity(
            generation = paket.generation,
            erzeugtVon = paket.erzeugtVon,
            basiertAuf = paket.basiertAuf,
        )
        // Gerechnet hat die Gegenseite, also steht dort ihre ID — dieses Gerät zählt im
        // oberen Fenster weiter. Beide kommen so ohne Absprache auf verschiedene Bänder.
        val band = NummernBand.ausHerkunft(meta.erzeugtVon, identitaet.eigeneId())

        dateien.merkeAlsVorher()
        wendeAn(eigeneDb, paket.operationen, meta, band)
        dateien.merkeAlsBasis()

        // Die Bilanz wird hier aus den Operationen abgelesen, nicht mitgeschickt: was
        // „abgegeben" wurde, weiß nur die Gegenseite, und Konflikte hat sie entschieden.
        // Lieber eine ehrlich unvollständige Bilanz als eine erfundene vollständige.
        Bilanz(
            uebernommen = paket.operationen.count { it !is Operation.Loeschen },
            abgegeben = 0,
            geloescht = paket.operationen.count { it is Operation.Loeschen },
            konfliktVerluste = 0,
            bezuegeGeloest = 0,
        )
    }

    private fun oeffneNurLesend(datei: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(datei.path, null, SQLiteDatabase.OPEN_READONLY)

    private companion object {
        /**
         * Die Antwort auf jede Datei, die nichts mit dieser App zu tun hat.
         *
         * Zwei Wege führen hierher, und beide sind am Gerät aufgetreten: die Datei ist gar
         * keine Datenbank (Bild, PDF, das eigene CSV), oder sie ist eine — nur nicht unsere.
         * Der Satz sagt deshalb nicht nur, was falsch ist, sondern welche Datei gemeint war.
         */
        const val KEINE_STANDDATEI =
            "Diese Datei ist kein BoulderBuddy-Stand. Nimm die Datei, die das andere Gerät " +
                "über „Stand abgeben\" erzeugt hat."
    }

    private fun zaehle(abfrage: Abfrage) = Bestandszahlen(
        hallen = zaehleZeilen(abfrage, "gym"),
        sessions = zaehleZeilen(abfrage, "session"),
        boulder = zaehleZeilen(abfrage, "route"),
        trainings = zaehleZeilen(abfrage, "hangboard_workout"),
        analysen = zaehleZeilen(abfrage, "ghost_analysis"),
    )
}
