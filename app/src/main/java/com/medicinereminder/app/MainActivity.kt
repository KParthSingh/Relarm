package com.medicinereminder.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
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
            MedicineReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onScheduleAlarm = { delayMillis, requestCode, name, current, total ->
                            scheduleAlarm(delayMillis, requestCode, name, current, total)
                        }
                    )
                }
            }
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
fun MainScreen(onScheduleAlarm: (Long, Int, String, Int, Int) -> Unit) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository(context) }
    var alarms by remember { mutableStateOf(repository.loadAlarms()) }
    
    val chainManager = remember { ChainManager(context) }
    var isChainActive by remember { mutableStateOf(chainManager.isChainActive()) }
    var isPaused by remember { mutableStateOf(chainManager.isChainPaused()) }
    var currentChainIndex by remember { mutableIntStateOf(chainManager.getCurrentIndex()) }

    // Sync State Loop
    LaunchedEffect(Unit) {
        while (true) {
            alarms = repository.loadAlarms()
            isChainActive = chainManager.isChainActive()
            isPaused = chainManager.isChainPaused()
            currentChainIndex = chainManager.getCurrentIndex()
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (!isChainActive) {
                ExtendedFloatingActionButton(
                    onClick = { alarms = alarms + Alarm() },
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
                        currentAlarm = alarms[currentChainIndex],
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
                        pausedData = if(isPaused) chainManager.getPausedRemainingTime() else 0L,
                        onStop = {
                            android.util.Log.d("MainActivity", "STOP button clicked! Sending ACTION_STOP_CHAIN intent")
                            val intent = Intent(context, ChainService::class.java).apply {
                                action = ChainService.ACTION_STOP_CHAIN
                            }
                            context.startService(intent)
                            android.util.Log.d("MainActivity", "Stop intent sent")
                            
                            // Also clear all active alarms
                            alarms = alarms.map { it.copy(isActive = false, scheduledTime = 0L) }
                        }
                    )
                }
            } else if (alarms.isNotEmpty()) {
                // Persistent Start Button at bottom
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                         val anyActive = alarms.any { it.isActive }
                         Button(
                            onClick = {
                                chainManager.startChain()
                                val firstAlarm = alarms[0]
                                val delay = firstAlarm.getTotalSeconds() * 1000L
                                onScheduleAlarm(delay, 1, firstAlarm.name, 0, alarms.size)
                                alarms = alarms.toMutableList().apply {
                                    set(0, firstAlarm.copy(isActive = true, scheduledTime = System.currentTimeMillis() + delay))
                                }
                            },
                            enabled = !anyActive && alarms.all { it.getTotalSeconds() > 0 },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)
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
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {



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
fun StickyChainBar(
    currentIndex: Int,
    totalAlarms: Int,
    currentAlarm: Alarm,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    isPaused: Boolean,
    pausedData: Long
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isPaused) {
        while (!isPaused) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isPaused) "SEQUENCE PAUSED" else stringResource(R.string.chain_active_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.chain_info_format, currentIndex + 1, totalAlarms),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Row {
                    Button(
                        onClick = { if (isPaused) onResume() else onPause() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isPaused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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
            Spacer(modifier = Modifier.height(12.dp))
            val progress = (currentIndex + 1).toFloat() / totalAlarms.toFloat()
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            val remainingSeconds = if (isPaused) {
                (pausedData / 1000).toInt()
            } else {
                ((currentAlarm.scheduledTime - currentTime) / 1000).toInt().coerceAtLeast(0)
            }

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
                            Icons.Default.Add,
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

@Composable
fun EditAlarmContent(
    alarm: Alarm,
    onUpdate: (Alarm) -> Unit,
    onSchedule: (Long) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    
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
                onClick = { onUpdate(alarm.copy(isActive = false, scheduledTime = 0L)) },
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
    
    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set Time") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeInput(alarm.hours, 23, "Hr") { onUpdate(alarm.copy(hours = it, isActive = false)) }
                        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        TimeInput(alarm.minutes, 59, "Min") { onUpdate(alarm.copy(minutes = it, isActive = false)) }
                        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        TimeInput(alarm.seconds, 59, "Sec") { onUpdate(alarm.copy(seconds = it, isActive = false)) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Quick presets
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(5, 30, 60, 300).forEach { sec ->
                            OutlinedButton(
                                onClick = {
                                    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
                                    onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(if (sec < 60) "${sec}s" else "${sec/60}m", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun TimeInput(
    value: Int,
    max: Int,
    label: String,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { onValueChange(if (value < max) value + 1 else 0) }) {
            Text("▲")
        }
        Text(
            String.format("%02d", value),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = { onValueChange(if (value > 0) value - 1 else max) }) {
            Text("▼")
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}
