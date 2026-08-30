package com.boulderbuddy.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Löst Anzeigetexte aus `strings.xml` auf — für die Stellen, an denen ein ViewModel einen
 * fertigen Satz in den UI-Zustand legt.
 *
 * **Warum eine Schnittstelle und nicht einfach der Context.** Der naheliegende Weg wäre
 * `@ApplicationContext` im ViewModel und `context.getString(…)`. Er funktioniert, kostet aber
 * genau das, was die JVM-Tests dieses Projekts ausmachen: `Context` ist eine Android-Klasse,
 * und ein ViewModel, das eine braucht, lässt sich ohne Emulator oder Robolectric nicht mehr
 * bauen. Beim Umstellen sind daran auf einen Schlag vier Testklassen zerbrochen —
 * Sortierung, Statistik, Timer-Zustandsmaschine —, und keine davon hat mit Text zu tun.
 *
 * Diese Schnittstelle ist die schmalste Naht, die beides erhält: der Wortlaut steht in
 * `strings.xml`, und im Test steht ein [com.boulderbuddy.fake.FakeTexte] daneben, das ohne
 * Android auskommt.
 *
 * **Wofür sie nicht da ist.** Text, den ein Composable anzeigt, holt sich das Composable
 * selbst über `stringResource` — dort gibt es die Ressourcen ohnehin. Hierher gehört nur,
 * was ein ViewModel zusammensetzen muss, weil die Regel dahinter ins ViewModel gehört.
 */
interface Texte {

    /** Wie `Context.getString`. */
    fun hole(@StringRes id: Int, vararg args: Any?): String

    /** Wie `Resources.getQuantityString`; [anzahl] wählt die Form und ist meist auch ein Argument. */
    fun mehrzahl(@PluralsRes id: Int, anzahl: Int, vararg args: Any?): String
}

@Singleton
class AndroidTexte @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : Texte {

    override fun hole(id: Int, vararg args: Any?): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    override fun mehrzahl(id: Int, anzahl: Int, vararg args: Any?): String =
        context.resources.getQuantityString(id, anzahl, *args)
}
