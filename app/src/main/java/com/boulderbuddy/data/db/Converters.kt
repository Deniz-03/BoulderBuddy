package com.boulderbuddy.data.db

import androidx.room.TypeConverter
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.HangboardWorkoutOrigin
import com.boulderbuddy.data.model.RouteStatus

/**
 * Room-TypeConverter. Datum bleibt `Long` (kein Converter nötig) — hier nur die
 * Enums ↔ String (Enum-Name).
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
