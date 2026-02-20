package com.yourname.privacyshield

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.yourname.privacyshield.ui.theme.PrivacyShieldTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            checkAndNavigate()
        }
    }

    private var permissionsGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = PrivacyDatabase.getDatabase(this)
        
        // Initial check
        checkPermissionsInternal()

        setContent {
            PrivacyShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!permissionsGranted) {
                        PermissionScreen(
                            modifier = Modifier.padding(innerPadding),
                            onRequestPermissions = { requestRequiredPermissions() }
                        )
                    } else {
                        PrivacyShieldApp(
                            modifier = Modifier.padding(innerPadding),
                            database = database
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissionsInternal() {
        val required = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val allGranted = required.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (allGranted && hasUsageStatsPermission() && isAccessibilityServiceEnabled() && isBatteryOptimizationIgnored()) {
            permissionsGranted = true
            startPrivacyService()
        }
    }

    private fun checkAndNavigate() {
        checkPermissionsInternal()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            // Check secondary permissions
            if (!hasUsageStatsPermission()) {
                requestUsageStatsPermission()
            } else if (!isAccessibilityServiceEnabled()) {
                requestAccessibilityPermission()
            } else if (!isBatteryOptimizationIgnored()) {
                requestIgnoreBatteryOptimization()
            } else {
                permissionsGranted = true
                startPrivacyService()
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "${packageName}/${PrivacyAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabledServices == null) return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun requestAccessibilityPermission() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun startPrivacyService() {
        val intent = Intent(this, PrivacyMonitorService::class.java)
        startForegroundService(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from settings
        checkPermissionsInternal()
    }
}

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Privacy Shield needs several permissions to monitor hardware usage and protect your privacy. Please grant them to continue.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
    }
}

data class LogUiModel(
    val log: PrivacyLog,
    val appLabel: String,
    val appIcon: Drawable?
)

@Composable
fun PrivacyShieldApp(
    modifier: Modifier = Modifier,
    database: PrivacyDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uiLogs by remember { mutableStateOf<List<LogUiModel>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        while(true) {
            val dbLogs = database.privacyLogDao().getAllLogs()
            uiLogs = dbLogs.map { log ->
                var label: String = log.packageName
                var icon: Drawable? = null
                if (log.packageName != "Unknown") {
                    try {
                        val appInfo = pm.getApplicationInfo(log.packageName, 0)
                        label = pm.getApplicationLabel(appInfo).toString()
                        icon = pm.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                    }
                } else {
                    label = "System Service / Unknown"
                }
                LogUiModel(log, label, icon)
            }
            delay(2000)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                exportLogsToCsv(context, it, uiLogs.map { model -> model.log })
            }
        }
    }

    val filteredLogs = remember(uiLogs, selectedFilter) {
        if (selectedFilter == "All") uiLogs
        else uiLogs.filter { it.log.hardware == selectedFilter }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Privacy Shield", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    createDocumentLauncher.launch("privacy_logs_$timeStamp.csv")
                }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    scope.launch { database.privacyLogDao().clearLogs() }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        LiveStatusDashboard()
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("All", "Camera", "Microphone", "GPS").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredLogs) { model ->
                LogItem(model)
            }
        }
    }
}

suspend fun exportLogsToCsv(context: Context, uri: Uri, logs: List<PrivacyLog>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("ID,Package Name,Hardware,Action,Timestamp\n")
                    logs.forEach { log ->
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                        writer.write("${log.id},${log.packageName},${log.hardware},${log.action},\"$date\"\n")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Logs exported successfully!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to export logs: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun LiveStatusDashboard() {
    val isCameraInUse by PrivacyStatusManager.isCameraInUse.collectAsState()
    val isMicInUse by PrivacyStatusManager.isMicInUse.collectAsState()
    val isLocationInUse by PrivacyStatusManager.isLocationInUse.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live Hardware Status", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatusIndicator("Camera", isCameraInUse)
                StatusIndicator("Mic", isMicInUse)
                StatusIndicator("GPS", isLocationInUse)
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, isActive: Boolean) {
    val color by animateColorAsState(if (isActive) Color.Red else Color.Gray, label = "color")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LogItem(model: LogUiModel) {
    val log = model.log
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val hardwareIcon = when (log.hardware) {
                    "Camera" -> Icons.Default.CameraAlt
                    "Microphone" -> Icons.Default.Mic
                    "GPS" -> Icons.Default.LocationOn
                    else -> Icons.Default.Warning
                }
                
                Icon(
                    imageVector = hardwareIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(log.hardware, style = MaterialTheme.typography.titleMedium)
                    Text(
                        log.action, 
                        color = if (log.action == "Started") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text("App: ${model.appLabel}", style = MaterialTheme.typography.bodyMedium)
                val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(log.timestamp))
                Text(date, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
