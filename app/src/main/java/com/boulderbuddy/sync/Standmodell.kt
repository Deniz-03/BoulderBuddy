package com.boulderbuddy.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ein Wert in einer Zelle. Bewusst ein eigener Typ statt `Any?`: SQLite liefert `Long`,
 * `Double`, `String` oder NULL, und `1L == 1` ist in Kotlin `false`. Ein Vergleich, der
 * gelegentlich an einem Zahlentyp scheitert, meldete Konflikte, die es nicht gibt.
 */
@Serializable
sealed interface Feld {
    /** SQL NULL. Unterscheidet sich von „Spalte nicht vorhanden". */
    @Serializable
    @SerialName("leer")
    data object Leer : Feld

    @Serializable
    @SerialName("text")
    data class Text(val wert: String) : Feld

    @Serializable
    @SerialName("zahl")
    data class Zahl(val wert: Long) : Feld

    @Serializable
    @SerialName("komma")
    data class Komma(val wert: Double) : Feld
}

/** Eine Zeile: Spaltenname → Wert. Ohne `id` — die steht als Schlüssel daneben. */
typealias Zeile = Map<String, Feld>

/**
 * Ein vollständiger Datenstand: Tabellenname → (Primärschlüssel → Zeile).
 *
 * Enthält nur die Tabellen aus [STAND_TABELLEN]. `stand_meta` gehört ausdrücklich nicht
 * dazu — sie beschreibt den Stand, sie ist nicht Teil davon (Ablauf 19).
 */
class Stand(private val tabellen: Map<String, Map<Int, Zeile>>) {

    fun zeilen(tabelle: String): Map<Int, Zeile> = tabellen[tabelle].orEmpty()

    fun zeile(tabelle: String, id: Int): Zeile? = tabellen[tabelle]?.get(id)

    /** Alle Primärschlüssel einer Tabelle — für die Sequenz-Rückstellung (E8). */
    fun ids(tabelle: String): Set<Int> = zeilen(tabelle).keys

    companion object {
        val LEER = Stand(emptyMap())

        fun aus(vararg tabellen: Pair<String, Map<Int, Zeile>>) = Stand(tabellen.toMap())
    }
}

/**
 * Herkunft des Standes, wie sie in `stand_meta` steht (E3) — hier als reiner Wert, damit
 * die Lagebestimmung ohne Room testbar bleibt.
 */
data class StandMeta(
    val generation: Long,
    val erzeugtVon: String,
    val basiertAuf: Long?,
)

/** Welches Gerät bei einem Konflikt gewinnt — eine Antwort für alle Konflikte (E12). */
enum class Seite { MEINS, FREMDES }

/** Warum eine Zeile strittig ist. Bestimmt den Text der Rückfrage, nicht die Rechnung. */
enum class KonfliktArt {
    /** Dieselbe Zeile auf beiden Seiten verschieden geändert. */
    BEIDSEITIG_GEAENDERT,

    /** Auf einer Seite gelöscht, auf der anderen geändert. */
    GELOESCHT_GEGEN_GEAENDERT,

    /**
     * Auf einer Seite gelöscht, auf der anderen wurde im Teilbaum darunter etwas
     * ergänzt oder geändert (Ablauf 8). Zeilenweise wäre das kein Konflikt — es ergäbe
     * einen Boulder ohne Session.
     */
    TEILBAUM,

    /**
     * Dieselbe Nummer, verschiedene Zeilen, beiden neu (Ablauf 7). Sollte mit den
     * Nummernbändern nicht mehr vorkommen; tut es doch, ist Raten die falsche Antwort.
     */
    GLEICHE_NUMMER,
}

/** Eine strittige Zeile, wie sie dem Nutzer vorgelegt wird. */
data class Konflikt(
    val tabelle: String,
    val id: Int,
    val art: KonfliktArt,
    /** Zeile dieses Geräts; `null` = hier gelöscht. */
    val meine: Zeile?,
    /** Zeile der Gegenseite; `null` = dort gelöscht. */
    val fremde: Zeile?,
)

/** Eine anzuwendende Änderung. Gilt immer für genau ein Gerät. */
@Serializable
sealed interface Operation {
    val tabelle: String
    val id: Int

    @Serializable
    @SerialName("einfuegen")
    data class Einfuegen(
        override val tabelle: String,
        override val id: Int,
        val zeile: Zeile,
    ) : Operation

    @Serializable
    @SerialName("aendern")
    data class Aendern(
        override val tabelle: String,
        override val id: Int,
        val zeile: Zeile,
    ) : Operation

    @Serializable
    @SerialName("loeschen")
    data class Loeschen(
        override val tabelle: String,
        override val id: Int,
    ) : Operation
}

/**
 * Was der Abgleich bewirkt hat — in Zeilen, die man einem Menschen vorlesen kann (S7).
 *
 * Löschungen stehen ausdrücklich drin: verschwindet etwas kommentarlos, wirkt das wie ein
 * Fehler (Ablauf 3).
 */
@Serializable
data class Bilanz(
    /** Zeilen, die auf dieses Gerät gekommen sind. */
    val uebernommen: Int,
    /** Zeilen, die dieses Gerät an die Gegenseite abgegeben hat. */
    val abgegeben: Int,
    /** Zeilen, die überall verschwinden. */
    val geloescht: Int,
    /** Zeilen, deren Fassung durch die Konfliktantwort verworfen wurde. */
    val konfliktVerluste: Int,
    /** Verweise, die durch eine Löschung leer wurden (z.B. Route verliert ihren Grad). */
    val bezuegeGeloest: Int,
) {
    val nichtsZuTun: Boolean
        get() = uebernommen == 0 && abgegeben == 0 && geloescht == 0 && bezuegeGeloest == 0

    companion object {
        val NICHTS = Bilanz(0, 0, 0, 0, 0)
    }
}

/** Das Ergebnis: was jede Seite zu tun hat, und was dabei herauskam. */
data class Anweisungen(
    val fuerMich: List<Operation>,
    val fuerDieGegenseite: List<Operation>,
    val bilanz: Bilanz,
)
