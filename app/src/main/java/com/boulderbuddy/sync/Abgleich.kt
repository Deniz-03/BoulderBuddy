package com.boulderbuddy.sync

/**
 * Der Vergleich (Sync-Plan S2) — das Herzstück, absichtlich ohne eine Zeile Android.
 *
 * Hier entscheidet sich, ob Daten überleben. Überall sonst kostet ein Fehler höchstens einen
 * Transfer, den man wiederholt. Deshalb: reine Funktionen, alles über Werte, keine Datenbank,
 * keine Nebenwirkungen — und Tests, die jeden Fall aus den fünf Ablauf-Runden abbilden.
 *
 * Der Ablauf in drei Schritten:
 *
 * 1. [darfIchLesen] — passt das Schema überhaupt zusammen? (E7)
 * 2. [lage] — gibt es einen gemeinsamen Stand, oder ist es eine Erstbegegnung? (E10)
 * 3. [abgleich] → [anwenden] — was ist zu tun, und was davon muss der Mensch entscheiden?
 */

// ---------------------------------------------------------------------------
// 1. Schema
// ---------------------------------------------------------------------------

/** Ergebnis der Schema-Prüfung vor dem Verbindungsaufbau (E7). */
sealed interface Schemapruefung {
    data object Passt : Schemapruefung

    /** Die Gegenseite ist neuer — **dieses** Gerät muss aktualisiert werden. */
    data object DiesesGeraetAktualisieren : Schemapruefung

    /** Dieses Gerät ist neuer — die **Gegenseite** muss aktualisiert werden. */
    data object GegenseiteAktualisieren : Schemapruefung
}

/**
 * Bricht ab, statt zu raten (E7).
 *
 * Auch der umgekehrte Fall wird abgelehnt, obwohl die App den fremden Stand technisch
 * hochmigrieren könnte: die Gegenseite kennt die neuen Spalten dann trotzdem nicht und
 * bekäme Zeilen, mit denen sie nichts anfangen kann. Ein Abgleich, bei dem eine Seite
 * weniger versteht als die andere, ist kein Abgleich.
 */
fun darfIchLesen(meinSchema: Int, fremdesSchema: Int): Schemapruefung = when {
    fremdesSchema > meinSchema -> Schemapruefung.DiesesGeraetAktualisieren
    fremdesSchema < meinSchema -> Schemapruefung.GegenseiteAktualisieren
    else -> Schemapruefung.Passt
}

// ---------------------------------------------------------------------------
// 2. Lage
// ---------------------------------------------------------------------------

/** Wie die beiden Stände zueinander stehen. */
sealed interface Lage {
    /**
     * Mindestens eine Seite hat noch nie abgeglichen. Läuft nicht über [abgleich], sondern
     * über die Routine aus E10 (Datei ersetzen) — es gibt keinen gemeinsamen Stand, gegen
     * den sich „neu" von „dort gelöscht" unterscheiden ließe.
     */
    data object Erstbegegnung : Lage

    /** Beide kennen denselben letzten gemeinsamen Stand. Der Normalfall. */
    data class GemeinsamerStand(val generation: Long, val erzeugtVon: String) : Lage

    /**
     * Der fremde Stand ist aus meinem hervorgegangen: die Gegenseite hat bereits
     * abgeglichen, ich noch nicht. **Mein `basis.db` ist trotzdem der richtige gemeinsame
     * Vorfahr** — der Vergleich läuft also ganz normal, nur die Herkunft übernehme ich
     * anschließend unverändert von drüben (E3).
     *
     * Über Nearby kommt das nicht vor: dort rechnet ein Gerät und beide wenden gleichzeitig
     * an. Über den Datei-Weg (S8) ist es der **Normalfall**, denn dort läuft der Abgleich
     * zwangsläufig in zwei Durchgängen — erst liest das eine Gerät die Datei des anderen,
     * dann umgekehrt.
     */
    data class GegenseiteWeiter(val fremdeGeneration: Long) : Lage

    /**
     * Der fremde Stand ist ein Vorfahr meines eigenen — die Datei ist älter als das, was
     * hier schon steht. Einlesen wäre kein Fehler, aber sinnlos; sinnvoll ist der
     * umgekehrte Weg.
     */
    data object IchBinWeiter : Lage

    /**
     * Beide haben abgeglichen, aber nicht miteinander. `basis.db` beschriebe dann nicht den
     * gemeinsamen Stand, und ein Zeilenabgleich verglich gegen die falsche Vergangenheit.
     */
    data object KeinGemeinsamerStand : Lage
}

/**
 * Bestimmt die Lage allein aus der Herkunft — **nie über Zeitstempel** (E12): die Gerätezeit
 * ist nicht verlässlich, und der ganze Plan kommt ohne Zeitvergleich aus.
 *
 * Auch nicht über `geaendertSeitAbgleich`: der Schalter ist ein Hinweis fürs UI und kann
 * schlicht falsch stehen (Ablauf 35). Ob und wer etwas geändert hat, sagt allein der
 * Vergleich mit der Basis — siehe [Abgleichplan.veraenderung].
 *
 * `basiertAuf` hält nur die Vorgänger-Generation fest, nicht die ganze Ahnenreihe. Für zwei
 * Geräte genügt das; bei dreien wäre eine Basis je Gegenstelle nötig (O3).
 */
fun lage(meine: StandMeta?, fremde: StandMeta?): Lage = when {
    meine == null || fremde == null -> Lage.Erstbegegnung
    meine.generation == fremde.generation && meine.erzeugtVon == fremde.erzeugtVon ->
        Lage.GemeinsamerStand(meine.generation, meine.erzeugtVon)
    fremde.basiertAuf == meine.generation -> Lage.GegenseiteWeiter(fremde.generation)
    meine.basiertAuf == fremde.generation -> Lage.IchBinWeiter
    else -> Lage.KeinGemeinsamerStand
}

/**
 * Welche Herkunft der Stand nach dem Abgleich trägt (E3).
 *
 * **Beide Geräte müssen hinterher dieselben drei Werte tragen** — sonst läse die nächste
 * Lagebestimmung „auseinandergelaufen", wo Einigkeit herrscht (Ablauf 32), böte eine
 * Erstbegegnung an und kostete einen der beiden Stände.
 *
 * Daraus folgen zwei Fälle, und sie zu vermischen ist genau der Fehler:
 *
 * - [Lage.GegenseiteWeiter]: die Gegenseite hat bereits gerechnet und trägt die neue
 *   Herkunft schon. Sie wird **unverändert übernommen**, nicht fortgezählt.
 * - sonst: dieses Gerät rechnet, also entsteht hier eine neue Generation mit der eigenen ID.
 */
fun neueHerkunft(wo: Lage, meine: StandMeta?, fremde: StandMeta?, ich: String): StandMeta =
    when {
        wo is Lage.GegenseiteWeiter && fremde != null -> fremde
        else -> StandMeta(
            generation = (meine?.generation ?: 0) + 1,
            erzeugtVon = ich,
            basiertAuf = meine?.generation,
        )
    }

// ---------------------------------------------------------------------------
// 3. Vergleich
// ---------------------------------------------------------------------------

/** Wie eine einzelne Zeile gegenüber der Basis dasteht. */
enum class Urteil {
    /** Auf beiden Seiten unverändert. */
    UNVERAENDERT,

    /** Auf beiden Seiten gelöscht. */
    BEIDE_GELOESCHT,

    /**
     * Gleiche Nummer, gleicher Inhalt, der Basis unbekannt — also einig (Ablauf 15).
     * Entsteht, wenn ein Abgleich nach dem Anwenden abbrach, bevor `basis.db` geschrieben
     * war: die Zeilen sähen sonst beim nächsten Mal „neu" aus, obwohl beide sie haben.
     */
    EINIG,

    /** Beide haben dieselbe Änderung gemacht. */
    BEIDE_GLEICH_GEAENDERT,

    NUR_MEINS_NEU,
    NUR_FREMDES_NEU,
    MEINS_GEAENDERT,
    FREMDES_GEAENDERT,

    /** Hier gelöscht, drüben unberührt → verschwindet überall. */
    MEINS_GELOESCHT,

    /** Drüben gelöscht, hier unberührt → verschwindet überall. */
    FREMDES_GELOESCHT,

    /** Der Mensch muss entscheiden (E12). */
    KONFLIKT,
}

/** Urteil über eine Zeile. */
data class Befund(
    val tabelle: String,
    val id: Int,
    val urteil: Urteil,
    val art: KonfliktArt? = null,
)

/** Wer sich seit dem gemeinsamen Stand bewegt hat. */
enum class Veraenderung { KEINE, NUR_ICH, NUR_GEGENSEITE, BEIDE }

/**
 * Das Ergebnis des Vergleichs, noch ohne Entscheidung. Enthält alles, was [anwenden]
 * braucht — [anwenden] liest keine Datenbank mehr.
 */
class Abgleichplan internal constructor(
    val befunde: List<Befund>,
    val konflikte: List<Konflikt>,
    internal val meine: Stand,
    internal val fremde: Stand,
) {
    /**
     * „Gleich / eine Seite weiter / beide weiter" — abgelesen am Vergleich mit der Basis,
     * nicht am Änderungs-Schalter. Ein Hinweis fürs UI; gerechnet wird ohnehin über die
     * [befunde].
     */
    val veraenderung: Veraenderung by lazy {
        var ich = false
        var gegen = false
        for (b in befunde) {
            when (b.urteil) {
                Urteil.NUR_MEINS_NEU, Urteil.MEINS_GEAENDERT, Urteil.MEINS_GELOESCHT -> ich = true
                Urteil.NUR_FREMDES_NEU, Urteil.FREMDES_GEAENDERT, Urteil.FREMDES_GELOESCHT ->
                    gegen = true
                // Ein Konflikt setzt beidseitige Bewegung voraus.
                Urteil.KONFLIKT, Urteil.BEIDE_GLEICH_GEAENDERT -> {
                    ich = true
                    gegen = true
                }
                Urteil.UNVERAENDERT, Urteil.BEIDE_GELOESCHT, Urteil.EINIG -> Unit
            }
        }
        when {
            ich && gegen -> Veraenderung.BEIDE
            ich -> Veraenderung.NUR_ICH
            gegen -> Veraenderung.NUR_GEGENSEITE
            else -> Veraenderung.KEINE
        }
    }
}

/**
 * Vergleicht drei Stände und sagt für jede Zeile, wie sie dasteht (E4).
 *
 * Verglichen wird **von oben nach unten entlang der Fremdschlüssel** und **nur über die
 * Spalten aus [STAND_TABELLEN]** — eine Spalte, die auf zwei Geräten verschieden aussehen
 * kann, darf nicht in einen Vergleich geraten (Ablauf 31, E15).
 *
 * @param basis der letzte gemeinsame Stand (`basis.db`). Ohne ihn ließe sich „neu
 *   hinzugefügt" nicht von „dort gelöscht" unterscheiden; die Erstbegegnung läuft deshalb
 *   nicht hier durch, sondern über E10.
 */
fun abgleich(basis: Stand, meine: Stand, fremde: Stand): Abgleichplan {
    val befunde = mutableListOf<Befund>()

    for (tabelle in STAND_TABELLEN) {
        val ids = basis.ids(tabelle.name) + meine.ids(tabelle.name) + fremde.ids(tabelle.name)
        for (id in ids.sorted()) {
            val (urteil, art) = beurteile(
                tabelle,
                basis.zeile(tabelle.name, id),
                meine.zeile(tabelle.name, id),
                fremde.zeile(tabelle.name, id),
            )
            befunde += Befund(tabelle.name, id, urteil, art)
        }
    }

    val mitTeilbaum = pruefeTeilbaeume(befunde, meine, fremde)

    val konflikte = mitTeilbaum
        .filter { it.urteil == Urteil.KONFLIKT }
        .map { b ->
            Konflikt(
                tabelle = b.tabelle,
                id = b.id,
                art = b.art ?: KonfliktArt.BEIDSEITIG_GEAENDERT,
                meine = meine.zeile(b.tabelle, b.id),
                fremde = fremde.zeile(b.tabelle, b.id),
            )
        }

    return Abgleichplan(mitTeilbaum, konflikte, meine, fremde)
}

private fun beurteile(
    tabelle: Tabelle,
    basis: Zeile?,
    meine: Zeile?,
    fremde: Zeile?,
): Pair<Urteil, KonfliktArt?> {
    if (basis == null) {
        return when {
            meine != null && fremde == null -> Urteil.NUR_MEINS_NEU to null
            meine == null && fremde != null -> Urteil.NUR_FREMDES_NEU to null
            meine != null && fremde != null ->
                if (gleich(tabelle, meine, fremde)) {
                    Urteil.EINIG to null
                } else {
                    Urteil.KONFLIKT to KonfliktArt.GLEICHE_NUMMER
                }
            // Kommt nicht vor: die id stammt aus der Vereinigung der drei Stände.
            else -> Urteil.UNVERAENDERT to null
        }
    }

    if (meine == null && fremde == null) return Urteil.BEIDE_GELOESCHT to null

    if (meine == null) {
        // Hier gelöscht. Drüben unberührt ⇒ die Löschung gilt überall; drüben geändert
        // ⇒ echter Konflikt, denn die Löschung würde eine fremde Änderung mitnehmen.
        return if (gleich(tabelle, fremde!!, basis)) {
            Urteil.MEINS_GELOESCHT to null
        } else {
            Urteil.KONFLIKT to KonfliktArt.GELOESCHT_GEGEN_GEAENDERT
        }
    }
    if (fremde == null) {
        return if (gleich(tabelle, meine, basis)) {
            Urteil.FREMDES_GELOESCHT to null
        } else {
            Urteil.KONFLIKT to KonfliktArt.GELOESCHT_GEGEN_GEAENDERT
        }
    }

    val ichGeaendert = !gleich(tabelle, meine, basis)
    val gegenGeaendert = !gleich(tabelle, fremde, basis)
    return when {
        !ichGeaendert && !gegenGeaendert -> Urteil.UNVERAENDERT to null
        ichGeaendert && !gegenGeaendert -> Urteil.MEINS_GEAENDERT to null
        !ichGeaendert && gegenGeaendert -> Urteil.FREMDES_GEAENDERT to null
        gleich(tabelle, meine, fremde) -> Urteil.BEIDE_GLEICH_GEAENDERT to null
        else -> Urteil.KONFLIKT to KonfliktArt.BEIDSEITIG_GEAENDERT
    }
}

/**
 * Vergleicht **nur** die gelisteten Spalten. Steht eine Spalte nicht in [Tabelle.spalten],
 * ist sie für den Abgleich nicht vorhanden — genau so bleiben gerätelokale Werte draußen.
 */
private fun gleich(tabelle: Tabelle, a: Zeile, b: Zeile): Boolean =
    tabelle.spalten.all { a[it] == b[it] }

/**
 * Hebt eine Löschung zum Konflikt, wenn im Teilbaum darunter auf der anderen Seite etwas
 * ergänzt oder geändert wurde (Ablauf 8).
 *
 * Zeilenweise ist „Session gelöscht" + „Boulder ergänzt" kein Widerspruch — das Ergebnis
 * wäre aber ein Boulder ohne Session. Entschieden wird deshalb von oben nach unten: wer den
 * Ast absägt, entscheidet über alles, was daran hängt.
 */
private fun pruefeTeilbaeume(
    befunde: List<Befund>,
    meine: Stand,
    fremde: Stand,
): List<Befund> {
    val nachSchluessel = befunde.associateBy { it.tabelle to it.id }

    fun bewegtAufSeite(tabelle: String, id: Int, meineSeite: Boolean): Boolean {
        val urteil = nachSchluessel[tabelle to id]?.urteil ?: return false
        return if (meineSeite) {
            urteil in setOf(Urteil.NUR_MEINS_NEU, Urteil.MEINS_GEAENDERT, Urteil.KONFLIKT)
        } else {
            urteil in setOf(Urteil.NUR_FREMDES_NEU, Urteil.FREMDES_GEAENDERT, Urteil.KONFLIKT)
        }
    }

    /** Läuft die CASCADE-Kanten hinab und fragt, ob dort jemand etwas angefasst hat. */
    fun teilbaumBewegt(tabelle: String, id: Int, seite: Stand, meineSeite: Boolean): Boolean {
        for (kind in kinderVon(tabelle)) {
            val bezuege = kind.eltern.filter {
                it.elternTabelle == tabelle && it.regel == Loeschregel.KASKADE
            }
            if (bezuege.isEmpty()) continue
            for ((kindId, zeile) in seite.zeilen(kind.name)) {
                val haengtDran = bezuege.any { zeile[it.spalte] == Feld.Zahl(id.toLong()) }
                if (!haengtDran) continue
                if (bewegtAufSeite(kind.name, kindId, meineSeite)) return true
                if (teilbaumBewegt(kind.name, kindId, seite, meineSeite)) return true
            }
        }
        return false
    }

    return befunde.map { befund ->
        when (befund.urteil) {
            // Ich habe gelöscht — die Gegenseite hat den Ast noch, also dort nachsehen.
            Urteil.MEINS_GELOESCHT ->
                if (teilbaumBewegt(befund.tabelle, befund.id, fremde, meineSeite = false)) {
                    befund.copy(urteil = Urteil.KONFLIKT, art = KonfliktArt.TEILBAUM)
                } else {
                    befund
                }

            Urteil.FREMDES_GELOESCHT ->
                if (teilbaumBewegt(befund.tabelle, befund.id, meine, meineSeite = true)) {
                    befund.copy(urteil = Urteil.KONFLIKT, art = KonfliktArt.TEILBAUM)
                } else {
                    befund
                }

            else -> befund
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Anwenden
// ---------------------------------------------------------------------------

/**
 * Macht aus dem Plan konkrete Anweisungen — je eine Liste für dieses Gerät und für die
 * Gegenseite. **Wer den Knopf gedrückt hat, rechnet** und schickt der Gegenseite das
 * Ergebnis, nicht die Aufgabe (E12, Ablauf 17).
 *
 * @param wahl gilt **ausschließlich für die konflikthaften Einträge** (Ablauf 23). Eine
 *   Antwort, die den ganzen Abgleich beträfe, machte das Gedächtnis aus E4 wertlos: der
 *   Nutzer verlöre einen ganzen Klettertag, weil er eine Ghost-Analyse behalten wollte.
 */
fun anwenden(plan: Abgleichplan, wahl: Seite): Anweisungen {
    val ziel = HashMap<Pair<String, Int>, Zeile?>()

    for (befund in plan.befunde) {
        val schluessel = befund.tabelle to befund.id
        val meine = plan.meine.zeile(befund.tabelle, befund.id)
        val fremde = plan.fremde.zeile(befund.tabelle, befund.id)
        ziel[schluessel] = when (befund.urteil) {
            Urteil.UNVERAENDERT, Urteil.EINIG, Urteil.BEIDE_GLEICH_GEAENDERT,
            Urteil.NUR_MEINS_NEU, Urteil.MEINS_GEAENDERT,
            -> meine

            Urteil.NUR_FREMDES_NEU, Urteil.FREMDES_GEAENDERT -> fremde

            Urteil.BEIDE_GELOESCHT, Urteil.MEINS_GELOESCHT, Urteil.FREMDES_GELOESCHT -> null

            Urteil.KONFLIKT -> if (wahl == Seite.MEINS) meine else fremde
        }
    }

    holeMitgeloeschteZurueck(ziel, plan)
    val bezuegeGeloest = folgeDenFremdschluesseln(ziel)

    val fuerMich = operationen(ziel, plan.meine)
    val fuerDieGegenseite = operationen(ziel, plan.fremde)

    val geloescht = ziel.count { (schluessel, z) ->
        z == null && (
            plan.meine.zeile(schluessel.first, schluessel.second) != null ||
                plan.fremde.zeile(schluessel.first, schluessel.second) != null
            )
    }

    return Anweisungen(
        fuerMich = fuerMich,
        fuerDieGegenseite = fuerDieGegenseite,
        bilanz = Bilanz(
            uebernommen = fuerMich.count { it !is Operation.Loeschen },
            abgegeben = fuerDieGegenseite.count { it !is Operation.Loeschen },
            geloescht = geloescht,
            konfliktVerluste = plan.konflikte.size,
            bezuegeGeloest = bezuegeGeloest,
        ),
    )
}

/**
 * Nimmt Löschungen zurück, die gar keine Entscheidung waren, sondern die Kaskade einer
 * anderen — falls diese andere den Konflikt verloren hat.
 *
 * Der Fall: auf dem Phone wird eine Session gelöscht, ihre Boulder verschwinden per CASCADE
 * mit. Zeilenweise sieht das aus wie „Phone hat auch jeden einzelnen Boulder gelöscht".
 * Entscheidet der Nutzer im Teilbaum-Konflikt dann für „Session behalten", käme die Session
 * zurück — aber ohne ihre alten Boulder, denn deren Löschung stünde ja unwidersprochen da.
 * Der Nutzer verlöre genau das, was er behalten wollte, und niemand hätte es je entschieden.
 *
 * Die Regel dahinter ist dieselbe wie in Ablauf 8, nur andersherum gelesen: **wer den Ast
 * absägt, entscheidet über alles, was daran hängt — und wenn er nicht absägen darf, hängt
 * auch alles wieder dran.**
 *
 * Läuft von oben nach unten, damit sich das über mehrere Ebenen fortsetzt (Session →
 * Workout → Sätze).
 */
private fun holeMitgeloeschteZurueck(
    ziel: HashMap<Pair<String, Int>, Zeile?>,
    plan: Abgleichplan,
) {
    for (tabelle in STAND_TABELLEN) {
        if (tabelle.eltern.none { it.regel == Loeschregel.KASKADE }) continue

        for (befund in plan.befunde.filter { it.tabelle == tabelle.name }) {
            // Auf welcher Seite steht die Zeile noch? Genau von dort kommt sie zurück.
            val ueberlebende = when (befund.urteil) {
                Urteil.MEINS_GELOESCHT -> plan.fremde
                Urteil.FREMDES_GELOESCHT -> plan.meine
                else -> continue
            }
            val loeschende = when (befund.urteil) {
                Urteil.MEINS_GELOESCHT -> plan.meine
                else -> plan.fremde
            }
            val zeile = ueberlebende.zeile(tabelle.name, befund.id) ?: continue

            val nurMitgerissen = tabelle.eltern
                .filter { it.regel == Loeschregel.KASKADE }
                .any { bezug ->
                    val wert = zeile[bezug.spalte]
                    if (wert !is Feld.Zahl) return@any false
                    val elternId = wert.wert.toInt()
                    // Fehlt die Elternzeile auf der löschenden Seite ebenfalls, war diese
                    // Löschung ihre Folge — und die Elternzeile hat gerade überlebt.
                    loeschende.zeile(bezug.elternTabelle, elternId) == null &&
                        ziel[bezug.elternTabelle to elternId] != null
                }

            if (nurMitgerissen) ziel[tabelle.name to befund.id] = zeile
        }
    }
}

/**
 * Zieht die Folgen einer Löschung durch den Baum, von oben nach unten:
 * CASCADE-Kinder verschwinden mit, SET-NULL-Verweise werden leer.
 *
 * Läuft in der Reihenfolge von [STAND_TABELLEN] — Eltern stehen dort vor ihren Kindern,
 * also ist das Ziel einer Elternzeile schon endgültig, wenn ihre Kinder drankommen. Ein
 * Kind, dessen Elternzeile über zwei Ebenen wegfällt, wird dadurch in einem Durchgang
 * miterfasst.
 *
 * @return wie viele Verweise leer wurden (für die Bilanz — „Route verliert den Grad").
 */
private fun folgeDenFremdschluesseln(ziel: HashMap<Pair<String, Int>, Zeile?>): Int {
    var bezuegeGeloest = 0

    for (tabelle in STAND_TABELLEN) {
        if (tabelle.eltern.isEmpty()) continue
        for (schluessel in ziel.keys.filter { it.first == tabelle.name }) {
            val zeile = ziel[schluessel] ?: continue
            val zuLoesendeBezuege = mutableListOf<String>()
            var faelltWeg = false

            for (bezug in tabelle.eltern) {
                val wert = zeile[bezug.spalte]
                if (wert !is Feld.Zahl) continue // NULL oder nicht gesetzt: nichts zu tun
                val elternSchluessel = bezug.elternTabelle to wert.wert.toInt()
                // Nur bekannte Elternzeilen zählen; unbekannte lässt der Abgleich in Ruhe.
                if (!ziel.containsKey(elternSchluessel)) continue
                if (ziel[elternSchluessel] != null) continue

                when (bezug.regel) {
                    Loeschregel.KASKADE -> faelltWeg = true
                    Loeschregel.AUF_NULL -> zuLoesendeBezuege += bezug.spalte
                }
            }

            // Fällt die Zeile ohnehin weg, ist ein gelöster Verweis keine Meldung wert.
            ziel[schluessel] = if (faelltWeg) {
                null
            } else {
                bezuegeGeloest += zuLoesendeBezuege.size
                zeile + zuLoesendeBezuege.associateWith { Feld.Leer }
            }
        }
    }
    return bezuegeGeloest
}

/**
 * Der Unterschied zwischen Ziel und dem Stand einer Seite, als Liste von Operationen.
 *
 * Reihenfolge ist hier keine Kosmetik: Einfügen und Ändern laufen von oben nach unten
 * (die Elternzeile ist da, bevor ein Kind auf sie zeigt), Löschen von unten nach oben
 * (kein Kind bleibt ohne Elternzeile zurück). Dass die Löschungen zuletzt kommen, ist
 * ebenfalls Absicht: ein gelöster SET-NULL-Verweis muss geschrieben sein, bevor die Zeile
 * verschwindet, auf die er zeigte.
 */
private fun operationen(
    ziel: Map<Pair<String, Int>, Zeile?>,
    seite: Stand,
): List<Operation> {
    val schreiben = mutableListOf<Operation>()
    val loeschen = mutableListOf<Operation>()

    for (tabelle in STAND_TABELLEN) {
        val schluessel = ziel.keys.filter { it.first == tabelle.name }.sortedBy { it.second }
        for ((_, id) in schluessel) {
            val sollZeile = ziel[tabelle.name to id]
            val istZeile = seite.zeile(tabelle.name, id)
            when {
                sollZeile == null && istZeile != null ->
                    loeschen += Operation.Loeschen(tabelle.name, id)

                sollZeile != null && istZeile == null ->
                    schreiben += Operation.Einfuegen(tabelle.name, id, sollZeile)

                sollZeile != null && istZeile != null && !gleich(tabelle, sollZeile, istZeile) ->
                    schreiben += Operation.Aendern(tabelle.name, id, sollZeile)
            }
        }
    }

    return schreiben + loeschen.reversed()
}
