package com.boulderbuddy.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Handgeschriebene Room-Migrationen v1→v6 (Sync-Plan S0).
 *
 * Vorher lief die App auf `fallbackToDestructiveMigration` — jedes Schema-Update hätte die
 * Daten gelöscht, also genau das, was der Geräte-Abgleich übertragbar machen soll. Die
 * Migrationen sind deshalb Voraussetzung für den Abgleich, nicht Beiwerk.
 *
 * Muster für Änderungen, die SQLite nicht per `ALTER TABLE` kann (Spalte entfernen,
 * `NOT NULL` lockern, Fremdschlüssel ergänzen): neue Tabelle `_new_x` anlegen, Zeilen
 * kopieren, alte Tabelle droppen, umbenennen, Indizes neu anlegen — dieselbe Reihenfolge,
 * die Room für Auto-Migrationen erzeugt. Fremdschlüssel sind während der Migration aus
 * (Room schaltet sie erst in `onOpen` ein, also nach dem Upgrade).
 *
 * Die `CREATE TABLE`-Anweisungen sind wörtlich aus `app/schemas/<version>.json` übernommen;
 * weicht hier ein Zeichen ab, schlägt Rooms Schema-Prüfung beim Öffnen fehl.
 */

/**
 * v1→v2: `route` bekommt Name und Sektor.
 *
 * `name` ist `NOT NULL` ohne Entity-seitigen Default. `ALTER TABLE ADD COLUMN` bräuchte
 * dafür eine `DEFAULT`-Klausel, die dann dauerhaft im Schema stünde — Rooms Schema-Prüfung
 * vergliche danach eine Spalte, die die Entity so nicht beschreibt. Deshalb auch hier neu
 * anlegen und kopieren; die Bestandszeilen bekommen den leeren Namen einmalig beim Kopieren.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_route` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`gradeId` INTEGER, " +
                "`name` TEXT NOT NULL, " +
                "`sektor` TEXT, " +
                "`attempts` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`mediaUri` TEXT, " +
                "`notes` TEXT, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`gradeId`) REFERENCES `grade`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "INSERT INTO `_new_route` " +
                "(`id`,`sessionId`,`gradeId`,`name`,`sektor`,`attempts`,`status`," +
                "`mediaUri`,`notes`) " +
                "SELECT `id`,`sessionId`,`gradeId`,'',NULL,`attempts`,`status`," +
                "`mediaUri`,`notes` FROM `route`",
        )
        db.execSQL("DROP TABLE `route`")
        db.execSQL("ALTER TABLE `_new_route` RENAME TO `route`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_sessionId` ON `route` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_gradeId` ON `route` (`gradeId`)")
    }
}

/**
 * v2→v3: `grade_system.gymId` wird nullable (globale Standard-Systeme ohne Hallen-Anker)
 * und die Tabelle `hangboard_session` kommt dazu.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_grade_system` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`gymId` INTEGER, " +
                "`name` TEXT NOT NULL, " +
                "FOREIGN KEY(`gymId`) REFERENCES `gym`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `_new_grade_system` (`id`,`gymId`,`name`) " +
                "SELECT `id`,`gymId`,`name` FROM `grade_system`",
        )
        db.execSQL("DROP TABLE `grade_system`")
        db.execSQL("ALTER TABLE `_new_grade_system` RENAME TO `grade_system`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_grade_system_gymId` ON `grade_system` (`gymId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `hangboard_session` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`completedSets` INTEGER NOT NULL, " +
                "`totalSets` INTEGER NOT NULL, " +
                "`hangSec` INTEGER NOT NULL, " +
                "`restSec` INTEGER NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hangboard_session_sessionId` " +
                "ON `hangboard_session` (`sessionId`)",
        )
    }
}

/**
 * v3→v4: Farbe von der Schwierigkeit entkoppelt — `grade.color` fällt weg, `route.color`
 * kommt dazu; `session` bekommt ein eigenes Gradsystem (`gradeSystemId`).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // grade: Spalte `color` entfernen — geht nur über Neuanlegen.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_grade` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`systemId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "FOREIGN KEY(`systemId`) REFERENCES `grade_system`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `_new_grade` (`id`,`systemId`,`label`,`sortOrder`) " +
                "SELECT `id`,`systemId`,`label`,`sortOrder` FROM `grade`",
        )
        db.execSQL("DROP TABLE `grade`")
        db.execSQL("ALTER TABLE `_new_grade` RENAME TO `grade`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_grade_systemId` ON `grade` (`systemId`)")

        // session: neuer Fremdschlüssel — `ALTER TABLE` kann keine FKs ergänzen.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_session` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`gymId` INTEGER NOT NULL, " +
                "`gradeSystemId` INTEGER, " +
                "`date` INTEGER NOT NULL, " +
                "`durationMin` INTEGER, " +
                "`notes` TEXT, " +
                "`endedAt` INTEGER, " +
                "FOREIGN KEY(`gymId`) REFERENCES `gym`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`gradeSystemId`) REFERENCES `grade_system`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "INSERT INTO `_new_session` " +
                "(`id`,`gymId`,`gradeSystemId`,`date`,`durationMin`,`notes`,`endedAt`) " +
                "SELECT `id`,`gymId`,NULL,`date`,`durationMin`,`notes`,`endedAt` FROM `session`",
        )
        db.execSQL("DROP TABLE `session`")
        db.execSQL("ALTER TABLE `_new_session` RENAME TO `session`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_gymId` ON `session` (`gymId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_gradeSystemId` " +
                "ON `session` (`gradeSystemId`)",
        )

        db.execSQL("ALTER TABLE `route` ADD COLUMN `color` TEXT")
    }
}

/** v4→v5: gespeicherte Ghost-Climber-Analysen. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ghost_analysis` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`refMediaUri` TEXT NOT NULL, " +
                "`cmpMediaUri` TEXT NOT NULL, " +
                "`refKeypointsPath` TEXT NOT NULL, " +
                "`cmpKeypointsPath` TEXT NOT NULL, " +
                "`homographyCmpJson` TEXT NOT NULL, " +
                "`routePathJson` TEXT NOT NULL, " +
                "`suggestedMode` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
    }
}

/**
 * v5→v6: `hangboard_session` wird zum vereinten Modell aus `hangboard_workout` +
 * `hangboard_segment`.
 *
 * Die Bestandszeilen wandern mit, statt verworfen zu werden: ein alter Durchlauf war immer
 * manuell und immer am Phone (die Uhr kam erst mit v6), seine Sätze lassen sich aus der
 * Vorgabe ableiten. `hangboard_session.date` war der *Abschluss* — daraus folgt `endedAt`
 * direkt und `startedAt` über die Summe der geplanten Dauern.
 *
 * Die alte `id` wird als `hangboard_workout.id` weiterverwendet: nur so lassen sich die
 * Segmente in einem zweiten `INSERT` zuordnen, ohne die Zeilen einzeln zu lesen.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `hangboard_workout` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER, " +
                "`mode` TEXT NOT NULL, " +
                "`origin` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER NOT NULL, " +
                "`plannedSets` INTEGER, " +
                "`plannedHangSec` INTEGER, " +
                "`plannedRestSec` INTEGER, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hangboard_workout_sessionId` " +
                "ON `hangboard_workout` (`sessionId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `hangboard_segment` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`workoutId` INTEGER NOT NULL, " +
                "`setIndex` INTEGER NOT NULL, " +
                "`hangMs` INTEGER NOT NULL, " +
                "`restMs` INTEGER NOT NULL, " +
                "FOREIGN KEY(`workoutId`) REFERENCES `hangboard_workout`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hangboard_segment_workoutId` " +
                "ON `hangboard_segment` (`workoutId`)",
        )

        // Ein Durchlauf mit n absolvierten Sätzen dauerte n Hänge und n-1 Pausen.
        db.execSQL(
            "INSERT INTO `hangboard_workout` " +
                "(`id`,`sessionId`,`mode`,`origin`,`startedAt`,`endedAt`," +
                "`plannedSets`,`plannedHangSec`,`plannedRestSec`) " +
                "SELECT `id`, `sessionId`, 'MANUAL', 'PHONE', " +
                "`date` - (`completedSets` * `hangSec` + " +
                "MAX(`completedSets` - 1, 0) * `restSec`) * 1000, " +
                "`date`, `totalSets`, `hangSec`, `restSec` " +
                "FROM `hangboard_session`",
        )

        // Je absolviertem Satz eine Segment-Zeile; nach dem letzten Satz gibt es keine Pause.
        db.execSQL(
            "INSERT INTO `hangboard_segment` (`workoutId`,`setIndex`,`hangMs`,`restMs`) " +
                "WITH RECURSIVE saetze(`workoutId`,`setIndex`,`completedSets`," +
                "`hangSec`,`restSec`) AS (" +
                "SELECT `id`, 0, `completedSets`, `hangSec`, `restSec` " +
                "FROM `hangboard_session` WHERE `completedSets` > 0 " +
                "UNION ALL " +
                "SELECT `workoutId`, `setIndex` + 1, `completedSets`, `hangSec`, `restSec` " +
                "FROM saetze WHERE `setIndex` + 1 < `completedSets`) " +
                "SELECT `workoutId`, `setIndex`, `hangSec` * 1000, " +
                "CASE WHEN `setIndex` = `completedSets` - 1 THEN 0 ELSE `restSec` * 1000 END " +
                "FROM saetze",
        )

        db.execSQL("DROP TABLE `hangboard_session`")
    }
}

/**
 * v6→v7 (Sync-Plan S1): Herkunft des Standes und geräteunabhängige Verweise.
 *
 * Zwei Dinge, die beide zum Abgleich gehören:
 *
 * 1. `stand_meta` — die Ein-Zeilen-Tabelle mit der Herkunft (E3). Sie wird **leer** angelegt:
 *    eine fehlende Zeile heißt „noch nie abgeglichen", und genau das trifft auf jedes Gerät
 *    zu, das diese Migration fährt.
 *
 * 2. Die Keypoint-Pfade in `ghost_analysis` werden von absolut auf relativ zu `filesDir`
 *    umgestellt (E15). Ein absoluter Pfad ist gerätelokal; stünde er weiter in einer
 *    verglichenen Zeile, wäre jede Analyse bei jedem Abgleich ein Konflikt (Ablauf 31).
 *
 * Der `filesDir` ist in der Migration nicht bekannt — die SQL-Anweisungen dürfen von keinem
 * Gerätepfad abhängen, sonst wären sie selbst gerätelokal. Sie schneiden deshalb bis
 * einschließlich `/files/` ab: `…/com.boulderbuddy/files/ghost/pose_x.json` → `ghost/…`.
 * Der Store hat die Pfade immer unter `filesDir` gebaut, also greift die Regel für jede
 * Zeile, die diese App je geschrieben hat. Zeilen ohne `/files/` bleiben unangetastet —
 * die sind entweder schon relativ oder stammen nicht von hier.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stand_meta` (" +
                "`id` INTEGER PRIMARY KEY NOT NULL, " +
                "`generation` INTEGER NOT NULL, " +
                "`erzeugtVon` TEXT NOT NULL, " +
                "`basiertAuf` INTEGER)",
        )

        // Bewusst ausgeschrieben statt über eine Schleife: Migrations-SQL soll wörtlich
        // dastehen, damit `tools/pruefe_migrationen.py` es lesen und nachfahren kann.
        db.execSQL(
            "UPDATE `ghost_analysis` " +
                "SET `refKeypointsPath` = " +
                "substr(`refKeypointsPath`, instr(`refKeypointsPath`, '/files/') + 7) " +
                "WHERE instr(`refKeypointsPath`, '/files/') > 0",
        )
        db.execSQL(
            "UPDATE `ghost_analysis` " +
                "SET `cmpKeypointsPath` = " +
                "substr(`cmpKeypointsPath`, instr(`cmpKeypointsPath`, '/files/') + 7) " +
                "WHERE instr(`cmpKeypointsPath`, '/files/') > 0",
        )
    }
}

/** Alle Migrationen in einer Liste — so kann `DatabaseModule` sie am Stück übergeben. */
val ALLE_MIGRATIONEN: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
)
