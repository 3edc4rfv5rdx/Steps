package xx.steps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import xx.steps.DATABASE_NAME

/** The whole store: recorded days plus the one-row sensor sync state. */
@Database(entities = [DaySteps::class, SyncStateRow::class], version = 1, exportSchema = true)
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
