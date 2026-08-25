package xx.steps.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import xx.steps.DATABASE_NAME

/**
 * The whole store: recorded days, their intra-day breakdown, and the one-row sensor sync state.
 *
 * Version 2 only adds the `day_slots` table, which Room can migrate to on its own from the
 * exported schema — a phone that has been counting keeps every day it has recorded, and simply has
 * no breakdown for the days walked before this version.
 */
@Database(
    entities = [DaySteps::class, SyncStateRow::class, DaySlot::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stepsDao(): StepsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }


        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
