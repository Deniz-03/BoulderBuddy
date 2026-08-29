package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Voreinstellung für den Hangboard-Timer (z.B. "7-3 Max Hangs"). Eigenständig, ohne FK —
 * ein Preset gehört keiner Session und keiner Halle, es ist eine Einstellung.
 */
@Entity(tableName = "hangboard_template")
data class HangboardTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sets: Int,
    /** Hängedauer je Satz in Sekunden. */
    val hangSec: Int,
    /** Pause zwischen zwei Sätzen in Sekunden. */
    val restSec: Int,
    /**
     * Pause zwischen Wiederholungen **innerhalb** eines Satzes.
     *
     * Der Timer kennt diese Ebene nicht: er zählt Sätze, keine Reps. Die Spalte wird
     * deshalb überall auf `restSec` gesetzt (siehe `SeedData` und
     * `HangboardTimerViewModel.saveCurrentAsPreset`) und nirgends gelesen. Sie steht hier
     * für den Fall, dass Repeater-Sätze dazukommen — wer sie belebt, muss den Timer
     * anfassen, nicht das Schema.
     */
    val repRestSec: Int,
)
