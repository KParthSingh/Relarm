package com.medicinereminder.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.medicinereminder.app.ui.theme.MedicineReminderTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var alarmScheduler: AlarmScheduler
    private var hasNotificationPermission by mutableStateOf(true)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        alarmScheduler = AlarmScheduler(this)
        
        NotificationHelper.createNotificationChannel(this)
        checkNotificationPermission()
        checkExactAlarmPermission()

        setContent {
            val settingsRepository = remember { SettingsRepository(this) }
            var themeMode by remember { mutableStateOf(settingsRepository.getThemeMode()) }
            var showSettings by remember { mutableStateOf(false) }
            
            MedicineReminderTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Handle back button when settings is open
                    if (showSettings) {
                        androidx.activity.compose.BackHandler {
                            showSettings = false
                        }
                        SettingsScreen(
                            onNavigateBack = { showSettings = false },
                            onThemeChanged = { themeMode = settingsRepository.getThemeMode() }
                        )
                    } else {
                        MainScreen(
                            onScheduleAlarm = { delayMillis, requestCode, name, current, total ->
                                scheduleAlarm(delayMillis, requestCode, name, current, total)
                            },
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // App going to background - pause countdown loop if hide counter is ON
        val settingsRepository = SettingsRepository(this)
        val hideCounter = settingsRepository.getDismissableCounter()
        
        Log.d("BatteryOpt", "[LIFECYCLE] App going to background - hideCounter: $hideCounter")
        
        if (hideCounter) {
            val intent = Intent(this, ChainService::class.java).apply {
                action = ChainService.ACTION_PAUSE_COUNTDOWN_LOOP
            }
            startService(intent)
        }
    }
    
    override fun onStart() {
        super.onStart()
        // App coming to foreground - resume countdown loop if hide counter is ON
        val settingsRepository = SettingsRepository(this)
        val hideCounter = settingsRepository.getDismissableCounter()
        
        Log.d("BatteryOpt", "[LIFECYCLE] App coming to foreground - hideCounter: $hideCounter")
        
        if (hideCounter) {
            val intent = Intent(this, ChainService::class.java).apply {
                action = ChainService.ACTION_RESUME_COUNTDOWN_LOOP
            }
            startService(intent)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun scheduleAlarm(delayMillis: Long, requestCode: Int, name: String, currentIndex: Int, totalAlarms: Int) {
        alarmScheduler.scheduleAlarm(delayMillis, requestCode)
        
        val chainIntent = Intent(this, ChainService::class.java).apply {
            action = ChainService.ACTION_START_CHAIN_ALARM
            putExtra(ChainService.EXTRA_END_TIME, System.currentTimeMillis() + delayMillis)
            putExtra(ChainService.EXTRA_CURRENT_INDEX, currentIndex)
            putExtra(ChainService.EXTRA_TOTAL_ALARMS, totalAlarms)
            putExtra(ChainService.EXTRA_ALARM_NAME, name)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(chainIntent)
        } else {
            startService(chainIntent)
        }
    }
}

// --- THEME moved to ui.theme package ---

// --- MAIN SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onScheduleAlarm: (Long, Int, String, Int, Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository(context) }
    var alarms by remember { mutableStateOf(repository.loadAlarms()) }
    
    val chainManager = remember { ChainManager(context) }
    var isChainActive by remember { mutableStateOf(chainManager.isChainActive()) }
    var isPaused by remember { mutableStateOf(chainManager.isChainPaused()) }
    var currentChainIndex by remember { mutableIntStateOf(chainManager.getCurrentIndex()) }
    var isAlarmRinging by remember { mutableStateOf(false) }

    // Sync State Loop
    LaunchedEffect(Unit) {
        while (true) {
            alarms = repository.loadAlarms()
            isChainActive = chainManager.isChainActive()
            isPaused = chainManager.isChainPaused()
            currentChainIndex = chainManager.getCurrentIndex()
            
            // Check if alarm is ringing from ChainManager state
            isAlarmRinging = chainManager.isAlarmRinging()
            
            delay(1000) // Poll every second for updates from Service/Receiver
        }
    }
    
    LaunchedEffect(alarms) {
        repository.saveAlarms(alarms)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.title_main),
                        fontWeight = FontWeight.Bold 
                    ) 
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (!isChainActive) {
                val settingsRepository = remember { SettingsRepository(context) }
                ExtendedFloatingActionButton(
                    onClick = { 
                        val defaultSeconds = settingsRepository.getDefaultAlarmTime()
                        alarms = alarms + Alarm(seconds = defaultSeconds)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, "Add Alarm") },
                    text = { Text(stringResource(R.string.btn_new_alarm)) }
                )
            }
        },
        bottomBar = {
            if (isChainActive) {
                if (currentChainIndex < alarms.size) {
                    StickyChainBar(
                        currentIndex = currentChainIndex,
                        totalAlarms = alarms.size,
                        isAlarmRinging = isAlarmRinging,
                        onPause = {
                            android.util.Log.d("MainActivity", "PAUSE button clicked! Sending ACTION_PAUSE_CHAIN intent")
                            val intent = Intent(context, ChainService::class.java).apply {
                                action = ChainService.ACTION_PAUSE_CHAIN
                            }
                            context.startService(intent)
                            android.util.Log.d("MainActivity", "Pause intent sent, result: success")
                        },
                        onResume = {
                            android.util.Log.d("MainActivity", "RESUME button clicked! Sending ACTION_RESUME_CHAIN intent")
                            val intent = Intent(context, ChainService::class.java).apply {
                                action = ChainService.ACTION_RESUME_CHAIN
                            }
                            context.startService(intent)
                            android.util.Log.d("MainActivity", "Resume intent sent")
                        },
                        isPaused = isPaused,
                        onStop = {
                            android.util.Log.d("MainActivity", "STOP button clicked! Sending ACTION_STOP_CHAIN intent")
                            val intent = Intent(context, ChainService::class.java).apply {
                                action = ChainService.ACTION_STOP_CHAIN
                            }
                            context.startService(intent)
                            android.util.Log.d("MainActivity", "Stop intent sent")
                            
                            // Also clear all active alarms
                            alarms = alarms.map { it.copy(isActive = false, scheduledTime = 0L) }
                        },
                        onDismiss = {
                            android.util.Log.d("MainActivity", "DISMISS button clicked! Stopping alarm")
                            val stopAlarmIntent = Intent(context, AlarmStopReceiver::class.java).apply {
                                action = "com.medicinereminder.app.STOP_ALARM"
                            }
                            context.sendBroadcast(stopAlarmIntent)
                            android.util.Log.d("MainActivity", "Dismiss broadcast sent")
                        },
                        onNext = {
                            android.util.Log.d("MainActivity", "NEXT button clicked! Sending ACTION_NEXT_ALARM intent")
                            val intent = Intent(context, ChainService::class.java).apply {
                                action = ChainService.ACTION_NEXT_ALARM
                            }
                            context.startService(intent)
                            android.util.Log.d("MainActivity", "Next alarm intent sent")
                        },
                        onPrev = {
                            android.util.Log.d("MainActivity", "PREV button clicked! Sending ACTION_PREV_ALARM intent")
                            val intent = Intent(context, ChainService::class.java).apply {
                                action = ChainService.ACTION_PREV_ALARM
                            }
                            context.startService(intent)
                            android.util.Log.d("MainActivity", "Prev alarm intent sent")
                        }
                    )
                }
            } else if (alarms.isNotEmpty()) {
                // Persistent Start Button at bottom - fills entire bottom bar
                val anyActive = alarms.any { it.isActive }
                val settingsRepository = remember { SettingsRepository(context) }
                Button(
                    onClick = {
                        chainManager.startChain()
                        val firstAlarm = alarms[0]
                        val delay = firstAlarm.getTotalSeconds() * 1000L
                        onScheduleAlarm(delay, 1, firstAlarm.name, 0, alarms.size)
                        alarms = alarms.toMutableList().apply {
                            set(0, firstAlarm.copy(isActive = true, scheduledTime = System.currentTimeMillis() + delay))
                        }
                        
                        // Check if close-on-start is enabled
                        if (settingsRepository.getCloseOnStart()) {
                            // Move app to background (go to home screen)
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(homeIntent)
                        }
                    },
                    enabled = !anyActive && alarms.all { it.getTotalSeconds() > 0 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.start_chain_btn), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.start_chain_subtitle, alarms.size),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            // Battery Optimization Warning
            BatteryOptimizationWarning()

            // ALARMS LIST
            if (alarms.isEmpty()) {
                EmptyState()
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    alarms.forEachIndexed { index, alarm ->
                        AlarmItem(
                            alarm = alarm,
                            index = index,
                            totalAlarms = alarms.size,
                            onUpdate = { updated ->
                                alarms = alarms.toMutableList().apply { set(index, updated) }
                            },
                            onSchedule = { delay ->
                                onScheduleAlarm(delay, index + 1, alarm.name, index, 1) // optimized for single alarm: treat as 1 of 1
                                alarms = alarms.toMutableList().apply {
                                    set(index, alarm.copy(
                                        isActive = true,
                                        scheduledTime = System.currentTimeMillis() + delay
                                    ))
                                }
                            },
                            onDelete = {
                                alarms = alarms.toMutableList().apply { removeAt(index) }
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    alarms = alarms.toMutableList().apply {
                                        val temp = this[index]; this[index] = this[index - 1]; this[index - 1] = temp
                                    }
                                }
                            },
                            onMoveDown = {
                                if (index < alarms.size - 1) {
                                    alarms = alarms.toMutableList().apply {
                                        val temp = this[index]; this[index] = this[index + 1]; this[index + 1] = temp
                                    }
                                }
                            },
                            onClone = {
                                alarms = alarms.toMutableList().apply { add(index + 1, alarm.clone()) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }

            // START CHAIN BUTTON (Sticky bottom if not empty)

        }
    }
}

// --- COMPONENTS ---

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("😴", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.empty_state_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BatteryOptimizationWarning() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    
    // Track visibility state
    var isVisible by remember { mutableStateOf(false) }
    
    // Check if warning should be shown
    LaunchedEffect(Unit) {
        val showWarning = ManufacturerDetector.requiresAutostartWarning() && 
                         !settingsRepository.getBatteryWarningNeverShow()
        isVisible = showWarning
    }
    
    if (isVisible) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.battery_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                // Description
                Text(
                    text = stringResource(R.string.battery_warning_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                // Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open Settings Button
                    OutlinedButton(
                        onClick = {
                            AutostartHelper.openAutostartSettings(context)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.battery_warning_btn_settings),
                            fontSize = 12.sp
                        )
                    }
                    
                    // Autostart Enabled Button
                    Button(
                        onClick = {
                            settingsRepository.setBatteryWarningNeverShow(true)
                            isVisible = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.battery_warning_btn_enabled),
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Secondary buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hide for Now Button
                    TextButton(
                        onClick = {
                            isVisible = false
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.battery_warning_btn_hide),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    // Never Show Again Button
                    TextButton(
                        onClick = {
                            settingsRepository.setBatteryWarningNeverShow(true)
                            isVisible = false
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.battery_warning_btn_never),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StickyChainBar(
    currentIndex: Int,
    totalAlarms: Int,
    isAlarmRinging: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    isPaused: Boolean,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val context = LocalContext.current
    val chainManager = remember { ChainManager(context) }
    
    // Read remaining time from ChainManager (single source of truth updated by ChainService)
    var remainingTimeMs by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(Unit) {
        while (true) {
            if (chainManager.isChainPaused()) {
                remainingTimeMs = chainManager.getPausedRemainingTime()
            } else {
                val endTime = chainManager.getEndTime()
                if (endTime > 0) {
                    remainingTimeMs = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                } else {
                    remainingTimeMs = 0
                }
            }
            delay(100) // Poll frequently for smooth updates
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // DISMISS button when alarm is ringing (prominent placement)
            if (isAlarmRinging) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "⏰ DISMISS ALARM",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isAlarmRinging) "🔔 ALARM RINGING" 
                        else if (isPaused) "SEQUENCE PAUSED" 
                        else stringResource(R.string.chain_active_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlarmRinging) MaterialTheme.colorScheme.primary
                               else if (isPaused) MaterialTheme.colorScheme.error 
                               else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.chain_info_format, currentIndex + 1, totalAlarms),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (!isAlarmRinging) {
                    Row {
                        Button(
                            onClick = { if (isPaused) onResume() else onPause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isPaused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(if (isPaused) "RESUME" else "PAUSE")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("STOP")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val progress = (currentIndex + 1).toFloat() / totalAlarms.toFloat()
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            val remainingSeconds = (remainingTimeMs / 1000).toInt().coerceAtLeast(0)
            
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPrev,
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous alarm",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PREV")
                }
                
                Button(
                    onClick = onNext,
                    enabled = currentIndex < totalAlarms - 1,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("NEXT")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next alarm",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (isPaused) "Resumes in: ${formatTime(remainingSeconds)}" 
                    else "Next alarm: ${formatTime(remainingSeconds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                Text(
                    stringResource(R.string.chain_remaining_format, totalAlarms - currentIndex - 1),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmItem(
    alarm: Alarm,
    index: Int,
    totalAlarms: Int,
    onUpdate: (Alarm) -> Unit,
    onSchedule: (Long) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClone: () -> Unit
) {

    
    // Auto-reset
    LaunchedEffect(alarm.isActive, alarm.scheduledTime) {
        if (alarm.isActive && alarm.scheduledTime > 0) {
            val remaining = alarm.scheduledTime - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            onUpdate(alarm.copy(isActive = false, scheduledTime = 0L))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (alarm.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (alarm.name.isNotBlank()) alarm.name else "Alarm ${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                // Action icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalAlarms - 1
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (index < totalAlarms - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(onClick = onClone) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Clone"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Content
            EditAlarmContent(
                    alarm = alarm,
                    onUpdate = onUpdate,
                    onSchedule = onSchedule
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmContent(
    alarm: Alarm,
    onUpdate: (Alarm) -> Unit,
    onSchedule: (Long) -> Unit
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Sound picker launcher
    val soundPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onUpdate(alarm.copy(soundUri = uri?.toString()))
        }
    }
    
    // Helper to get sound name
    fun getSoundName(uri: String?): String {
        if (uri == null) return "Default Alarm Sound"
        return try {
            val ringtone = RingtoneManager.getRingtone(context, Uri.parse(uri))
            ringtone.getTitle(context)
        } catch (e: Exception) {
            "Custom Sound"
        }
    }
    
    Column {
        OutlinedTextField(
            value = alarm.name,
            onValueChange = { onUpdate(alarm.copy(name = it)) },
            label = { Text("Alarm Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Time selector button
        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.MoreVert, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(alarm.getFormattedTime())
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Sound selector button
        OutlinedButton(
            onClick = {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                    alarm.soundUri?.let { uri ->
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(uri))
                    }
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                }
                soundPickerLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.MoreVert, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(getSoundName(alarm.soundUri))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Start button
        Button(
            onClick = { onSchedule(alarm.getTotalSeconds() * 1000L) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !alarm.isActive && alarm.getTotalSeconds() > 0
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.start_alarm_now))
        }
        
        // Stop button (when active)
        if (alarm.isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { 
                    // Get the alarm index from the repository to determine request code
                    val repository = AlarmRepository(context)
                    val allAlarms = repository.loadAlarms()
                    val alarmIndex = allAlarms.indexOf(alarm)
                    val requestCode = alarmIndex + 1
                    
                    // Cancel the scheduled alarm
                    val alarmScheduler = AlarmScheduler(context)
                    alarmScheduler.cancelAlarm(requestCode)
                    
                    // Stop the ChainService (which manages the notification)
                    val stopIntent = Intent(context, ChainService::class.java).apply {
                        action = ChainService.ACTION_STOP_CHAIN
                    }
                    context.startService(stopIntent)
                    
                    // Clear alarm ringing state
                    val chainManager = ChainManager(context)
                    chainManager.setAlarmRinging(false)
                    
                    // Update UI state
                    onUpdate(alarm.copy(isActive = false, scheduledTime = 0L))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Alarm")
            }
        }
    }
    
    // Duration Picker Bottom Sheet
    if (showTimePicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTimePicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Set Duration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                SimpleDurationPicker(
                    hours = alarm.hours,
                    minutes = alarm.minutes,
                    seconds = alarm.seconds,
                    onTimeChange = { h, m, s ->
                        onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false))
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                // Quick Add
                Text(
                    "Quick Add", 
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // First row: seconds
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(5, 10, 30, 60).forEach { sec ->
                        OutlinedButton(
                            onClick = {
                                val currentTotal = alarm.getTotalSeconds()
                                val newTotal = currentTotal + sec
                                val h = newTotal / 3600
                                val m = (newTotal % 3600) / 60
                                val s = newTotal % 60
                                onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                        ) {
                            Text("+${if (sec < 60) "${sec}s" else "${sec/60}m"}", fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Second row: minutes and hours
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(300, 1800, 3600).forEach { sec ->
                        OutlinedButton(
                            onClick = {
                                val currentTotal = alarm.getTotalSeconds()
                                val newTotal = currentTotal + sec
                                val h = newTotal / 3600
                                val m = (newTotal % 3600) / 60
                                val s = newTotal % 60
                                onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                        ) {
                            Text("+${if (sec < 3600) "${sec/60}m" else "${sec/3600}h"}", fontSize = 12.sp)
                        }
                    }
                    // Empty space to balance the row
                    Spacer(modifier = Modifier.weight(1f).padding(horizontal = 2.dp))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Quick Subtract
                Text(
                    "Quick Subtract", 
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // First row: seconds
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(5, 10, 30, 60).forEach { sec ->
                        OutlinedButton(
                            onClick = {
                                val currentTotal = alarm.getTotalSeconds()
                                val newTotal = (currentTotal - sec).coerceAtLeast(0)
                                val h = newTotal / 3600
                                val m = (newTotal % 3600) / 60
                                val s = newTotal % 60
                                onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("-${if (sec < 60) "${sec}s" else "${sec/60}m"}", fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Second row: minutes and hours
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(300, 1800, 3600).forEach { sec ->
                        OutlinedButton(
                            onClick = {
                                val currentTotal = alarm.getTotalSeconds()
                                val newTotal = (currentTotal - sec).coerceAtLeast(0)
                                val h = newTotal / 3600
                                val m = (newTotal % 3600) / 60
                                val s = newTotal % 60
                                onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("-${if (sec < 3600) "${sec/60}m" else "${sec/3600}h"}", fontSize = 12.sp)
                        }
                    }
                    // Empty space to balance the row
                    Spacer(modifier = Modifier.weight(1f).padding(horizontal = 2.dp))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { showTimePicker = false },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Done")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
           else if (m > 0) String.format(Locale.getDefault(), "%d:%02d", m, s)
           else "${s}s"
}
