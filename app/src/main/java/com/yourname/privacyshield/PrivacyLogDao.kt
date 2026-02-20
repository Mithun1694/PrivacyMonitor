package com.yourname.privacyshield

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PrivacyLogDao {
    @Insert
    suspend fun insert(log: PrivacyLog)

    @Query("SELECT * FROM privacy_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<PrivacyLog>

    @Query("DELETE FROM privacy_logs")
    suspend fun clearLogs()
}
