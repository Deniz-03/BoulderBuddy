package com.boulderbuddy.ui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Die beiden Dauer-Formatierungen arbeiten auf unterschiedlichen Größenordnungen:
 * [formatDurationShort] für Session-Dauern (Stunden), [formatHangTime] für Hängezeiten
 * am Hangboard (Sekunden). Die Trennung existiert, weil kurze Durchläufe beim Abrunden
 * auf ganze Minuten sonst als "0min" erscheinen.
 */
class DurationFormatTest {

    @Test
    fun hangTime_belowOneMinute_staysInSeconds() {
        // Der gemeldete Fall: 30 Sekunden Hängen dürfen nicht zu "0min" werden.
        assertThat(formatHangTime(30_000)).isEqualTo("30s")
        assertThat(formatHangTime(1_000)).isEqualTo("1s")
        assertThat(formatHangTime(59_000)).isEqualTo("59s")
    }

    @Test
    fun hangTime_fromOneMinute_showsMinutesAndSeconds() {
        assertThat(formatHangTime(60_000)).isEqualTo("1:00min")
        assertThat(formatHangTime(70_000)).isEqualTo("1:10min")
        assertThat(formatHangTime(605_000)).isEqualTo("10:05min")
    }

    @Test
    fun hangTime_zeroAndNegative_areClamped() {
        assertThat(formatHangTime(0)).isEqualTo("0s")
        assertThat(formatHangTime(-5_000)).isEqualTo("0s")
    }

    @Test
    fun sessionDuration_keepsHourScale() {
        assertThat(formatDurationShort(45 * 60_000L)).isEqualTo("45min")
        assertThat(formatDurationShort(90 * 60_000L)).isEqualTo("1,5h")
    }
}
