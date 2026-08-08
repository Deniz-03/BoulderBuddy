package com.boulderbuddy.sync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Ein gescheiterter Checkpoint bricht den Abgleich ab, statt einen halben Stand zu senden. */
class CheckpointFehlgeschlagen(nachricht: String) : IllegalStateException(nachricht)

/**
 * Die Dateien rund um den Abgleich: der Stand selbst, das Gedächtnis (`basis.db`) und der
 * Rückweg (`vorher.db`) — Sync-Plan E4, E13, Ablauf 30/34.
 *
 * Hier stecken die zwei Fallen, über die dieses Projekt schon einmal gestolpert ist:
 *
 * 1. **Die Datenbank steht im WAL-Modus.** Ohne `wal_checkpoint(FULL)` liegen die frischen
 *    Daten in `-wal` und die kopierte `.db` ist fast leer. Auf dem Testgerät waren das 4 KB
 *    Datei gegen 111 KB WAL — man überträgt also praktisch nichts und merkt es nicht.
 * 2. **Beim Ersetzen der Datei muss `-wal` und `-shm` mit weg.** Sonst spielt SQLite beim
 *    nächsten Öffnen den alten WAL über die neue Datei — den alten Stand über den neuen.
 */
@Singleton
class AbgleichDateien @Inject constructor(
    @ApplicationContext private val context: Context,
    private val datenbank: BoulderBuddyDatabase,
) {

    /** Die Datenbankdatei selbst — die, um die es beim Abgleich geht. */
    val standDatei: File get() = context.getDatabasePath(BoulderBuddyDatabase.NAME)

    private val ordner: File get() = File(context.filesDir, "abgleich")

    val basisDatei: File get() = File(ordner, "basis.db")
    val vorherDatei: File get() = File(ordner, "vorher.db")

    fun hatGedaechtnis(): Boolean = basisDatei.exists()

    fun kannRueckgaengig(): Boolean = vorherDatei.exists()

    /**
     * Schreibt den WAL in die Datei und **prüft, dass es geklappt hat**.
     *
     * `wal_checkpoint(FULL)` meldet in der ersten Spalte, ob es blockiert war. Wer das nicht
     * liest, überträgt im Zweifel eine fast leere Datenbank und überschreibt damit den Stand
     * der Gegenseite (Ablauf 30).
     */
    fun checkpoint(db: SupportSQLiteDatabase = datenbank.openHelper.writableDatabase) {
        db.query("PRAGMA wal_checkpoint(FULL)").use { c ->
            if (!c.moveToFirst()) {
                throw CheckpointFehlgeschlagen("PRAGMA wal_checkpoint hat nichts geliefert")
            }
            val blockiert = c.getInt(0)
            if (blockiert != 0) {
                throw CheckpointFehlgeschlagen(
                    "wal_checkpoint war blockiert (busy=$blockiert) — es schreibt noch jemand",
                )
            }
        }
    }

    /** Eine Kopie des aktuellen Standes, fertig zum Verschicken oder Aufheben. */
    suspend fun kopiereStand(nach: File): File = withContext(Dispatchers.IO) {
        checkpoint()
        nach.parentFile?.mkdirs()
        standDatei.copyTo(nach, overwrite = true)
        nach
    }

    /** Das Gedächtnis nach einem erfolgreichen Abgleich — **erst anwenden, dann das hier**. */
    suspend fun merkeAlsBasis() {
        kopiereStand(basisDatei)
    }

    /** Der Rückweg, vor jedem Anwenden — auch bei der Erstbegegnung (E13). */
    suspend fun merkeAlsVorher() {
        kopiereStand(vorherDatei)
    }

    /**
     * Ersetzt den ganzen Stand durch eine empfangene Datei (Erstbegegnung, E10).
     *
     * Die Reihenfolge ist der Punkt, nicht die einzelnen Schritte: Rückweg sichern → Room
     * schließen → `-wal` und `-shm` löschen → Datei ersetzen. Verteilt man das über drei
     * Stellen, fehlt irgendwann ein Schritt.
     *
     * Danach ist die App **nicht mehr benutzbar**, bis der Prozess neu startet — Room hält
     * noch die alte Datei. Der Aufrufer muss [Neustart.jetzt] rufen.
     */
    suspend fun ersetzeStand(empfangen: File) = withContext(Dispatchers.IO) {
        require(empfangen.exists()) { "Empfangene Standdatei fehlt: ${empfangen.name}" }

        merkeAlsVorher()
        datenbank.close()

        File(standDatei.path + "-wal").delete()
        File(standDatei.path + "-shm").delete()
        empfangen.copyTo(standDatei, overwrite = true)
    }

    /**
     * Reicht der Platz? Gefordert ist das **Doppelte** (Ablauf 27): die empfangene Datei
     * liegt kurzzeitig neben dem eigenen Stand, und Nearby legt zusätzlich seine eigene
     * Kopie der Payload ab.
     */
    fun genugPlatz(bedarfBytes: Long): Boolean = freierPlatz() > bedarfBytes * 2

    /** Freier Speicher dort, wo Stand und Medien liegen. */
    fun freierPlatz(): Long = context.filesDir.usableSpace

    /** Räumt liegengebliebene Empfangsdateien weg — auch nach einem Abbruch (Ablauf 27). */
    fun raeumeEmpfangenesAuf() {
        empfangsOrdner().listFiles()?.forEach { it.delete() }
    }

    fun empfangsOrdner(): File = File(ordner, "empfangen").apply { mkdirs() }

    /**
     * Verwirft das Gedächtnis und den Rückweg. Nach einer rückgängig gemachten
     * Erstbegegnung gilt das Gerät wieder als „noch nie abgeglichen" (Ablauf 36) — sonst
     * würde sein alter Stand beim nächsten Mal mit dem des anderen Geräts verschmolzen, und
     * es entstünden doppelte Hallen und doppelte Sessions aus zwei Datensätzen, die nie
     * zusammengehörten.
     */
    fun verwirfKopplung() {
        basisDatei.delete()
        vorherDatei.delete()
    }
}
