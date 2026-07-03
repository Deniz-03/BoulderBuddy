package com.boulderbuddy.data.db

import androidx.room.TypeConverter
import com.boulderbuddy.data.model.RouteStatus

/**
 * Room-TypeConverter. Datum bleibt `Long` (kein Converter nötig) — hier nur der
 * [RouteStatus] ↔ String (Enum-Name).
 */
class Converters {
    @TypeConverter
    fun fromRouteStatus(status: RouteStatus): String = status.name

    @TypeConverter
    fun toRouteStatus(value: String): RouteStatus = RouteStatus.valueOf(value)
}
