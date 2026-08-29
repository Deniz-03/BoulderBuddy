package com.boulderbuddy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.boulderbuddy.data.db.dao.GhostAnalysisDao
import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.dao.GymVisitDao
import com.boulderbuddy.data.db.dao.HangboardTemplateDao
import com.boulderbuddy.data.db.dao.HangboardWorkoutDao
import com.boulderbuddy.data.db.dao.MedienDao
import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.dao.StandMetaDao
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.boulderbuddy.data.db.entity.HangboardSegmentEntity
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.db.entity.StandMetaEntity

/**
 * Room-Datenbank der App — die einzige. Bereitgestellt von `di/DatabaseModule`.
 *
 * Zwei Regeln, an denen hier alles hängt:
 *
 * 1. **Jede Versionserhöhung braucht eine Migration** in `Migrations.kt`. Einen destruktiven
 *    Fallback gibt es seit dem Geräte-Abgleich nicht mehr; ohne passende Migration bricht
 *    der Start ab, statt Daten zu löschen. Die Versionsliste unten ist die Chronik dazu —
 *    wer erhöht, schreibt eine Zeile mehr.
 * 2. **Der Schema-Export ist Teil des Quellcodes**, nicht Build-Abfall
 *    (`room.schemaLocation` in `app/build.gradle.kts`, Dateien unter `app/schemas/`). Die
 *    Migrationen sind wörtlich daraus abgeschrieben, `tools/pruefe_migrationen.py` vergleicht
 *    beides gegeneinander, und `MigrationTest` fährt sie auf dem Gerät. Ein nicht
 *    eingecheckter Export nimmt allen dreien die Grundlage.
 */
@Database(
    entities = [
        GymEntity::class,
        GradeSystemEntity::class,
        GradeEntity::class,
        SessionEntity::class,
        RouteEntity::class,
        HangboardTemplateEntity::class,
        HangboardWorkoutEntity::class,
        HangboardSegmentEntity::class,
        GhostAnalysisEntity::class,
        StandMetaEntity::class,
        GymVisitEntity::class,
    ],
    // v2 (Phase 6): RouteEntity um name + sektor erweitert.
    // v3 (MVP-Polish): GradeSystem.gymId nullable (globale Standard-Systeme) +
    // neue hangboard_session-Tabelle (getrackte Timer-Durchläufe).
    // v4 (MVP-Polish): Farbe von der Schwierigkeit entkoppelt — Grade.color entfernt,
    // Route.color (eigener Farb-Key) ergänzt; Session.gradeSystemId (Grading pro Session).
    // v5 (Phase 7.5): neue ghost_analysis-Tabelle (gespeicherte Ghost-Climber-Analysen).
    // v6 (Phase 7 Anhang B): vereintes Hangboard-Workout-Modell — hangboard_session ersetzt
    // durch hangboard_workout (sessionId nullable = eigenständige Trainings, mode/origin)
    // + hangboard_segment (gemessene bzw. abgeleitete Hänge-/Pausendauern je Satz).
    // v7 (Sync-Plan S1): stand_meta (Herkunft des gemeinsamen Standes, E3) + Keypoint-Pfade
    // relativ zu filesDir statt absolut (E15) — eine gerätelokale Spalte darf nicht in eine
    // verglichene Zeile geraten.
    // v8 (Gym-Näherungs-Push): Gym um Koordinaten/Radius/Alerts-Toggle erweitert +
    // neue gym_visit-Tabelle (Besuchs-Log für gelernte Besuchsmuster).
    // v9: Gym.defaultGradeSystemId — beim Session-Anlegen vorgewähltes Gradsystem der Halle.
    // v10: Hallen sind löschbar, ohne Historie mitzunehmen — session.gymId und
    // grade_system.gymId auf SET NULL (vorher CASCADE) + session.gymName als Beleg, in
    // welcher Halle eine Session lief, wenn es die Halle nicht mehr gibt.
    // v11: grade_system.istStandard — ob ein System zur App gehört, hing bis dahin an
    // `gymId == null`. Seit v10 bedeutet das aber auch „Halle wurde gelöscht", und selbst
    // angelegte Systeme haben ohnehin keine Halle mehr: der Marker muss eigenständig sein.
    // v12: ghost_analysis.sessionId — eine Ghost-Analyse darf in einer Session stehen, wie
    // ein Hangboard-Workout. SET NULL statt CASCADE: die Analyse überlebt das Löschen ihrer
    // Session (siehe GhostAnalysisEntity).
    //
    // Das Feature war auf seinem Branch einmal v6 — dieselbe Nummer, die der Hangboard-Umbau
    // schon vergeben hatte. Auf einem Gerät, das beide Stände nacheinander sah, verglich Room
    // nur die Nummer, fand sie gleich und prüfte den Identity-Hash: Absturz beim Start, ohne
    // dass eine Migration je lief. Deshalb hier v8 und nicht v6.
    //
    // Ab dem Geräte-Abgleich (Sync-Plan S0) gibt es echte Migrationen für jeden Schritt:
    // siehe Migrations.kt. Wer die Version erhöht, schreibt dort die passende Migration —
    // einen destruktiven Fallback gibt es nicht mehr.
    version = 12,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BoulderBuddyDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao
    abstract fun gradeSystemDao(): GradeSystemDao
    abstract fun gradeDao(): GradeDao
    abstract fun sessionDao(): SessionDao
    abstract fun routeDao(): RouteDao
    abstract fun hangboardTemplateDao(): HangboardTemplateDao
    abstract fun hangboardWorkoutDao(): HangboardWorkoutDao
    abstract fun ghostAnalysisDao(): GhostAnalysisDao
    abstract fun standMetaDao(): StandMetaDao
    abstract fun medienDao(): MedienDao
    abstract fun gymVisitDao(): GymVisitDao

    companion object {
        const val NAME = "boulderbuddy.db"
    }
}
