package com.yourname.privacyshield

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PrivacyLog::class], version = 1, exportSchema = false)
abstract class PrivacyDatabase : RoomDatabase() {
    abstract fun privacyLogDao(): PrivacyLogDao

    companion object {
        @Volatile
        private var INSTANCE: PrivacyDatabase? = null

        fun getDatabase(context: Context): PrivacyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrivacyDatabase::class.java,
                    "privacy_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
