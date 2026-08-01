package com.boulderbuddy.wear.sensing

/** Label-Wechsel aus einer Aufnahme (B.5.1): ab [timeMs] gilt [label] (HANG/REST/NONE). */
data class LabelMark(val timeMs: Long, val label: String)

/** Geparster Inhalt eines Sensor-Logs: Sample-Strom + Label-Zeitstrahl. */
data class ParsedSensorLog(
    val samples: List<SensorSample>,
    val labels: List<LabelMark>,
)

/**
 * Parser für das CSV-Format des [SensorLoggingService] (B.5.1) — Android-frei, damit
 * aufgezeichnete Logs auf der JVM gegen den [HangDetector] abgespielt werden können
 * (B.5.4) und die Offline-Kalibrierung (B.5.3) dieselbe Leselogik nutzt.
 *
 * Format: `tMs;GRAV|LIN;x;y;z` bzw. `tMs;LABEL;<label>`; `#`-Zeilen sind Kommentare.
 * Defekte Zeilen (abgerissene Aufnahme) werden übersprungen statt zu werfen.
 */
object SensorLogParser {

    fun parse(lines: Sequence<String>): ParsedSensorLog {
        val samples = mutableListOf<SensorSample>()
        val labels = mutableListOf<LabelMark>()
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split(';')
            if (parts.size < 3) return@forEach
            val timeMs = parts[0].toLongOrNull() ?: return@forEach
            when (parts[1]) {
                "LABEL" -> labels.add(LabelMark(timeMs, parts[2]))
                "GRAV", "LIN" -> {
                    if (parts.size != 5) return@forEach
                    val x = parts[2].toFloatOrNull() ?: return@forEach
                    val y = parts[3].toFloatOrNull() ?: return@forEach
                    val z = parts[4].toFloatOrNull() ?: return@forEach
                    samples.add(
                        SensorSample(
                            timeMs = timeMs,
                            type = if (parts[1] == "GRAV") SampleType.GRAVITY else SampleType.LINEAR,
                            x = x, y = y, z = z,
                        ),
                    )
                }
            }
        }
        return ParsedSensorLog(samples = samples, labels = labels)
    }

    fun parse(text: String): ParsedSensorLog = parse(text.lineSequence())
}
