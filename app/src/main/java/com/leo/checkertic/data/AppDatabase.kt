package com.leo.checkertic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.leo.checkertic.data.dao.AnalyticsDao
import com.leo.checkertic.data.dao.CategoryDao
import com.leo.checkertic.data.dao.NoteDao
import com.leo.checkertic.data.dao.ReminderDao
import com.leo.checkertic.data.dao.TaskDao
import com.leo.checkertic.data.dao.VoiceNoteDao
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.NoteActivityEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.ReminderEntity
import com.leo.checkertic.data.entity.TaskCompletionEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.data.entity.VoiceNoteEntity

@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        NoteEntity::class,
        VoiceNoteEntity::class,
        NoteActivityEntity::class,
        ReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {

        /**
         * v1 -> v2.
         *
         * Hand-written rather than destructive, because this app's whole
         * analytics feature is built on completion history the user can never
         * regenerate. A `fallbackToDestructiveMigration()` here would silently
         * delete every streak the moment they updated.
         *
         * Three things happen:
         *  1. New presentation / security / ordering columns on the three
         *     existing tables.
         *  2. `task_completions.category_id` is added and back-filled from
         *     `tasks`, so historic completions still group by category. Rows
         *     whose task has since been deleted keep -1 and are counted in
         *     totals but not attributed to a category, which is the honest
         *     answer rather than silently dropping them.
         *  3. The three new tables, plus index changes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                // -- categories ------------------------------------------------
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `background` TEXT")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `sort_mode` TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `categories` SET `created_at` = $now, `updated_at` = $now")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_pinned` ON `categories` (`pinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_order_index` ON `categories` (`order_index`)")

                // -- tasks -----------------------------------------------------
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `encrypted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `order_index` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `completed_at` INTEGER")
                db.execSQL("UPDATE `tasks` SET `created_at` = $now, `updated_at` = $now, `order_index` = `id`")
                // Seed completed_at from the newest logged completion so the
                // completed section sorts sensibly on first launch after update.
                db.execSQL(
                    """
                    UPDATE `tasks` SET `completed_at` = (
                        SELECT MAX(c.`completed_at`) FROM `task_completions` c WHERE c.`task_id` = `tasks`.`id`
                    ) WHERE `completed` = 1
                    """.trimIndent()
                )
                db.execSQL("DROP INDEX IF EXISTS `index_tasks_category_id`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_category_id_completed` ON `tasks` (`category_id`, `completed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_pinned` ON `tasks` (`pinned`)")

                // -- notes -----------------------------------------------------
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `encrypted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `background` TEXT")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `order_index` INTEGER NOT NULL DEFAULT 0")
                // No creation date was ever recorded, so the last-modified time
                // is the closest truthful stand-in.
                db.execSQL("UPDATE `notes` SET `created_at` = `updated_at`, `order_index` = `id`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_pinned` ON `notes` (`pinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updated_at` ON `notes` (`updated_at`)")

                // -- task_completions -----------------------------------------
                db.execSQL("ALTER TABLE `task_completions` ADD COLUMN `category_id` INTEGER NOT NULL DEFAULT -1")
                db.execSQL(
                    """
                    UPDATE `task_completions` SET `category_id` = COALESCE(
                        (SELECT t.`category_id` FROM `tasks` t WHERE t.`id` = `task_completions`.`task_id`), -1
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_completions_completed_at` ON `task_completions` (`completed_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_completions_category_id_completed_at` ON `task_completions` (`category_id`, `completed_at`)")

                // -- new tables ------------------------------------------------
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `voice_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `note_id` INTEGER NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `encrypted` INTEGER NOT NULL,
                        FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_notes_note_id` ON `voice_notes` (`note_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_activity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `note_id` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_activity_at` ON `note_activity` (`at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_activity_note_id_at` ON `note_activity` (`note_id`, `at`)")
                // Give the activity trend a truthful starting point instead of
                // an empty chart: every existing note gets one `created` event.
                db.execSQL("INSERT INTO `note_activity` (`note_id`, `kind`, `at`) SELECT `id`, 'created', `created_at` FROM `notes`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `owner_type` TEXT NOT NULL,
                        `owner_id` INTEGER NOT NULL,
                        `trigger_at` INTEGER NOT NULL,
                        `repeat_mode` TEXT NOT NULL,
                        `repeat_interval_days` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `last_fired_at` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_owner_type_owner_id` ON `reminders` (`owner_type`, `owner_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_trigger_at` ON `reminders` (`trigger_at`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "checker_tic.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            runCatching {
                                val now = System.currentTimeMillis()
                                db.execSQL(
                                    """
                                    INSERT OR IGNORE INTO `categories` (
                                        `id`, `name`, `order_index`, `recurrence_type`,
                                        `recurrence_custom_days`, `last_period_key`,
                                        `pinned`, `locked`, `background`, `sort_mode`,
                                        `created_at`, `updated_at`
                                    ) VALUES (
                                        1, 'General', 0, 'once',
                                        0, '',
                                        0, 0, NULL, 'manual',
                                        $now, $now
                                    )
                                    """.trimIndent()
                                )
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            runCatching {
                                val now = System.currentTimeMillis()
                                db.execSQL(
                                    """
                                    INSERT INTO `categories` (
                                        `id`, `name`, `order_index`, `recurrence_type`,
                                        `recurrence_custom_days`, `last_period_key`,
                                        `pinned`, `locked`, `background`, `sort_mode`,
                                        `created_at`, `updated_at`
                                    ) SELECT
                                        1, 'General', 0, 'once',
                                        0, '',
                                        0, 0, NULL, 'manual',
                                        $now, $now
                                    WHERE NOT EXISTS (SELECT 1 FROM `categories`)
                                    """.trimIndent()
                                )
                            }
                        }
                    })
                    // WAL is Room's default on API 16+; stated explicitly so a
                    // future change here is a deliberate one. It is what lets
                    // the widget read while the app writes without blocking.
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
