package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.ui.Texte
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.HangboardSegmentEntity
import com.boulderbuddy.data.db.entity.hallenName
import com.boulderbuddy.data.haptics.HapticPattern
import com.boulderbuddy.data.haptics.HapticPlayer
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.HangboardWorkoutOrigin
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.HangboardRepository
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.R
import com.boulderbuddy.ui.Fehlerkanal
import com.boulderbuddy.ui.schreibe
import com.boulderbuddy.data.settings.TimerConfig
import com.boulderbuddy.ui.screens.HangboardTimerUiState
import com.boulderbuddy.ui.screens.TimerPhase
import com.boulderbuddy.ui.screens.TimerPreset
import com.boulderbuddy.wearsync.WearConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Treibt den Hangboard-Timer: Countdown je Phase (HANG → REST → HANG …), Satz-Hochzählen,
 * Play/Pause/Reset. Reine Logik im ViewModel — der Screen bleibt stateless und rendert nur
 * den [HangboardTimerUiState].
 *
 * Konfiguration (Sätze/Hang/Pause) kommt aus dem [SettingsRepository] und wird über
 * [updateConfig] persistiert (letzte Einstellung gemerkt). Benannte Voreinstellungen
 * (Presets) liegen dagegen im [HangboardRepository] und werden hier eingebunden
 * ([savePreset]/[deletePreset]/[applyPreset]). Ein bis zum Ende absolvierter Durchlauf
 * wird **immer** als Hangboard-Workout gespeichert — läuft eine Kletter-Session, wird er
 * an sie gehängt, sonst als eigenständiges Training (siehe [recordWorkout], §0 Säule 2).
 */
@HiltViewModel
class HangboardTimerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val hangboardWorkoutRepository: HangboardWorkoutRepository,
    private val hangboardRepository: HangboardRepository,
    private val gymRepository: GymRepository,
    private val hapticPlayer: HapticPlayer,
    private val fehlerkanal: Fehlerkanal,
    wearConnection: WearConnection,
    // Loest die Anzeigetexte aus strings.xml auf (siehe ui/Texte.kt: als Schnittstelle,
    // damit dieses ViewModel ohne Android testbar bleibt).
    private val texte: Texte,
) : ViewModel() {

    // Konfiguration (var, da über updateConfig änderbar). Defaults bis DataStore geladen ist.
    private var totalSets = 6
    private var hangSec = 7
    private var restSec = 3

    // Benannte Voreinstellungen aus der DB (Room-Flow), für die Preset-Auswahl im UI.
    private var presets: List<HangboardTemplateEntity> = emptyList()

    // Interner Lauf-Zustand (getrennt vom UI-State, damit Pause/Resume die Restsekunden behält).
    private var currentSet = 1
    private var phase = TimerPhase.HANG
    private var secondsLeft = hangSec
    private var running = false
    private var timerJob: Job? = null
    // Verhindert doppeltes Tracken desselben abgeschlossenen Durchlaufs.
    private var recorded = false
    // Echter Startzeitpunkt des laufenden Durchlaufs (erster Play nach Reset).
    private var startedAtMillis: Long? = null
    // Speicherort-Feedback nach DONE (§0 Säule 3), z.B. "In Session „Halle X" gespeichert".
    private var savedTo: String? = null
    // Zwischengespeicherter Stand des Haptik-Schalters. Der Phasenwechsel läuft synchron in der
    // Timer-Schleife, dort wäre ein suspendierender DataStore-Zugriff pro Sekunde unnötig.
    private var hapticEnabled = true
    // Ob gerade eine Uhr verbunden ist — steuert nur den Indikator in der Top-Bar.
    private var watchConnected = false

    private val _uiState = MutableStateFlow(snapshot())
    val uiState: StateFlow<HangboardTimerUiState> = _uiState.asStateFlow()

    init {
        // Zuletzt genutzte Konfiguration laden und anwenden.
        viewModelScope.launch {
            applyConfig(settingsRepository.timerConfig.first())
        }
        // Presets beobachten und in den UI-State spiegeln (ändern nicht den Lauf-Zustand).
        viewModelScope.launch {
            hangboardRepository.observeAll().collect {
                presets = it
                _uiState.value = snapshot()
            }
        }
        // Haptik-Schalter beobachten, damit eine Änderung sofort greift.
        viewModelScope.launch {
            settingsRepository.hapticFeedback.collect { hapticEnabled = it }
        }
        // Uhr-Verbindung beobachten und in den UI-State spiegeln (Indikator in der Top-Bar).
        viewModelScope.launch {
            wearConnection.connected.collect {
                watchConnected = it
                _uiState.value = snapshot()
            }
        }
    }

    fun onPlayPause() {
        if (running) pause() else start()
    }

    fun onReset() {
        timerJob?.cancel()
        currentSet = 1
        phase = TimerPhase.HANG
        secondsLeft = hangSec
        running = false
        recorded = false
        startedAtMillis = null
        savedTo = null
        _uiState.value = snapshot()
    }

    /**
     * Übernimmt eine neue Timer-Konfiguration: persistiert sie (letzte Einstellung gemerkt)
     * und setzt den Timer mit den neuen Werten zurück.
     */
    fun updateConfig(sets: Int, hangSec: Int, restSec: Int) {
        val config = TimerConfig(
            sets = sets.coerceAtLeast(1),
            hangSec = hangSec.coerceAtLeast(1),
            restSec = restSec.coerceAtLeast(0),
        )
        viewModelScope.launch {
            // Scheitert nur das Merken, laeuft der Timer trotzdem mit den neuen Werten - das
            // sagt die Meldung auch. Ein Abbruch waere hier die schlechtere Antwort: der
            // Nutzer steht am Board und will loslegen, nicht die Einstellung retten.
            fehlerkanal.schreibe(
                R.string.fehler_timer_einstellung,
                protokollMarke = "Timer-Konfiguration merken",
            ) {
                settingsRepository.setTimerConfig(config)
            }
        }
        /*
         * Unveränderte Werte lassen den Durchlauf in Ruhe.
         *
         * `applyConfig` setzt zurück, und das ist beim Ändern auch richtig — ein Durchlauf mit
         * halb alten, halb neuen Vorgaben wäre nichts wert. Nur trifft „Übernehmen" auch, wer
         * den Dialog bloß aufgemacht und wieder bestätigt hat: am Gerät stand der Timer danach
         * mitten aus Satz 4 heraus wieder auf Satz 1, angehalten, und der Durchlauf war nicht
         * gespeichert. Ohne Rückfrage, ohne Hinweis.
         */
        // `this.` ist hier keine Zierde: die Parameter heißen wie die Felder und verdeckten
        // sie sonst — der Vergleich prüfte den Parameter gegen sich selbst und wäre immer wahr.
        val unveraendert = config.sets == totalSets &&
            config.hangSec == this.hangSec &&
            config.restSec == this.restSec
        if (unveraendert) return
        applyConfig(config)
    }

    private fun applyConfig(config: TimerConfig) {
        totalSets = config.sets
        hangSec = config.hangSec
        restSec = config.restSec
        onReset()
    }

    /**
     * Übernimmt eine Voreinstellung (Preset) als aktuelle Config: wie [updateConfig], nur mit
     * den Werten des gewählten Templates. Persistiert die Config als „zuletzt genutzt".
     */
    fun applyPreset(presetId: Int) {
        val preset = presets.find { it.id == presetId } ?: return
        updateConfig(preset.sets, preset.hangSec, preset.restSec)
    }

    /**
     * Speichert die übergebene Konfiguration als benanntes Preset. Leerer Name wird ignoriert.
     * (Die Preset-Liste aktualisiert sich über den beobachteten Room-Flow von selbst.)
     */
    fun savePreset(name: String, sets: Int, hangSec: Int, restSec: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            fehlerkanal.schreibe(
                R.string.fehler_preset_speichern,
                protokollMarke = "Preset speichern",
            ) {
                hangboardRepository.create(
                    HangboardTemplateEntity(
                        name = trimmed,
                        sets = sets.coerceAtLeast(1),
                        hangSec = hangSec.coerceAtLeast(1),
                        restSec = restSec.coerceAtLeast(0),
                        // repRestSec (Rep-Pause) wird vom Timer aktuell nicht genutzt → = Pause.
                        repRestSec = restSec.coerceAtLeast(0),
                    )
                )
            }
        }
    }

    /** Löscht ein Preset anhand seiner ID. */
    fun deletePreset(presetId: Int) {
        val preset = presets.find { it.id == presetId } ?: return
        viewModelScope.launch {
            fehlerkanal.schreibe(
                R.string.fehler_preset_loeschen,
                protokollMarke = "Preset loeschen",
            ) {
                hangboardRepository.delete(preset)
            }
        }
    }

    private fun start() {
        // Nach dem Ende neu starten: erst zurücksetzen.
        if (phase == TimerPhase.DONE) onReset()
        if (startedAtMillis == null) startedAtMillis = System.currentTimeMillis()
        running = true
        _uiState.value = snapshot()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (running && phase != TimerPhase.DONE) {
                delay(1000)
                secondsLeft--
                /*
                 * `while` statt `if`: eine Phase der Länge 0 darf keine Sekunde kosten.
                 *
                 * Bei Pause = 0 setzte der Wechsel `secondsLeft` auf 0, und die Schleife wartete
                 * trotzdem erst die nächste volle Sekunde ab, bevor sie weiterschaltete. Gemessen
                 * an der von der App selbst protokollierten Laufzeit: 3 Sätze à 2 s ohne Pause
                 * brauchten 8,1 s statt 6 — je Null-Pause exakt eine Sekunde zu viel.
                 *
                 * Terminiert immer, weil `hangSec` mindestens 1 ist (siehe [updateConfig]): jede
                 * zweite Phase hat damit eine echte Dauer.
                 */
                while (secondsLeft <= 0 && phase != TimerPhase.DONE) advancePhase()
                if (phase == TimerPhase.DONE && !recorded) {
                    recorded = true
                    recordWorkout()
                }
                _uiState.value = snapshot()
            }
        }
    }

    private fun pause() {
        running = false
        timerJob?.cancel()
        _uiState.value = snapshot()
    }

    // Phasenübergang, wenn die aktuelle Phase abgelaufen ist. Jeder Übergang vibriert (sofern
    // eingeschaltet) — am Hangboard schaut man nicht auf den Bildschirm.
    private fun advancePhase() {
        when (phase) {
            TimerPhase.HANG ->
                if (currentSet >= totalSets) {
                    phase = TimerPhase.DONE
                    running = false
                    vibrate(HapticPattern.FERTIG)
                } else {
                    phase = TimerPhase.REST
                    secondsLeft = restSec
                    vibrate(HapticPattern.PHASENWECHSEL)
                }
            TimerPhase.REST -> {
                currentSet++
                phase = TimerPhase.HANG
                secondsLeft = hangSec
                vibrate(HapticPattern.PHASENWECHSEL)
            }
            TimerPhase.DONE -> running = false
        }
    }

    private fun vibrate(pattern: HapticPattern) {
        if (hapticEnabled) hapticPlayer.play(pattern)
    }

    /**
     * Speichert den abgeschlossenen Durchlauf als Hangboard-Workout — **immer** (§0 Säule 2):
     * Läuft eine Kletter-Session, wird er an sie gehängt, sonst als eigenständiges Training
     * (`sessionId = null`). Die Segmente sind beim manuellen Timer aus der Vorgabe abgeleitet
     * (identische Dauern, letzter Satz ohne Pause). Danach wird der Speicherort im UI gemeldet.
     */
    private fun recordWorkout() {
        viewModelScope.launch {
            fehlerkanal.schreibe(
                R.string.fehler_workout_speichern,
                protokollMarke = "Hangboard-Durchlauf speichern",
            ) {
                val session = sessionRepository.observeActive().first()
                val endedAt = System.currentTimeMillis()
                val segments = List(totalSets) { i ->
                    HangboardSegmentEntity(
                        workoutId = 0,
                        setIndex = i,
                        hangMs = hangSec * 1000L,
                        restMs = if (i < totalSets - 1) restSec * 1000L else 0L,
                    )
                }
                hangboardWorkoutRepository.create(
                    HangboardWorkoutEntity(
                        sessionId = session?.id,
                        mode = HangboardWorkoutMode.MANUAL,
                        origin = HangboardWorkoutOrigin.PHONE,
                        startedAt = startedAtMillis ?: endedAt,
                        endedAt = endedAt,
                        plannedSets = totalSets,
                        plannedHangSec = hangSec,
                        plannedRestSec = restSec,
                    ),
                    segments,
                )
                // Erst NACH dem Schreiben. Der Satz „In Session gespeichert" ist eine
                // Zusicherung; stünde er schon vor dem Insert, behauptete er im Fehlerfall
                // genau das Gegenteil dessen, was passiert ist.
                savedTo = if (session != null) {
                    // Die Halle vorab laden: `hallenName` nimmt keine suspend-Funktion, die
                    // Auflösungsregel soll aber auch hier nicht ein zweites Mal dastehen.
                    val halle = session.gymId?.let { gymRepository.getById(it) }
                    val gymName = session.hallenName { halle?.name }
                    if (gymName != null) {
                        texte.hole(R.string.timer_gespeichert_in_halle, gymName)
                    } else {
                        texte.hole(R.string.timer_gespeichert_aktive_session)
                    }
                } else {
                    texte.hole(R.string.timer_gespeichert_eigenstaendig)
                }
                _uiState.value = snapshot()
            }
        }
    }

    private fun snapshot(): HangboardTimerUiState {
        val duration = when (phase) {
            TimerPhase.HANG -> hangSec
            TimerPhase.REST -> restSec
            TimerPhase.DONE -> 1
        }.coerceAtLeast(1)  // schützt vor Division durch 0 (Pause = 0 erlaubt)
        return HangboardTimerUiState(
            progress = if (phase == TimerPhase.DONE) 1f else secondsLeft.toFloat() / duration,
            time = format(secondsLeft.coerceAtLeast(0)),
            restTime = format(restSec),
            phase = phase,
            currentSet = currentSet.coerceAtMost(totalSets),
            totalSets = totalSets,
            hangSec = hangSec,
            restSec = restSec,
            isRunning = running,
            doneSummary = if (phase == TimerPhase.DONE) {
                texte.hole(
                    R.string.timer_zusammenfassung,
                    texte.mehrzahl(R.plurals.historie_saetze, totalSets, totalSets),
                    format(totalSets * hangSec),
                )
            } else null,
            savedTo = if (phase == TimerPhase.DONE) savedTo else null,
            watchConnected = watchConnected,
            presets = presets.map {
                TimerPreset(
                    id = it.id,
                    name = it.name,
                    sets = it.sets,
                    hangSec = it.hangSec,
                    restSec = it.restSec,
                )
            },
        )
    }

    private fun format(totalSeconds: Int): String =
        "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
