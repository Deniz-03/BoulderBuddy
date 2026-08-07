package com.boulderbuddy.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prüft die handgeschriebenen Migrationen aus [ALLE_MIGRATIONEN] (Sync-Plan S0).
 *
 * Zwei Dinge stehen auf dem Spiel und werden getrennt geprüft: dass das *Schema* am Ende
 * exakt dem exportierten v6 entspricht (`runMigrationsAndValidate`), und dass die *Daten*
 * den Weg überleben — Letzteres prüft die Validierung ausdrücklich nicht.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BoulderBuddyDatabase::class.java,
    )

    @Test
    fun migriert_v1_bis_v6_und_behaelt_die_daten() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO gym (id, name, location) VALUES (1, 'Halle Nord', 'Berlin')")
            db.execSQL("INSERT INTO grade_system (id, gymId, name) VALUES (1, 1, 'Farbsystem')")
            db.execSQL(
                "INSERT INTO grade (id, systemId, label, color, sortOrder) " +
                    "VALUES (1, 1, '6b', 'red', 0)",
            )
            db.execSQL(
                "INSERT INTO session (id, gymId, date, durationMin, notes, endedAt) " +
                    "VALUES (1, 1, 1000, 90, 'guter Tag', 2000)",
            )
            db.execSQL(
                "INSERT INTO route (id, sessionId, gradeId, attempts, status, mediaUri, notes) " +
                    "VALUES (1, 1, 1, 3, 'TOP', NULL, 'zweiter Versuch saß')",
            )
            db.execSQL(
                "INSERT INTO hangboard_template (id, name, sets, hangSec, restSec, repRestSec) " +
                    "VALUES (1, '7-3 Max Hangs', 6, 7, 3, 180)",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, *ALLE_MIGRATIONEN)

        db.query("SELECT name, sektor, color, attempts, notes FROM route WHERE id = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            // Neue Spalten: der Default der Entity, nicht irgendein Platzhalter.
            assertThat(c.getString(0)).isEmpty()
            assertThat(c.isNull(1)).isTrue()
            assertThat(c.isNull(2)).isTrue()
            // Alte Spalten unangetastet.
            assertThat(c.getInt(3)).isEqualTo(3)
            assertThat(c.getString(4)).isEqualTo("zweiter Versuch saß")
        }
        db.query("SELECT gymId, gradeSystemId, notes FROM session WHERE id = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
            assertThat(c.isNull(1)).isTrue()
            assertThat(c.getString(2)).isEqualTo("guter Tag")
        }
        db.query("SELECT label, sortOrder FROM grade WHERE id = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("6b")
        }
        db.query("SELECT gymId FROM grade_system WHERE id = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
        db.query("SELECT name FROM hangboard_template WHERE id = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("7-3 Max Hangs")
        }
        db.close()
    }

    @Test
    fun v2_zu_v3_erlaubt_gradsysteme_ohne_halle() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO gym (id, name, location) VALUES (1, 'Halle Nord', NULL)")
            db.execSQL("INSERT INTO grade_system (id, gymId, name) VALUES (1, 1, 'Farbsystem')")
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALLE_MIGRATIONEN).use { db ->
            // Das war der Zweck der Migration: globale Systeme ohne Hallen-Anker.
            db.execSQL("INSERT INTO grade_system (id, gymId, name) VALUES (2, NULL, 'V-Scale')")
            db.query("SELECT COUNT(*) FROM grade_system WHERE gymId IS NULL").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
        }
    }

    @Test
    fun v5_zu_v6_macht_aus_einem_durchlauf_workout_plus_saetze() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL("INSERT INTO gym (id, name, location) VALUES (1, 'Halle Nord', NULL)")
            db.execSQL(
                "INSERT INTO session (id, gymId, gradeSystemId, date, durationMin, notes, endedAt) " +
                    "VALUES (1, 1, NULL, 1000, NULL, NULL, NULL)",
            )
            // 4 von 6 Sätzen geschafft, 7 s Hang / 3 s Pause, fertig bei t = 100_000.
            db.execSQL(
                "INSERT INTO hangboard_session " +
                    "(id, sessionId, completedSets, totalSets, hangSec, restSec, date) " +
                    "VALUES (1, 1, 4, 6, 7, 3, 100000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, *ALLE_MIGRATIONEN).use { db ->
            db.query(
                "SELECT sessionId, mode, origin, startedAt, endedAt, " +
                    "plannedSets, plannedHangSec, plannedRestSec FROM hangboard_workout",
            ).use { c ->
                assertThat(c.count).isEqualTo(1)
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(1)
                assertThat(c.getString(1)).isEqualTo("MANUAL")
                assertThat(c.getString(2)).isEqualTo("PHONE")
                // 4 Hänge à 7 s + 3 Pausen à 3 s = 37 s vor dem Abschluss.
                assertThat(c.getLong(3)).isEqualTo(100_000L - 37_000L)
                assertThat(c.getLong(4)).isEqualTo(100_000L)
                assertThat(c.getInt(5)).isEqualTo(6)
                assertThat(c.getInt(6)).isEqualTo(7)
                assertThat(c.getInt(7)).isEqualTo(3)
            }
            db.query(
                "SELECT setIndex, hangMs, restMs FROM hangboard_segment " +
                    "WHERE workoutId = 1 ORDER BY setIndex",
            ).use { c ->
                assertThat(c.count).isEqualTo(4)
                val hangMs = mutableListOf<Long>()
                val restMs = mutableListOf<Long>()
                while (c.moveToNext()) {
                    hangMs += c.getLong(1)
                    restMs += c.getLong(2)
                }
                assertThat(hangMs).containsExactly(7_000L, 7_000L, 7_000L, 7_000L).inOrder()
                // Nach dem letzten Satz keine Pause.
                assertThat(restMs).containsExactly(3_000L, 3_000L, 3_000L, 0L).inOrder()
            }
        }
    }

    @Test
    fun v5_zu_v6_uebersteht_einen_abgebrochenen_durchlauf_ohne_satz() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL("INSERT INTO gym (id, name, location) VALUES (1, 'Halle Nord', NULL)")
            db.execSQL(
                "INSERT INTO session (id, gymId, gradeSystemId, date, durationMin, notes, endedAt) " +
                    "VALUES (1, 1, NULL, 1000, NULL, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO hangboard_session " +
                    "(id, sessionId, completedSets, totalSets, hangSec, restSec, date) " +
                    "VALUES (1, 1, 0, 6, 7, 3, 100000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, *ALLE_MIGRATIONEN).use { db ->
            db.query("SELECT startedAt, endedAt FROM hangboard_workout").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                // Ohne absolvierten Satz fällt Start auf Ende — kein negativer Zeitraum.
                assertThat(c.getLong(0)).isEqualTo(100_000L)
                assertThat(c.getLong(1)).isEqualTo(100_000L)
            }
            db.query("SELECT COUNT(*) FROM hangboard_segment").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun v6_zu_v7_macht_die_keypoint_pfade_geraeteunabhaengig() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO ghost_analysis (id, refMediaUri, cmpMediaUri, refKeypointsPath, " +
                    "cmpKeypointsPath, homographyCmpJson, routePathJson, suggestedMode, " +
                    "createdAt) VALUES (1, 'content://x/a.mp4', 'content://x/b.mp4', " +
                    "'/data/user/0/com.boulderbuddy/files/ghost/pose_aaa.json', " +
                    "'/data/user/0/com.boulderbuddy/files/ghost/pose_bbb.json', " +
                    "'[]', '[]', 'OVERLAY', 1000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, *ALLE_MIGRATIONEN).use { db ->
            db.query("SELECT refKeypointsPath, cmpKeypointsPath FROM ghost_analysis").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                // Nach der Umstellung trägt dieselbe Analyse auf jedem Gerät denselben Wert —
                // sonst wäre sie bei jedem Abgleich ein Konflikt (Ablauf 31).
                assertThat(c.getString(0)).isEqualTo("ghost/pose_aaa.json")
                assertThat(c.getString(1)).isEqualTo("ghost/pose_bbb.json")
            }
            // Ohne Zeile in stand_meta gilt das Gerät als „noch nie abgeglichen" (E3).
            db.query("SELECT COUNT(*) FROM stand_meta").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(0)
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
