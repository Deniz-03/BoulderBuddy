package com.boulderbuddy.sync

import android.database.sqlite.SQLiteDatabase
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.entity.StandMetaEntity
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
        internal val neueMeta: StandMetaEntity,
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

        oeffneNurLesend(fremdeDatei).use { fremd ->
            val abfrage: Abfrage = { sql -> fremd.rawQuery(sql, null) }

            // Die eigene Version wird abgefragt, nicht als Konstante gepflegt — eine
            // Konstante, die jemand beim Schema-Update zu ändern vergisst, wäre schlimmer
            // als gar keine Prüfung.
            val meinSchema = liesSchemaVersion { eigeneDb.query(it) }
            val schemaPasst = darfIchLesen(meinSchema, liesSchemaVersion(abfrage))
            if (schemaPasst != Schemapruefung.Passt) {
                // E7: abbrechen statt raten — und dabei sagen, WELCHES Gerät zu
                // aktualisieren ist. „Versionen passen nicht" hilft niemandem weiter.
                val grund = when (schemaPasst) {
                    Schemapruefung.DiesesGeraetAktualisieren ->
                        "Der andere Stand kommt aus einer neueren Version der App. " +
                            "Aktualisiere zuerst dieses Gerät."
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

                    // Die Herkunft bestimmt, wer rechnet — und beide übernehmen sie
                    // unverändert (E3). Beim Datei-Weg rechnet, wer einliest.
                    val vorige = meineMeta?.generation ?: 0
                    val fremdeGen = fremdeMeta?.generation ?: 0
                    Abgleichvorschlag.Zusammenfuehren(
                        plan = plan,
                        neueMeta = StandMetaEntity(
                            generation = maxOf(vorige, fremdeGen) + 1,
                            erzeugtVon = ich,
                            basiertAuf = vorige,
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
        val band = identitaet.identitaet.first().band ?: 0

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
    suspend fun uebernimmGanz(vorschlag: Abgleichvorschlag.Erstbegegnung) =
        withContext(Dispatchers.IO) {
            val ich = identitaet.eigeneId()
            val band = NummernBand.bandFuer(ich, vorschlag.fremdeGeraeteId)
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
                val meta = fremdeMeta ?: StandMeta(1, vorschlag.fremdeGeraeteId, null)
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

            identitaet.koppele(vorschlag.fremdeGeraeteId, band)
            // Das Gedächtnis ist ab jetzt genau der übernommene Stand.
            dateien.basisDatei.delete()
            vorschlag.datei.copyTo(dateien.basisDatei, overwrite = true)
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
        val band = identitaet.identitaet.first().band ?: 0
        val vorher = oeffneNurLesend(dateien.vorherDatei).use { v ->
            liesStand { sql -> v.rawQuery(sql, null) }
        }
        setzeDatentabellenZurueck(eigeneDb, vorher, band)
        dateien.vorherDatei.delete()
        true
    }

    /** Der eigene Stand als Datei, fertig zum Abgeben. */
    suspend fun gibStandAb(nach: File): File = dateien.kopiereStand(nach)

    private fun oeffneNurLesend(datei: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(datei.path, null, SQLiteDatabase.OPEN_READONLY)

    private fun zaehle(abfrage: Abfrage) = Bestandszahlen(
        hallen = zaehleZeilen(abfrage, "gym"),
        sessions = zaehleZeilen(abfrage, "session"),
        boulder = zaehleZeilen(abfrage, "route"),
        trainings = zaehleZeilen(abfrage, "hangboard_workout"),
        analysen = zaehleZeilen(abfrage, "ghost_analysis"),
    )
}
