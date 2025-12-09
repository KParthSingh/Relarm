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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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
        
        // Create notification channel
        NotificationHelper.createNotificationChannel(this)
        
        // Check and request notification permission (Android 13+)
        checkNotificationPermission()
        
        // Check exact alarm permission
        checkExactAlarmPermission()

        setContent {
            MedicineReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onScheduleAlarm = { delayMillis, requestCode ->
                            scheduleAlarm(delayMillis, requestCode)
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
                // Guide user to settings
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun scheduleAlarm(delayMillis: Long, requestCode: Int) {
        alarmScheduler.scheduleAlarm(delayMillis, requestCode)
        
        // Start countdown notification service
        val countdownIntent = Intent(this, CountdownService::class.java).apply {
            action = CountdownService.ACTION_START_COUNTDOWN
            putExtra(CountdownService.EXTRA_END_TIME, System.currentTimeMillis() + delayMillis)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(countdownIntent)
        } else {
            startService(countdownIntent)
        }
    }
}

@Composable
fun MedicineReminderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFFF5F5F5)
        ),
        content = content
    )
}

@Composable
fun MainScreen(onScheduleAlarm: (Long, Int) -> Unit) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository(context) }
    var alarms by remember { mutableStateOf(repository.loadAlarms()) }
    
    // Save alarms whenever they change
    LaunchedEffect(alarms) {
        repository.saveAlarms(alarms)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App title and add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Medicine Reminder",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6200EE)
            )
            
            Button(
                onClick = {
                    alarms = alarms + Alarm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE)
                )
            ) {
                Text("+ New Alarm")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Alarm list
        if (alarms.isEmpty()) {
            // Empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⏰",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No alarms yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap '+ New Alarm' to create your first alarm",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Scrollable alarm list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                alarms.forEachIndexed { index, alarm ->
                    Column {
                        AlarmCard(
                            alarm = alarm,
                            index = index,
                            onUpdate = { updatedAlarm ->
                                alarms = alarms.toMutableList().apply {
                                    set(index, updatedAlarm)
                                }
                            },
                            onSchedule = { delayMillis ->
                                onScheduleAlarm(delayMillis, index + 1)
                                alarms = alarms.toMutableList().apply {
                                    set(index, alarm.copy(
                                        isActive = true,
                                        scheduledTime = System.currentTimeMillis() + delayMillis
                                    ))
                                }
                            }
                        )
                        
                        // Action buttons row (outside card)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            // Clone button
                            OutlinedButton(
                                onClick = {
                                    alarms = alarms.toMutableList().apply {
                                        add(index + 1, alarm.clone())
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(6.dp)
                            ) {
                                Text("📋 Clone", fontSize = 11.sp)
                            }

                            // Move up
                            OutlinedButton(
                                onClick = {
                                    if (index > 0) {
                                        alarms = alarms.toMutableList().apply {
                                            val temp = this[index]
                                            this[index] = this[index - 1]
                                            this[index - 1] = temp
                                        }
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(6.dp)
                            ) {
                                Text("↑", fontSize = 11.sp)
                            }

                            // Move down
                            OutlinedButton(
                                onClick = {
                                    if (index < alarms.size - 1) {
                                        alarms = alarms.toMutableList().apply {
                                            val temp = this[index]
                                            this[index] = this[index + 1]
                                            this[index + 1] = temp
                                        }
                                    }
                                },
                                enabled = index < alarms.size - 1,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(6.dp)
                            ) {
                                Text("↓", fontSize = 11.sp)
                            }

                            // Remove
                            OutlinedButton(
                                onClick = {
                                    alarms = alarms.toMutableList().apply {
                                        removeAt(index)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.Red
                                ),
                                contentPadding = PaddingValues(6.dp)
                            ) {
                                Text("🗑️", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info text
        Text(
            text = "💡 Alarms work in background, silent mode, and when screen is off",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmCard(
    alarm: Alarm,
    index: Int,
    onUpdate: (Alarm) -> Unit,
    onSchedule: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(alarm.name) }
    var hours by remember { mutableStateOf(alarm.hours) }
    var minutes by remember { mutableStateOf(alarm.minutes) }
    var seconds by remember { mutableStateOf(alarm.seconds) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isActive) Color(0xFFFFF3E0) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header (always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = if (alarm.name.isNotBlank()) alarm.name else "Alarm ${index + 1}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (alarm.isActive) Color(0xFFE65100) else Color.Black
                        )
                    }
                    Text(
                        text = alarm.getFormattedTime(),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    if (alarm.isActive) {
                        val remaining = ((alarm.scheduledTime - System.currentTimeMillis()) / 1000).toInt()
                        if (remaining > 0) {
                            val h = remaining / 3600
                            val m = (remaining % 3600) / 60
                            val s = remaining % 60
                            Text(
                                text = "⏱️ ${String.format("%02d:%02d:%02d", h, m, s)} remaining",
                                fontSize = 11.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded content
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        onUpdate(alarm.copy(name = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name (optional)", fontSize = 10.sp) },
                    placeholder = { Text("e.g., First Medicine", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time picker - more compact
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactTimePickerColumn(
                        value = hours,
                        range = 0..23,
                        label = "H",
                        onValueChange = { 
                            hours = it
                            onUpdate(alarm.copy(hours = it, isActive = false, scheduledTime = 0L))
                        }
                    )
                    Text(":", fontSize = 18.sp, color = Color(0xFF6200EE), modifier = Modifier.padding(horizontal = 2.dp))
                    CompactTimePickerColumn(
                        value = minutes,
                        range = 0..59,
                        label = "M",
                        onValueChange = { 
                            minutes = it
                            onUpdate(alarm.copy(minutes = it, isActive = false, scheduledTime = 0L))
                        }
                    )
                    Text(":", fontSize = 18.sp, color = Color(0xFF6200EE), modifier = Modifier.padding(horizontal = 2.dp))
                    CompactTimePickerColumn(
                        value = seconds,
                        range = 0..59,
                        label = "S",
                        onValueChange = { 
                            seconds = it
                            onUpdate(alarm.copy(seconds = it, isActive = false, scheduledTime = 0L))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick presets - compact
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        Triple(0, 0, 5) to "5s",
                        Triple(0, 0, 30) to "30s",
                        Triple(0, 1, 0) to "1m",
                        Triple(0, 5, 0) to "5m"
                    ).forEach { (time, label) ->
                        OutlinedButton(
                            onClick = {
                                hours = time.first
                                minutes = time.second
                                seconds = time.third
                                onUpdate(alarm.copy(hours = time.first, minutes = time.second, seconds = time.third, isActive = false, scheduledTime = 0L))
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            Text(label, fontSize = 9.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Start button only
                Button(
                    onClick = {
                        val delayMillis = alarm.getTotalSeconds() * 1000L
                        onSchedule(delayMillis)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !alarm.isActive && alarm.getTotalSeconds() > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE)
                    ),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Text("Start Alarm", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// New compact time picker column
@Composable
fun CompactTimePickerColumn(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(50.dp)
    ) {
        IconButton(
            onClick = { 
                val newValue = if (value < range.last) value + 1 else range.first
                onValueChange(newValue)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Text("▲", fontSize = 10.sp, color = Color(0xFF6200EE))
        }

        Text(
            text = String.format("%02d", value),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EE)
        )

        IconButton(
            onClick = { 
                val newValue = if (value > range.first) value - 1 else range.last
                onValueChange(newValue)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Text("▼", fontSize = 10.sp, color = Color(0xFF6200EE))
        }

        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun TimePickerColumn(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
    ) {
        IconButton(
            onClick = { 
                val newValue = if (value < range.last) value + 1 else range.first
                onValueChange(newValue)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Text("▲", fontSize = 12.sp, color = Color(0xFF6200EE))
        }

        Text(
            text = String.format("%02d", value),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EE)
        )

        IconButton(
            onClick = { 
                val newValue = if (value > range.first) value - 1 else range.last
                onValueChange(newValue)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Text("▼", fontSize = 12.sp, color = Color(0xFF6200EE))
        }

        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}
