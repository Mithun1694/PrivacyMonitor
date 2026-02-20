package com.yourname.privacyshield

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.GnssStatus
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrivacyMonitorService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var cameraManager: CameraManager
    private lateinit var audioManager: AudioManager
    private lateinit var locationManager: LocationManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var database: PrivacyDatabase
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            super.onCameraAvailable(cameraId)
            logUsage("Camera", getResponsiblePackage(), "Stopped")
            PrivacyStatusManager.updateCameraStatus(false)
        }

        override fun onCameraUnavailable(cameraId: String) {
            super.onCameraUnavailable(cameraId)
            logUsage("Camera", getResponsiblePackage(), "Started")
            PrivacyStatusManager.updateCameraStatus(true)
        }
    }

    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            super.onRecordingConfigChanged(configs)
            if (configs.isNotEmpty()) {
                // clientPackageName is available from API 24
                var pkg: String? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        val method = AudioRecordingConfiguration::class.java.getMethod("getClientPackageName")
                        pkg = method.invoke(configs[0]) as? String
                    } catch (e: Exception) {
                        Log.e("PrivacyShield", "Error getting client package name: ${e.message}")
                    }
                }
                logUsage("Microphone", pkg ?: getResponsiblePackage(), "Started")
                PrivacyStatusManager.updateMicStatus(true)
            } else {
                logUsage("Microphone", getResponsiblePackage(), "Stopped")
                PrivacyStatusManager.updateMicStatus(false)
            }
        }
    }

    private val gnssCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : GnssStatus.Callback() {
            override fun onStarted() {
                super.onStarted()
                logUsage("GPS", getResponsiblePackage(), "Started")
                PrivacyStatusManager.updateLocationStatus(true)
            }

            override fun onStopped() {
                super.onStopped()
                logUsage("GPS", getResponsiblePackage(), "Stopped")
                PrivacyStatusManager.updateLocationStatus(false)
            }
        }
    } else {
        null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        database = PrivacyDatabase.getDatabase(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification("Privacy Monitoring Active", "Watching for hardware usage in background."))
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cameraManager.registerAvailabilityCallback(mainExecutor, cameraCallback)
        } else {
            cameraManager.registerAvailabilityCallback(cameraCallback, null)
        }
        
        audioManager.registerAudioRecordingCallback(audioRecordingCallback, null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.registerGnssStatusCallback(mainExecutor, gnssCallback)
                } else {
                    locationManager.registerGnssStatusCallback(gnssCallback, null)
                }
            } else {
                Log.w("PrivacyShield", "Missing ACCESS_FINE_LOCATION to monitor GPS")
            }
        }
    }

    private fun getResponsiblePackage(): String {
        // Precise check using UsageEvents for recent foreground activity
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 10000, time) 
        val event = UsageEvents.Event()
        var lastForegroundApp = "Unknown"
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED is the modern standard for foreground detection
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.packageName != this.packageName) {
                    lastForegroundApp = event.packageName
                }
            }
        }
        
        if (lastForegroundApp != "Unknown") return lastForegroundApp
        
        return PrivacyAccessibilityService.currentForegroundPackage
    }

    private fun logUsage(hardware: String, packageName: String, action: String) {
        if (packageName == this.packageName) return

        val timestamp = System.currentTimeMillis()
        val timestampStr = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(timestamp))
        Log.d("PrivacyShield", "[$timestampStr] $packageName $action using $hardware")
        
        serviceScope.launch {
            database.privacyLogDao().insert(
                PrivacyLog(
                    packageName = packageName,
                    hardware = hardware,
                    action = action,
                    timestamp = timestamp
                )
            )
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val channelId = "PrivacyMonitorChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Privacy Monitor Service", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.unregisterAvailabilityCallback(cameraCallback)
        audioManager.unregisterAudioRecordingCallback(audioRecordingCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
        serviceJob.cancel()
    }
}
