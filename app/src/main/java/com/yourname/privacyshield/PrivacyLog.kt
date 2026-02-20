package com.yourname.privacyshield

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "privacy_logs")
data class PrivacyLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val hardware: String, // Camera, Microphone, GPS
    val action: String,    // Started, Stopped
    val timestamp: Long = System.currentTimeMillis()
)
