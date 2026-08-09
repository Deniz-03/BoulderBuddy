package com.boulderbuddy.sync.nearby

import com.boulderbuddy.sync.Operation
import com.boulderbuddy.sync.Schemapruefung
import com.boulderbuddy.sync.StandMeta
import com.boulderbuddy.sync.darfIchLesen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Was die beiden Geräte einander sagen (Sync-Plan S5) — und wie sie sich einig werden, wer
 * rechnet.
 *
 * Bewusst android-frei: der Gesprächsablauf ist die Stelle, an der ein Fehler den Abgleich
 * hängen lässt oder zwei Geräte gleichzeitig rechnen lässt. Das soll sich auf der JVM prüfen
 * lassen, nicht erst mit zwei Geräten auf dem Tisch.
 *
 * Kleine Nachrichten gehen als BYTES-Payload, große Dinge als FILE-Payload: **Nearby deckelt
 * BYTES bei rund 32 KB.** Der Stand allein ist über 100 KB, die Anweisungen können bei einem
 * großen Abgleich beliebig wachsen — beides muss als Datei gehen (Ablauf 22). Eine Datei wird
 * immer durch eine [Nachricht.DateiFolgt] angekündigt, weil Nearby beim Empfang nur eine
 * Payload-Nummer liefert und sonst niemand wüsste, was da gerade angekommen ist.
 */
@Serializable
sealed interface Nachricht {

    /**
     * Der Handschlag. Enthält alles, was für die Vorprüfungen (E9) und die Rollenwahl (E12)
     * nötig ist — in **einer** Nachricht, damit beide Seiten gleichzeitig entscheiden können
     * und niemand auf eine Rückfrage warten muss.
     */
    @Serializable
    @SerialName("hallo")
    data class Hallo(
        val protokoll: Int = PROTOKOLL_VERSION,
        val geraeteId: String,
        val schemaVersion: Int,
        /** Ein Abgleich läuft nicht, während auf einer der Seiten eine Session läuft (E9). */
        val laufendeSession: Boolean,
        /** Herkunft des eigenen Standes; `null` = noch nie abgeglichen. */
        val generation: Long? = null,
        val erzeugtVon: String? = null,
        val basiertAuf: Long? = null,
        /** Hat der Nutzer auf **diesem** Gerät den Knopf gedrückt? */
        val hatGedrueckt: Boolean,
        /** Größe des eigenen Standes in Bytes — für die Platzprüfung der Gegenseite. */
        val standGroesse: Long,
        /** Freier Speicher auf diesem Gerät. */
        val freierPlatz: Long,
    ) : Nachricht {
        val meta: StandMeta?
            get() = if (generation == null || erzeugtVon == null) {
                null
            } else {
                StandMeta(generation, erzeugtVon, basiertAuf)
            }
    }

    /** Abbruch mit Begründung. Der Empfänger zeigt sie an und räumt auf. */
    @Serializable
    @SerialName("abbruch")
    data class Abbruch(val grund: String) : Nachricht

    /** Die Namen aller vorhandenen Medien — der Name ist der Hash (E5). */
    @Serializable
    @SerialName("medienliste")
    data class Medienliste(val namen: List<String>) : Nachricht

    /**
     * Kündigt eine FILE-Payload an. Ohne das käme drüben nur eine Nummer und eine Datei an,
     * und niemand wüsste, ob es der Stand, ein Video oder das Ergebnis ist.
     */
    @Serializable
    @SerialName("dateiFolgt")
    data class DateiFolgt(
        val payloadId: Long,
        val art: DateiArt,
        /** Bei [DateiArt.MEDIUM] der inhaltsadressierte Name, sonst leer. */
        val name: String = "",
        val groesse: Long = 0,
    ) : Nachricht

    /** Ich habe alles geschickt, was ich schicken wollte. */
    @Serializable
    @SerialName("fertig")
    data object Fertig : Nachricht

    /**
     * Das Ergebnis der Erstbegegnung: der Nutzer hat entschieden, welcher Stand gilt.
     * Gewinnt der eigene, schickt dieses Gerät anschließend seinen Stand als Datei.
     */
    @Serializable
    @SerialName("erstbegegnung")
    data class ErstbegegnungEntschieden(val meinStandGewinnt: Boolean) : Nachricht
}

/** Wofür eine übertragene Datei steht. */
@Serializable
enum class DateiArt {
    /** Die ganze SQLite-Datei des Standes. */
    STAND,

    /** Die Zeilenoperationen, die der Empfänger anwenden soll ([Anweisungspaket]). */
    ANWEISUNGEN,

    /** Ein einzelnes Medium, benannt nach seinem SHA-256. */
    MEDIUM,
}

/**
 * Was das rechnende Gerät der Gegenseite schickt: das **Ergebnis**, nicht die Aufgabe
 * (E12, Ablauf 17).
 *
 * Beide rechnen zu lassen hieße, sich darauf zu verlassen, dass zweimal dasselbe
 * herauskommt. Bei gleichem Code und gleichen Eingaben stimmt das auch — bis es einmal nicht
 * stimmt, und dann stehen zwei Geräte mit verschiedenen Daten da und niemand merkt es.
 */
@Serializable
data class Anweisungspaket(
    val operationen: List<Operation>,
    /** Die neue Herkunft — **unverändert** zu übernehmen (E3, Ablauf 32). */
    val generation: Long,
    val erzeugtVon: String,
    val basiertAuf: Long?,
)

/** Version des Gesprächs. Ändert sich das Format, weigern sich alte Fassungen sauber. */
const val PROTOKOLL_VERSION = 1

/**
 * Nearby deckelt BYTES-Payloads. Der Wert ist die dokumentierte Obergrenze; alles, was auch
 * nur in die Nähe kommt, geht als Datei.
 */
const val BYTES_OBERGRENZE = 32 * 1024

/** Ergebnis der Vorprüfungen nach dem Handschlag. */
sealed interface Handschlag {
    /** Es kann losgehen. [ichRechne] sagt, wer das Ergebnis produziert. */
    data class Bereit(val ichRechne: Boolean) : Handschlag

    /** Der Abgleich läuft nicht — mit einer Begründung für den Nutzer. */
    data class Abbruch(val grund: String) : Handschlag
}

/**
 * Prüft den Handschlag und bestimmt die Rolle — auf **beiden** Geräten mit demselben
 * Ergebnis, weil beide dieselben zwei Nachrichten kennen.
 *
 * Die Vorprüfungen stehen hier zusammen, nicht verstreut: ein Abgleich, der mittendrin an
 * fehlendem Platz scheitert, hinterlässt mehr Schaden als einer, der gar nicht erst beginnt
 * (E9).
 */
fun pruefeHandschlag(meine: Nachricht.Hallo, fremde: Nachricht.Hallo): Handschlag {
    if (meine.protokoll != fremde.protokoll) {
        return Handschlag.Abbruch(
            "Die beiden Geräte sprechen verschiedene Fassungen des Abgleichs. " +
                "Aktualisiere beide auf dieselbe App-Version.",
        )
    }

    when (darfIchLesen(meine.schemaVersion, fremde.schemaVersion)) {
        Schemapruefung.DiesesGeraetAktualisieren -> return Handschlag.Abbruch(
            "Das andere Gerät hat eine neuere App-Version. Aktualisiere zuerst dieses Gerät.",
        )
        Schemapruefung.GegenseiteAktualisieren -> return Handschlag.Abbruch(
            "Das andere Gerät hat eine ältere App-Version. Aktualisiere zuerst das andere Gerät.",
        )
        // Über Funk kann das nicht von einer falsch gewählten Datei kommen — die Gegenseite
        // ist die App selbst. Eine 0 heißt hier: dort stimmt etwas grundlegend nicht.
        Schemapruefung.KeineBoulderBuddyDatei -> return Handschlag.Abbruch(
            "Das andere Gerät meldet keine gültige Datenbank-Version.",
        )
        Schemapruefung.Passt -> Unit
    }

    // Auf EINER der beiden Seiten genügt — dort würde während des Anwendens weitergeschrieben.
    if (meine.laufendeSession) {
        return Handschlag.Abbruch("Auf diesem Gerät läuft eine Session. Beende sie zuerst.")
    }
    if (fremde.laufendeSession) {
        return Handschlag.Abbruch(
            "Auf dem anderen Gerät läuft eine Session. Beende sie dort zuerst.",
        )
    }

    // Doppelter Bedarf (Ablauf 27): die empfangene Datei liegt neben dem eigenen Stand, und
    // Nearby legt zusätzlich seine eigene Kopie der Payload ab.
    if (meine.freierPlatz < fremde.standGroesse * 2) {
        return Handschlag.Abbruch("Auf diesem Gerät ist zu wenig Platz frei.")
    }
    if (fremde.freierPlatz < meine.standGroesse * 2) {
        return Handschlag.Abbruch("Auf dem anderen Gerät ist zu wenig Platz frei.")
    }

    return Handschlag.Bereit(ichRechne = ichRechne(meine, fremde))
}

/**
 * Wer rechnet: **wer den Knopf gedrückt hat** (E12, Ablauf 17).
 *
 * Drücken beide gleichzeitig, entscheidet die kleinere Geräte-ID — irgendeine feste Regel
 * muss es geben, und sie muss auf beiden Geräten dieselbe Antwort geben, ohne dass sie sich
 * darüber verständigen müssen (Ablauf 11).
 *
 * Hat niemand gedrückt, kann der Abgleich gar nicht laufen; die Regel greift trotzdem, damit
 * es keinen undefinierten Zustand gibt.
 */
fun ichRechne(meine: Nachricht.Hallo, fremde: Nachricht.Hallo): Boolean = when {
    meine.hatGedrueckt && !fremde.hatGedrueckt -> true
    !meine.hatGedrueckt && fremde.hatGedrueckt -> false
    else -> meine.geraeteId < fremde.geraeteId
}

/**
 * Welche Medien der Gegenseite fehlen.
 *
 * Der ganze Aufwand mit den inhaltsadressierten Namen zahlt sich hier aus: es genügt, zwei
 * Namenslisten zu vergleichen. Nichts wird gehasht, nichts wird verglichen, nichts wird
 * unnötig übertragen (E5).
 */
fun fehlendeMedien(meine: Collection<String>, fremde: Collection<String>): List<String> =
    (meine.toSet() - fremde.toSet()).sorted()
