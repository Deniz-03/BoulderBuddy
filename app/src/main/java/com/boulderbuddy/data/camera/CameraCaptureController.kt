package com.boulderbuddy.data.camera

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
// Suspend-Variante von getInstance(); liegt als Erweiterung neben der Klasse und braucht
// deshalb einen eigenen Import — sonst müsste hier ein ListenableFuture ausgepackt werden.
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Die einzige Stelle, an der CameraX angefasst wird — Gegenstück zu [CaptureModel], das die
 * Regeln android-frei hält.
 *
 * **Warum ein eigener Aufnahme-Screen statt `ACTION_IMAGE_CAPTURE`/`ACTION_VIDEO_CAPTURE`:**
 * Der Intent liefert, was die installierte Kamera-App gerade für richtig hält — Auflösung,
 * Bildrate und Kompression sind nicht bestimmbar. Für den Ghost Climber ist das ein Problem:
 * die Pose-Pipeline wird berechenbarer, wenn Referenz- und Vergleichsvideo dieselbe Auflösung
 * haben. Hier ist sie deshalb fest auf [Quality.HD] gesetzt, nicht auf „das Beste, was geht".
 */
class CameraCaptureController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private val executor = ContextCompat.getMainExecutor(context)

    /**
     * Bindet Vorschau und den zum [modus] passenden Anwendungsfall an den Lifecycle.
     *
     * Foto und Video werden **nicht gleichzeitig** gebunden: die gleichzeitige Nutzung von
     * `Preview` + `ImageCapture` + `VideoCapture` ist nicht auf jedem Gerät zugesichert. Ein
     * Moduswechsel bindet deshalb neu — kostet einen kurzen Vorschau-Aussetzer, funktioniert
     * dafür überall.
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        modus: CaptureModus,
        vorderkamera: Boolean,
        rotation: Int,
    ): Result<Unit> {
        val provider = cameraProvider ?: run {
            val neu = runCatching { ProcessCameraProvider.awaitInstance(context) }
                .getOrElse { return Result.failure(it) }
            cameraProvider = neu
            neu
        }

        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .apply { setSurfaceProvider(surfaceProvider) }

        return runCatching {
            provider.unbindAll()
            when (modus) {
                CaptureModus.FOTO -> {
                    val capture = ImageCapture.Builder()
                        // Auf Latenz statt auf maximale Qualität: der Nutzer steht vor der Wand
                        // und will den Auslöser drücken, nicht warten.
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(rotation)
                        .build()
                    imageCapture = capture
                    videoCapture = null
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selektor(vorderkamera),
                        preview,
                        capture,
                    )
                }

                CaptureModus.VIDEO -> {
                    val recorder = Recorder.Builder()
                        // Feste Stufe statt Quality.HIGHEST — siehe Klassenkommentar. Die
                        // Ausweichstrategie greift nur auf Geräten ohne 720p-Profil.
                        .setQualitySelector(
                            QualitySelector.from(
                                Quality.HD,
                                FallbackStrategy.higherQualityOrLowerThan(Quality.HD),
                            ),
                        )
                        .build()
                    val capture = VideoCapture.withOutput(recorder)
                    videoCapture = capture
                    imageCapture = null
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selektor(vorderkamera),
                        preview,
                        capture,
                    )
                }
            }
            Unit
        }
    }

    /** Nimmt ein Foto auf und liefert seine `content://`-URI. */
    suspend fun fotoAufnehmen(): Result<Uri> = suspendCancellableCoroutine { fortsetzung ->
        val capture = imageCapture
        if (capture == null) {
            fortsetzung.resume(Result.failure(IllegalStateException("ImageCapture nicht gebunden")))
            return@suspendCancellableCoroutine
        }
        val datei = neueDatei(CaptureModus.FOTO)
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(datei).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    fortsetzung.resume(Result.success(uriFuer(datei)))
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w(TAG, "Foto fehlgeschlagen", exception)
                    // Halbe Datei nicht liegen lassen — sie würde als kaputtes Bild auftauchen.
                    datei.delete()
                    fortsetzung.resume(Result.failure(exception))
                }
            },
        )
    }

    /**
     * Startet die Videoaufnahme. Ereignisse kommen auf dem Main-Thread zurück.
     *
     * Ton wird **nur** aufgenommen, wenn `RECORD_AUDIO` freigegeben ist. Ohne die Freigabe
     * würde `withAudioEnabled()` eine `SecurityException` werfen — ein stummes Video ist die
     * bessere Antwort als eine verweigerte Aufnahme.
     */
    // Genau dafür ist darfTonAufnehmen() da; Lint sieht die Prüfung hinter dem Aufruf nicht.
    @SuppressLint("MissingPermission")
    fun videoStarten(onEreignis: (VideoEreignis) -> Unit) {
        val capture = videoCapture
        if (capture == null) {
            onEreignis(VideoEreignis.Fehlgeschlagen)
            return
        }
        val datei = neueDatei(CaptureModus.VIDEO)
        val vorbereitet = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(datei).build())
            .let { if (darfTonAufnehmen()) it.withAudioEnabled() else it }

        recording = runCatching {
            vorbereitet.start(executor) { ereignis ->
                when (ereignis) {
                    is VideoRecordEvent.Status -> onEreignis(
                        VideoEreignis.Laeuft(
                            TimeUnit.NANOSECONDS.toMillis(
                                ereignis.recordingStats.recordedDurationNanos,
                            ),
                        ),
                    )

                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        if (ereignis.hasError()) {
                            Log.w(TAG, "Videoaufnahme fehlgeschlagen: ${ereignis.error}")
                            datei.delete()
                            onEreignis(VideoEreignis.Fehlgeschlagen)
                        } else {
                            onEreignis(VideoEreignis.Fertig(uriFuer(datei)))
                        }
                    }

                    else -> Unit
                }
            }
        }.onFailure {
            Log.w(TAG, "Videoaufnahme konnte nicht gestartet werden", it)
            datei.delete()
            onEreignis(VideoEreignis.Fehlgeschlagen)
        }.getOrNull()
    }

    /** Beendet die laufende Aufnahme; das Ergebnis kommt als `Finalize`-Ereignis. */
    fun videoStoppen() {
        recording?.stop()
        recording = null
    }

    /**
     * Gibt alles frei. Eine laufende Aufnahme wird **verworfen**, nicht gespeichert: wer den
     * Screen verlässt, will das Video nicht — und eine halbe Datei wäre schlimmer als keine.
     */
    fun freigeben() {
        recording?.close()
        recording = null
        cameraProvider?.unbindAll()
        imageCapture = null
        videoCapture = null
    }

    private fun darfTonAufnehmen(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun selektor(vorderkamera: Boolean): CameraSelector =
        if (vorderkamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    private fun neueDatei(modus: CaptureModus): File {
        val ordner = File(context.filesDir, AUFNAHME_ORDNER).apply { mkdirs() }
        return File(ordner, aufnahmeDateiname(modus, System.currentTimeMillis()))
    }

    /**
     * `content://`-URI statt `file://`: nur so beantwortet `contentResolver.getType()` den
     * MIME-Typ, den `mediaTypeOf` zur Unterscheidung Bild/Video braucht.
     */
    private fun uriFuer(datei: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        datei,
    )

    private companion object {
        const val TAG = "CameraCapture"
    }
}

/** Rückmeldungen einer laufenden Videoaufnahme. */
sealed interface VideoEreignis {
    data class Laeuft(val dauerMs: Long) : VideoEreignis
    data class Fertig(val uri: Uri) : VideoEreignis
    data object Fehlgeschlagen : VideoEreignis
}
