package com.boulderbuddy.data.db

import androidx.room.TypeConverter
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.HangboardWorkoutOrigin
import com.boulderbuddy.data.model.RouteStatus

/**
 * Room-TypeConverter. Datum bleibt `Long` (kein Converter nötig) — hier nur die
 * Enums ↔ String (Enum-Name).
 *
 * Der Name und nicht `ordinal`: eine Zahl in der Datenbank bindet die Reihenfolge der
 * Enum-Konstanten für immer, ein Name überlebt das Einsortieren eines neuen Wertes.
 *
 * Preis dieser Wahl: `valueOf` wirft, wenn ein Wert in der Zeile steht, den diese
 * App-Fassung nicht kennt. Das ist beim Geräte-Abgleich denkbar (neueres Gerät schreibt,
 * älteres liest) und würde beim Lesen der Zeile knallen statt still zu ignorieren — bislang
 * kein Problem, weil kein Enum seit Bestehen einen Wert dazubekommen hat. Wer einen ergänzt,
 * sollte hier einen Rückfall auf einen bekannten Wert einbauen.
 */
class Converters {
    @TypeConverter
    fun fromRouteStatus(status: RouteStatus): String = status.name

    @TypeConverter
    fun toRouteStatus(value: String): RouteStatus = RouteStatus.valueOf(value)

    @TypeConverter
    fun fromHangboardWorkoutMode(mode: HangboardWorkoutMode): String = mode.name

    @TypeConverter
    fun toHangboardWorkoutMode(value: String): HangboardWorkoutMode =
        HangboardWorkoutMode.valueOf(value)

    @TypeConverter
    fun fromHangboardWorkoutOrigin(origin: HangboardWorkoutOrigin): String = origin.name

    @TypeConverter
    fun toHangboardWorkoutOrigin(value: String): HangboardWorkoutOrigin =
        HangboardWorkoutOrigin.valueOf(value)
}
