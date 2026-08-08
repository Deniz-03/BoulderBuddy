package com.boulderbuddy.data.camera

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Zustandsmodell und Regeln des eigenen Aufnahme-Screens (CameraX).
 *
 * Wie bei der Spracheingabe liegt hier alles, was **entscheidet**, android-frei und damit
 * JVM-testbar. Was die Kamera anfasst, steht ausschließlich in [CameraCaptureController].
 */

/** Was aufgenommen werden soll. */
enum class CaptureModus { FOTO, VIDEO }

/**
 * Was der aufrufende Screen zulässt. Der Ghost Climber kann mit einem Foto nichts anfangen,
 * das Boulder-Formular dagegen mit beidem — deshalb ist die Modus-Umschaltung kein fester
 * Bestandteil des Aufnahme-Screens, sondern eine Eigenschaft des Auftrags.
 */
enum class CaptureAuftrag {
    /** Nur Foto (aktuell von keinem Aufrufer genutzt, aber der Screen kann es). */
    NUR_FOTO,

    /** Nur Video — der Ghost-Climber-Fall. */
    NUR_VIDEO,

    /** Der Nutzer wählt im Aufnahme-Screen. */
    FOTO_UND_VIDEO,
}

/** Mit welchem Modus der Screen startet und ob umgeschaltet werden darf. */
fun startModusFuer(auftrag: CaptureAuftrag): CaptureModus = when (auftrag) {
    CaptureAuftrag.NUR_VIDEO -> CaptureModus.VIDEO
    else -> CaptureModus.FOTO
}

fun darfModusWechseln(auftrag: CaptureAuftrag): Boolean = auftrag == CaptureAuftrag.FOTO_UND_VIDEO

/** Zustand der Aufnahme-Oberfläche. */
sealed interface CaptureState {
    /** Vorschau läuft, nichts wird aufgezeichnet. */
    data object Bereit : CaptureState

    /** Foto wird geschrieben — kurz, aber der Auslöser muss so lange gesperrt sein. */
    data object FotoLaeuft : CaptureState

    /** Videoaufnahme läuft. [dauerMs] ist die bisher aufgezeichnete Zeit. */
    data class VideoLaeuft(val dauerMs: Long) : CaptureState

    /** Aufnahme beendet, Datei wird finalisiert. */
    data object WirdGespeichert : CaptureState

    /** Abbruch mit Grund; der Screen zeigt ihn an und bleibt offen. */
    data class Fehler(val grund: CaptureFehler) : CaptureState
}

/** Grund eines gescheiterten Aufnahme-Versuchs, mit der Meldung für die Oberfläche. */
enum class CaptureFehler(val message: String) {
    KEINE_FREIGABE("Ohne Kamera-Freigabe geht die Aufnahme nicht."),
    KEINE_KAMERA("Auf diesem Gerät ist keine nutzbare Kamera gefunden worden."),
    SPEICHERN_FEHLGESCHLAGEN("Die Aufnahme konnte nicht gespeichert werden."),
    AUFNAHME_FEHLGESCHLAGEN("Die Aufnahme ist abgebrochen."),
}

/**
 * Obergrenze einer Videoaufnahme. Zwei Gründe: eine Boulder-Begehung dauert Sekunden, nicht
 * Minuten, und die Aufnahmen liegen app-intern — ein vergessener Daumen auf dem Auslöser soll
 * nicht den Gerätespeicher füllen. Der Screen stoppt bei Erreichen selbst.
 */
const val MAX_VIDEO_DAUER_MS = 3 * 60 * 1000L

/** Ob die laufende Aufnahme die Obergrenze erreicht hat und automatisch beendet werden muss. */
fun mussAutomatischStoppen(dauerMs: Long): Boolean = dauerMs >= MAX_VIDEO_DAUER_MS

/**
 * Laufzeit-Anzeige der Videoaufnahme als `m:ss`. Bewusst **nicht** `formatHangTime` aus dem
 * UI-Modell: das dort gewählte „30s" unter einer Minute ist für eine mitlaufende Uhr falsch —
 * eine Aufnahmeanzeige, die von `59s` auf `1:00min` springt, wirkt wie ein Fehler. Hier zählt
 * von Anfang an dieselbe Form hoch. Negative Werte werden auf 0 geklemmt.
 */
fun formatAufnahmedauer(millis: Long): String {
    val gesamt = millis.coerceAtLeast(0L)
    val minuten = TimeUnit.MILLISECONDS.toMinutes(gesamt)
    val sekunden = TimeUnit.MILLISECONDS.toSeconds(gesamt) % 60
    return String.format(Locale.GERMANY, "%d:%02d", minuten, sekunden)
}

/**
 * Dateiname einer Aufnahme. Der Zeitstempel macht ihn eindeutig und beim Debuggen lesbar;
 * die Endung entscheidet über den MIME-Typ, den der FileProvider meldet — und damit darüber,
 * ob `mediaTypeOf` die Aufnahme später als Bild oder Video erkennt.
 *
 * @param zeitstempelMs Aufnahmezeit; als Parameter statt `System.currentTimeMillis()`, damit
 *   der Name testbar ist.
 */
fun aufnahmeDateiname(modus: CaptureModus, zeitstempelMs: Long): String {
    val endung = when (modus) {
        CaptureModus.FOTO -> "jpg"
        CaptureModus.VIDEO -> "mp4"
    }
    return "BB_${zeitstempelMs}.$endung"
}

/** Unterordner der Aufnahmen in `filesDir`. Muss zu `res/xml/file_paths.xml` passen. */
const val AUFNAHME_ORDNER = "aufnahmen"
