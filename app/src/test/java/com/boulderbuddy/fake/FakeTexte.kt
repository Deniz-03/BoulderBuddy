package com.boulderbuddy.fake

import com.boulderbuddy.ui.Texte

/**
 * Löst Texte ohne Android auf — für die JVM-Tests der ViewModels.
 *
 * Zurück kommt kein deutscher Satz, sondern eine **Kennung**: `«2131820801»` bzw.
 * `«2131820801»[Halle Nord]`. Das ist Absicht und der eigentliche Gewinn dieser Naht.
 *
 * Ein Test, der auf „In Session „Halle Nord" gespeichert" prüft, prüft zwei Dinge auf einmal:
 * dass der richtige Fall gewählt wurde, und wie er formuliert ist. Das Zweite gehört nicht in
 * einen ViewModel-Test — jede Umformulierung in `strings.xml` hätte ihn rot gemacht, obwohl
 * sich am Verhalten nichts geändert hat. Mit der Kennung prüft er nur noch das Erste, und die
 * eingesetzten Werte stehen weiterhin sichtbar dahinter.
 *
 * [erwartet] baut dieselbe Kennung für die Behauptungsseite eines Tests.
 */
class FakeTexte : Texte {

    /** Alle Aufrufe in ihrer Reihenfolge — für Tests, die die Auswahl selbst prüfen wollen. */
    val aufrufe = mutableListOf<Int>()

    override fun hole(id: Int, vararg args: Any?): String {
        aufrufe += id
        return erwartet(id, *args)
    }

    override fun mehrzahl(id: Int, anzahl: Int, vararg args: Any?): String {
        aufrufe += id
        return erwartet(id, *args)
    }

    companion object {
        /** Die Kennung, die [FakeTexte] für [id] mit [args] liefert. */
        fun erwartet(id: Int, vararg args: Any?): String =
            if (args.isEmpty()) {
                "«$id»"
            } else {
                "«$id»" + args.joinToString(prefix = "[", postfix = "]")
            }
    }
}
