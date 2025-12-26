package com.medicinereminder.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.isActive

// Navigation screens
enum class Screen { Main, Settings, Permissions }

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
        
        // Initialize debug logger
        DebugLogger.init(this)
        DebugLogger.info("MainActivity", "========== APP STARTED ==========")
        
        alarmScheduler = AlarmScheduler(this)
        
        NotificationHelper.createNotificationChannel(this)
        checkNotificationPermission()
        checkExactAlarmPermission()

        setContent {
            val settingsRepository = remember { SettingsRepository(this) }
            var themeMode by remember { mutableStateOf(settingsRepository.getThemeMode()) }
            
            // Navigation state
            var currentScreen by remember { 
                mutableStateOf(
                    if (!settingsRepository.isFirstLaunchComplete()) Screen.Permissions 
                    else Screen.Main
                )
            }
            
            MedicineReminderTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Handle back button
                    if (currentScreen != Screen.Main) {
                        androidx.activity.compose.BackHandler {
                            currentScreen = Screen.Main
                        }
                    }
                    
                    // Animated transition between screens
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            // Define the slide + fade transition
                            val slideOffset = 1000 // pixels to slide from
                            val duration = 400 // duration in milliseconds
                            
                            when {
                                // Going to Settings or Permissions: slide in from right
                                targetState == Screen.Settings || targetState == Screen.Permissions -> {
                                    (slideInHorizontally(
                                        initialOffsetX = { slideOffset },
                                        animationSpec = tween(duration)
                                    ) + fadeIn(
                                        animationSpec = tween(duration)
                                    )).togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { -slideOffset },
                                            animationSpec = tween(duration)
                                        ) + fadeOut(
                                            animationSpec = tween(duration)
                                        )
                                    )
                                }
                                // Going back to Main: slide in from left
                                else -> {
                                    (slideInHorizontally(
                                        initialOffsetX = { -slideOffset },
                                        animationSpec = tween(duration)
                                    ) + fadeIn(
                                        animationSpec = tween(duration)
                                    )).togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { slideOffset },
                                            animationSpec = tween(duration)
                                        ) + fadeOut(
                                            animationSpec = tween(duration)
                                        )
                                    )
                                }
                            }
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            Screen.Settings -> {
                                SettingsScreen(
                                    onNavigateBack = { currentScreen = Screen.Main },
                                    onThemeChanged = { themeMode = settingsRepository.getThemeMode() },
                                    onOpenPermissions = { currentScreen = Screen.Permissions }
                                )
                            }
                            Screen.Permissions -> {
                                PermissionsScreen(
                                    onNavigateBack = { currentScreen = Screen.Main }
                                )
                            }
                            Screen.Main -> {
                                MainScreen(
                                    onScheduleAlarm = { delayMillis, requestCode, name, current, total, isChain ->
                                        scheduleAlarm(delayMillis, requestCode, name, current, total, isChain)
                                    },
                                    onOpenSettings = { currentScreen = Screen.Settings },
                                    onOpenPermissions = { currentScreen = Screen.Permissions }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        DebugLogger.info("MainActivity", "onStop() - App going to background")
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
        DebugLogger.info("MainActivity", "onStart() - App coming to foreground")
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

    private fun scheduleAlarm(delayMillis: Long, requestCode: Int, name: String, currentIndex: Int, totalAlarms: Int, isChain: Boolean) {
        alarmScheduler.scheduleAlarm(delayMillis, requestCode)
        
        val chainIntent = Intent(this, ChainService::class.java).apply {
            action = ChainService.ACTION_START_CHAIN_ALARM
            putExtra(ChainService.EXTRA_END_TIME, System.currentTimeMillis() + delayMillis)
            putExtra(ChainService.EXTRA_CURRENT_INDEX, currentIndex)
            putExtra(ChainService.EXTRA_TOTAL_ALARMS, totalAlarms)
            putExtra(ChainService.EXTRA_ALARM_NAME, name)
            putExtra(ChainService.EXTRA_IS_CHAIN, isChain)
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onScheduleAlarm: (Long, Int, String, Int, Int, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository(context) }
    // OPTIMIZED: Use SnapshotStateList for efficient O(1) mutations
    // We initialise it with an empty list first, then populate it
    val alarms = remember { mutableStateListOf<Alarm>() }
    
    // Check permission states
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val hasAllPermissions by remember {
        derivedStateOf {
            val batteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else {
                true
            }
            
            val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            val fullscreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.USE_FULL_SCREEN_INTENT
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            batteryOptimized && notifications && fullscreen
        }
    }
    
    // Initial load
    LaunchedEffect(Unit) {
        val loaded = repository.loadAlarms()
        alarms.clear()
        alarms.addAll(loaded)
    }

    val chainManager = remember { ChainManager(context) }
    // OPTIMIZATION: Use Flow instead of Polling
    val chainState by chainManager.getChainStateFlow().collectAsState(initial = chainManager.getChainState())
    
    val isChainActive = chainState.isChainActive
    val isPaused = chainState.isPaused
    val currentChainIndex = chainState.currentIndex
    val isAlarmRinging = chainState.isAlarmRinging
    val isChainSequence = chainState.isChainSequence

    // Observe Alarms Flow (Hybrid approach to support Drag & Drop)
    LaunchedEffect(Unit) {
        repository.getAlarmsFlow().collect { newAlarms ->
            // Only update if content changed remotely AND we are not dragging?
            // For now, simple diff check. 
            // Only effective way to check efficiently is if sizes or IDs differ
            if (newAlarms.size != alarms.size || newAlarms.map { it.id } != alarms.map { it.id }) {
                 // Make sure we don't clobber local state if it's identical
                 // (This is a simplified check, proper would be deep equals but expensive)
                 alarms.clear()
                 alarms.addAll(newAlarms)
            }
        }
    }
    
    // Helper to save state explicitly
    fun saveAlarms() {
        val currentList = alarms.toList() // Snapshot
        repository.saveAlarms(currentList)
        android.util.Log.d("DragOptimizer", "Saved ${currentList.size} alarms to persistence")
    }

    // REMOVED: LaunchedEffect(alarms) auto-saver to prevent lag during drag


    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(stringResource(R.string.title_main))
                },
                actions = {
                    // Warning pill button if permissions not granted
                    if (!hasAllPermissions) {
                        FilledTonalButton(
                            onClick = onOpenPermissions,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFD32F2F), // Fixed red color
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = "Permissions",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("!", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isChainActive) {
                val settingsRepository = remember { SettingsRepository(context) }
                ExtendedFloatingActionButton(
                    onClick = { 
                        val defaultSeconds = settingsRepository.getDefaultAlarmTime()
                        val h = defaultSeconds / 3600
                        val m = (defaultSeconds % 3600) / 60
                        val s = defaultSeconds % 60
                        alarms.add(Alarm(hours = h, minutes = m, seconds = s))
                        saveAlarms() // Save on add
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Outlined.Add, "Add Alarm") },
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
                        isChainSequence = isChainSequence,
                        alarms = alarms,
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
                            alarms.forEachIndexed { i, a ->
                                if (a.isActive || a.scheduledTime != 0L) {
                                    alarms[i] = a.copy(isActive = false, scheduledTime = 0L)
                                }
                            }
                            saveAlarms() // Save on stop
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
                Surface(
                    shadowElevation = 8.dp,
                    tonalElevation = 3.dp
                ) {
                    Button(
                        onClick = {
                            chainManager.startChain()
                            val firstAlarm = alarms[0]
                            val delay = firstAlarm.getTotalSeconds() * 1000L
                            onScheduleAlarm(delay, 1, firstAlarm.name, 0, alarms.size, true)
                            
                            // efficient update
                            alarms[0] = firstAlarm.copy(isActive = true, scheduledTime = System.currentTimeMillis() + delay)
                            saveAlarms() // Save on start
                            
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
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.start_chain_btn), 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.start_chain_subtitle, alarms.size),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // Drag and Scroll State
        val listState = rememberLazyListState()
        var draggedAlarmId by remember { mutableStateOf<String?>(null) }
        var draggingItemInitialY by remember { mutableFloatStateOf(0f) }
        var totalDragOffsetY by remember { mutableFloatStateOf(0f) }
        var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
        var lastSwapTime by remember { mutableLongStateOf(0L) }
        var lastSwapDirection by remember { mutableIntStateOf(0) } // 1=down, -1=up, 0=none
        
        // Map to store original indices when drag starts
        // We moved this up to be accessible for Overlay
        val dragStartIndices = remember { mutableStateMapOf<String, Int>() }

        val itemHeights = remember { mutableStateMapOf<String, Int>() }
        val itemCoordinates = remember { mutableStateMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>() }
        var listCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        
        val density = LocalDensity.current
        val spacing = 8.dp
        
        // Auto-scroll loop
        LaunchedEffect(autoScrollSpeed) {
            if (autoScrollSpeed != 0f) {
                while (isActive) {
                    val scrollAmount = autoScrollSpeed
                    listState.scrollBy(scrollAmount)
                    delay(16)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = paddingValues,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .onGloballyPositioned { listCoordinates = it }
                    .pointerInput(isChainActive) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                if (isChainActive) return@detectDragGesturesAfterLongPress
                                val listCoords = listCoordinates ?: return@detectDragGesturesAfterLongPress
                                // Convert tap to root to find the hit item
                                val rootOffset = listCoords.localToRoot(offset)
                                
                                val hitId = itemCoordinates.entries.firstOrNull { (id, coords) ->
                                    if (!coords.isAttached) return@firstOrNull false
                                    val pos = coords.positionInRoot()
                                    rootOffset.y >= pos.y && rootOffset.y <= pos.y + coords.size.height
                                }?.key
                                
                                if (hitId != null) {
                                    draggedAlarmId = hitId
                                    // Capture current indices for stable numbering
                                    alarms.forEachIndexed { i, a -> dragStartIndices[a.id] = i }
                                    
                                    val itemCoords = itemCoordinates[hitId]!!
                                    draggingItemInitialY = itemCoords.positionInRoot().y - listCoords.positionInRoot().y 
                                    totalDragOffsetY = 0f
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggedAlarmId != null) {
                                    totalDragOffsetY += dragAmount.y
                                    
                                    // Calculate Swap Logic
                                    val overlayTop = draggingItemInitialY + totalDragOffsetY
                                    val listCoords = listCoordinates
                                    
                                    if (listCoords != null) {
                                        // Current Slot Global Y calculation
                                        val currentSlotCoords = itemCoordinates[draggedAlarmId!!]
                                        if (currentSlotCoords != null && currentSlotCoords.isAttached) {
                                            val currentSlotGlobalY = currentSlotCoords.positionInRoot().y
                                            // Overlay Global Y
                                            val overlayGlobalY = listCoords.positionInRoot().y + overlayTop
                                            
                                            val diff = overlayGlobalY - currentSlotGlobalY
                                            val spacingPx = with(density) { spacing.toPx() }
                                            val currentTime = System.currentTimeMillis()
                                            
                                            // STABILIZED: Increased debounce to prevent layout-triggered oscillation
                                            // Layout needs time to settle after swap before next check
                                            if (currentTime - lastSwapTime > 150) { 
                                                val currentIndex = alarms.indexOfFirst { it.id == draggedAlarmId }
                                                if (currentIndex != -1) {
                                                    // Move Down
                                                    if (diff > 0 && currentIndex < alarms.size - 1) {
                                                        val nextAlarmId = alarms[currentIndex + 1].id
                                                        val nextHeight = itemHeights[nextAlarmId]?.toFloat() ?: 0f
                                                        // Include spacing in threshold to prevent oscillation
                                                        val fullHeight = nextHeight + spacingPx
                                                        val threshold = fullHeight * 0.6f // Slightly higher than 50% for stability
                                                        
                                                        if (nextHeight > 0 && diff > threshold) {
                                                            // Prevent ping-pong: only swap if not recently swapping in opposite direction
                                                            if (lastSwapDirection != -1 || currentTime - lastSwapTime > 500) {
                                                                alarms.swap(currentIndex, currentIndex + 1)
                                                                lastSwapTime = currentTime
                                                                lastSwapDirection = 1
                                                            }
                                                        }
                                                    }
                                                    // Move Up
                                                    else if (diff < 0 && currentIndex > 0) {
                                                        val prevAlarmId = alarms[currentIndex - 1].id
                                                        val prevHeight = itemHeights[prevAlarmId]?.toFloat() ?: 0f
                                                        val fullHeight = prevHeight + spacingPx
                                                        val threshold = fullHeight * 0.6f
                                                        
                                                        if (prevHeight > 0 && diff < -threshold) {
                                                            if (lastSwapDirection != 1 || currentTime - lastSwapTime > 500) {
                                                                alarms.swap(currentIndex, currentIndex - 1)
                                                                lastSwapTime = currentTime
                                                                lastSwapDirection = -1
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Auto Scroll Logic
                                        val overlayGlobalY = listCoords.positionInRoot().y + overlayTop
                                        val rootHeight = with(density) { context.resources.displayMetrics.heightPixels.toFloat() }
                                        val threshold = rootHeight * 0.25f
                                        
                                        if (overlayGlobalY < threshold) {
                                             autoScrollSpeed = -15f
                                        } else if (overlayGlobalY > rootHeight - threshold) {
                                             autoScrollSpeed = 15f
                                        } else {
                                             autoScrollSpeed = 0f
                                        }
                                    }
                                }
                            },
                                    onDragEnd = {
                                        draggedAlarmId = null
                                        totalDragOffsetY = 0f
                                        autoScrollSpeed = 0f
                                        lastSwapDirection = 0
                                        dragStartIndices.clear()
                                        
                                        // SAVE STATE ON DROP - ONLY ONCE
                                        saveAlarms()
                                    },
                                    onDragCancel = {
                                        draggedAlarmId = null
                                        totalDragOffsetY = 0f
                                        autoScrollSpeed = 0f
                                        lastSwapDirection = 0
                                        dragStartIndices.clear()
                                    }
                        )
                }
            ) {
                if (alarms.isEmpty()) {
                    item { EmptyState() }
                } else {
                    itemsIndexed(alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                        val isDragging = alarm.id == draggedAlarmId
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .alpha(if (isDragging) 0f else 1f)
                                .animateItemPlacement()
                                .onGloballyPositioned { coordinates ->
                                    itemHeights[alarm.id] = coordinates.size.height
                                    itemCoordinates[alarm.id] = coordinates
                                }
                        ) {
                            AlarmItem(
                                alarm = alarm,
                                index = (if (draggedAlarmId != null) dragStartIndices[alarm.id] else null) ?: index,
                                totalAlarms = alarms.size,
                                chainState = chainState,
                                onUpdate = { updated ->
                                    alarms[index] = updated
                                    saveAlarms()
                                },
                                onSchedule = { delay ->
                                    onScheduleAlarm(delay, index + 1, alarm.name, index, 1, false) 
                                    alarms[index] = alarm.copy(
                                        isActive = true,
                                        scheduledTime = System.currentTimeMillis() + delay
                                    )
                                    saveAlarms()
                                },
                                isEditable = !isChainActive,
                                onDelete = {
                                    alarms.removeAt(index)
                                    saveAlarms()
                                },
                                onClone = {
                                    alarms.add(index + 1, alarm.clone())
                                    saveAlarms()
                                }
                            )
                        }
                    }
                    item { 
                        Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                    }
                }
            }
            
            // OVERLAY for dragged item
            if (draggedAlarmId != null) {
                val alarm = alarms.find { it.id == draggedAlarmId }
                if (alarm != null) {
                    val currentIndex = alarms.indexOf(alarm)
                    val displayIndex = dragStartIndices[alarm.id] ?: currentIndex
                    AlarmItem(
                        alarm = alarm,
                        index = displayIndex,
                        totalAlarms = alarms.size,
                        chainState = chainState,
                        onUpdate = {},
                        onSchedule = {},
                        onDelete = {},
                        onClone = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            // So LazyColumn Render Node starts at Top Left of content area.
                            // So offset is correct.
                            .offset { IntOffset(0, (draggingItemInitialY + totalDragOffsetY).toInt()) }
                            .zIndex(10f)
                            .graphicsLayer {
                                scaleX = 1.05f
                                scaleY = 1.05f
                                shadowElevation = 8.dp.toPx()
                            }
                    )
                }
            }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "⏰",
                fontSize = 72.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.empty_state_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
fun StickyChainBar(
    currentIndex: Int,
    totalAlarms: Int,
    isAlarmRinging: Boolean,
    isChainSequence: Boolean = true,
    alarms: List<Alarm>,
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
    
    // UNIFIED TIME CALCULATION - matches notification's chronometer exactly
    // This is the SINGLE SOURCE OF TRUTH for UI timer display
    var remainingTimeMs by remember { mutableLongStateOf(0L) }
    
    // Log when isAlarmRinging changes
    LaunchedEffect(isAlarmRinging) {
        DebugLogger.logState("StickyChainBar-UI", mapOf(
            "isAlarmRinging" to isAlarmRinging,
            "currentIndex" to currentIndex,
            "isPaused" to isPaused,
            "showingDismissButton" to isAlarmRinging
        ))
    }
    
    // CRITICAL: React to isPaused and currentIndex changes for immediate sync
    // This ensures when pause/resume happens, we recalculate immediately
    LaunchedEffect(isPaused, currentIndex) {
        // Log when the effect is triggered by state changes
        Log.d("TimerSync", "[UI] LaunchedEffect triggered - isPaused=$isPaused, currentIndex=$currentIndex")
        
        while (true) {
            // Get current state from ChainManager (same source as notification uses)
            val paused = chainManager.isChainPaused()
            val endTime = chainManager.getEndTime()
            
            val oldRemainingMs = remainingTimeMs
            
            // Calculate remaining time using EXACT same logic as notification
            remainingTimeMs = if (paused) {
                // When paused, use saved paused time (notification shows static value)
                chainManager.getPausedRemainingTime()
            } else {
                // When running, calculate from endTime (notification chronometer uses this)
                if (endTime > 0) {
                    (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                } else {
                    0L
                }
            }
            
            // Log significant time changes (more than 2 seconds difference or state changes)
            if (kotlin.math.abs(remainingTimeMs - oldRemainingMs) > 2000 || paused != isPaused) {
                Log.d("TimerSync", "[UI] Time updated: $oldRemainingMs -> $remainingTimeMs (paused=$paused, endTime=$endTime)")
            }
            
            delay(1000) // Poll at 1000ms (1s) for text countdown display
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
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
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
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
                        else if (isPaused) if (isChainSequence) "SEQUENCE PAUSED" else "ALARM PAUSED"
                        else if (isChainSequence) stringResource(R.string.chain_active_title)
                        else "ALARM RUNNING",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlarmRinging) MaterialTheme.colorScheme.error
                               else if (isPaused) MaterialTheme.colorScheme.error 
                               else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (isChainSequence) stringResource(R.string.chain_info_format, currentIndex + 1, totalAlarms)
                        else alarms.getOrNull(currentIndex)?.name ?: "Alarm",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (!isAlarmRinging) {
                    Row {
                        FilledTonalButton(
                            onClick = { if (isPaused) onResume() else onPause() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isPaused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isPaused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(if (isPaused) "RESUME" else "PAUSE")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("STOP")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            
            val remainingSeconds = (remainingTimeMs / 1000).toInt().coerceAtLeast(0)
            
            // Navigation buttons (only for sequence)
            if (isChainSequence) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous alarm",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PREV")
                }
                
                OutlinedButton(
                    onClick = onNext,
                    enabled = currentIndex < totalAlarms - 1,
                    modifier = Modifier.weight(1f)
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
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (isPaused) "Resumes in: ${formatTime(remainingSeconds)}" 
                    else if (isChainSequence) "Next alarm: ${formatTime(remainingSeconds)}"
                    else "Time remaining: ${formatTime(remainingSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                // Calculate when the last alarm will trigger
                val totalRemainingTimeMs = if (!isChainSequence) {
                    remainingTimeMs
                } else if (currentIndex < alarms.size) {
                    // Sum up remaining time for current alarm plus all future alarms
                    remainingTimeMs + alarms.subList(currentIndex + 1, alarms.size).sumOf { it.getTotalSeconds() * 1000L }
                } else {
                    0L
                }
                val lastAlarmTime = System.currentTimeMillis() + totalRemainingTimeMs
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val formattedTime = timeFormat.format(Date(lastAlarmTime))
                
                Text(
                    "Ends at: $formattedTime",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlarmItem(
    alarm: Alarm,
    index: Int,
    totalAlarms: Int,
    chainState: ChainState,
    onUpdate: (Alarm) -> Unit,
    onSchedule: (Long) -> Unit,
    onDelete: () -> Unit,
    isEditable: Boolean = true,
    modifier: Modifier = Modifier,
    onClone: () -> Unit
) {
    val context = LocalContext.current
    var showNameDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var showJumpConfirmation by remember { mutableStateOf(false) }
    
    // Determine effective state from ChainState (Single Source of Truth)
    val isChainActive = chainState.isChainActive
    val isRunningInChain = isChainActive && chainState.currentIndex == index
    
    val effectiveState: AlarmState
    val effectiveScheduledTime: Long
    val effectivePausedRemainingMs: Long
    
    if (isRunningInChain) {
        if (chainState.isAlarmRinging) {
             effectiveState = AlarmState.EXPIRED
             effectiveScheduledTime = 0L
             effectivePausedRemainingMs = 0L
        } else if (chainState.isPaused) {
             effectiveState = AlarmState.PAUSED
             effectiveScheduledTime = 0L
             effectivePausedRemainingMs = chainState.pausedRemainingTime
        } else {
             effectiveState = AlarmState.RUNNING
             effectiveScheduledTime = chainState.endTime
             effectivePausedRemainingMs = 0L
        }
    } else {
        effectiveState = AlarmState.RESET
        effectiveScheduledTime = 0L
        effectivePausedRemainingMs = 0L
    }
    
    // Track remaining time for text display
    var remainingTime by remember { mutableIntStateOf(alarm.getTotalSeconds()) }
    
    // Update progress and remaining time in real-time
    LaunchedEffect(effectiveState, effectiveScheduledTime, effectivePausedRemainingMs, alarm.hours, alarm.minutes, alarm.seconds) {
        if (effectiveState == AlarmState.RUNNING) {
            while (isActive) {
                // Update text every second
                val remaining = ((effectiveScheduledTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                remainingTime = remaining
                delay(1000)
            }
        } else if (effectiveState == AlarmState.PAUSED) {
            remainingTime = (effectivePausedRemainingMs / 1000).toInt().coerceAtLeast(0)
        } else if (effectiveState == AlarmState.EXPIRED) {
            remainingTime = 0
        } else {
            // Reset/Idle
            remainingTime = alarm.getTotalSeconds()
        }
    }
    
    
    // Sound picker launcher
    val soundPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onUpdate(alarm.copy(soundUri = uri?.toString()))
        }
    }
    
    // Confirmation dialog for jumping to this alarm during active sequence
    if (showJumpConfirmation) {
        AlertDialog(
            onDismissRequest = { showJumpConfirmation = false },
            title = { Text("Switch Alarm?") },
            text = { 
                Text("A sequence is currently running. Do you want to switch to this alarm? The sequence will continue from this alarm.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showJumpConfirmation = false
                        // Send jump command to ChainService
                        val jumpIntent = Intent(context, ChainService::class.java).apply {
                            action = ChainService.ACTION_JUMP_TO_ALARM
                            putExtra(ChainService.EXTRA_TARGET_INDEX, index)
                        }
                        context.startService(jumpIntent)
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpConfirmation = false }) {
                    Text("No")
                }
            }
        )
    }
    
    val isExpanded = effectiveState != AlarmState.RESET
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(), // Smooth height animation
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // ===== HEADER SECTION =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Label with clickable edit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = isEditable) { showNameDialog = true }
                        .padding(6.dp)
                ) {
                    if (isEditable) {
                        // show icon always if editable? Or only if blank?
                        // User said: "icon ... should only be shown if you haven't put any name"
                        // But also "when there's low space the name... should roll"
                        
                        if (alarm.name.isBlank()) {
                            Icon(
                                imageVector = Icons.Outlined.TextFields,
                                contentDescription = "Edit label",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    } else {
                        // If not editable, same logic? 
                        // User request didn't specify mode, just "the icon... should only be shown..."
                        if (alarm.name.isBlank()) {
                            Icon(
                                imageVector = Icons.Outlined.TextFields,
                                contentDescription = "Edit label",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                    
                    Text(
                        text = if (alarm.name.isNotBlank()) alarm.name else "Alarm ${index + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (alarm.name.isNotBlank()) 
                            MaterialTheme.colorScheme.onSurface 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sound selector button - expands when custom sound selected
                    val hasCustomSound = alarm.soundUri != null
                    val soundName = try {
                        if (hasCustomSound) {
                            val ringtone = RingtoneManager.getRingtone(context, Uri.parse(alarm.soundUri))
                            ringtone.getTitle(context)
                        } else {
                            ""
                        }
                    } catch (e: Exception) {
                        "Custom"
                    }
                    
                    Surface(
                        onClick = {
                            if (isEditable) {
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
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier.animateContentSize()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.MusicNote,
                                contentDescription = "Select Sound",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (hasCustomSound) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 100.dp)
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = soundName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    
                    // Clone button
                    IconButton(onClick = onClone, enabled = isEditable) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Clone",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Delete button
                    IconButton(onClick = onDelete, enabled = isEditable) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (!isExpanded) {
                // ===== COMPACT LAYOUT (RESET STATE) =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Duration text with edit icon on left
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = isEditable) { showTimePicker = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit duration",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = alarm.getFormattedTime(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Start button on right
                    Button(
                        onClick = {
                            // Check if a chain is currently active
                            val chainManager = ChainManager(context)
                            val isChainActive = chainManager.isChainActive()
                            val currentChainIndex = chainManager.getCurrentIndex()
                            
                            if (isChainActive && currentChainIndex != index) {
                                // Show confirmation dialog to switch alarms
                                showJumpConfirmation = true
                            } else {
                                // Normal start for single alarm or if chain not active
                                val delay = alarm.getTotalSeconds() * 1000L
                                val now = System.currentTimeMillis()
                                onSchedule(delay)
                                onUpdate(alarm.copy(
                                    isActive = true,
                                    scheduledTime = now + delay,
                                    state = AlarmState.RUNNING,
                                    totalDuration = delay,
                                    startTime = now
                                ))
                            }
                        },
                        enabled = alarm.getTotalSeconds() > 0,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Start",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                // ===== EXPANDED LAYOUT (ACTIVE STATE) =====
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // LEFT: Progress Circle (Dominates space)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 5.dp, end = 16.dp, bottom = 12.dp)
                            .heightIn(max = 240.dp)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Progress circle
                        com.medicinereminder.app.ui.TimerCircleView(
                            scheduledTime = effectiveScheduledTime,
                            totalDuration = alarm.getTotalSeconds() * 1000L,
                            isExpired = effectiveState == AlarmState.EXPIRED,
                            isPaused = effectiveState == AlarmState.PAUSED,
                            pausedRemainingMs = effectivePausedRemainingMs,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                        
                        // Time display in center
                        Text(
                            text = if (effectiveState == AlarmState.RUNNING || effectiveState == AlarmState.PAUSED) {
                                formatTime(remainingTime)
                            } else {
                                alarm.getFormattedTime()
                            },
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable(enabled = isEditable) { showTimePicker = true }
                        )
                        
                        // Reset button (inside circle, bottom) - always visible
                        IconButton(
                            onClick = {
                                if (effectiveState == AlarmState.RUNNING) {
                                    // If running: reset and restart timer
                                    val delay = alarm.getTotalSeconds() * 1000L
                                    val now = System.currentTimeMillis()
                                    
                                    // Cancel current alarm
                                    val repository = AlarmRepository(context)
                                    val allAlarms = repository.loadAlarms()
                                    val alarmIndex = allAlarms.indexOf(alarm)
                                    val requestCode = alarmIndex + 1
                                    val alarmScheduler = AlarmScheduler(context)
                                    alarmScheduler.cancelAlarm(requestCode)
                                    
                                    // Reschedule with original duration
                                    onSchedule(delay)
                                    onUpdate(alarm.copy(
                                        isActive = true,
                                        scheduledTime = now + delay,
                                        state = AlarmState.RUNNING,
                                        totalDuration = delay,
                                        startTime = now,
                                        pausedRemainingMs = 0L
                                    ))
                                } else if (effectiveState == AlarmState.PAUSED) {
                                    // If paused: reset timer to original duration but stay paused
                                    val originalDuration = alarm.getTotalSeconds() * 1000L
                                    onUpdate(alarm.copy(
                                        pausedRemainingMs = originalDuration,
                                        totalDuration = originalDuration
                                    ))
                                } else {
                                    // If in any other state: stop and reset completely
                                    val stopIntent = Intent(context, ChainService::class.java).apply {
                                        action = ChainService.ACTION_STOP_CHAIN
                                    }
                                    context.startService(stopIntent)
                                    
                                    onUpdate(alarm.copy(
                                        isActive = false,
                                        scheduledTime = 0L,
                                        state = AlarmState.RESET,
                                        totalDuration = 0L,
                                        startTime = 0L
                                    ))
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // RIGHT: Controls Stack
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight().padding(bottom = 12.dp)
                    ) {
                        // +1:00 Button (Only when running)
                        if (effectiveState == AlarmState.RUNNING) {
                            TextButton(
                                onClick = {
                                    // Add 1 minute code
                                    val repository = AlarmRepository(context)
                                    val allAlarms = repository.loadAlarms()
                                    val alarmIndex = allAlarms.indexOf(alarm)
                                    val requestCode = alarmIndex + 1
                                    
                                    val alarmScheduler = AlarmScheduler(context)
                                    alarmScheduler.cancelAlarm(requestCode)
                                    
                                    val newScheduledTime = alarm.scheduledTime + 60000
                                    val newTotalDuration = alarm.totalDuration + 60000
                                    
                                    val delay = newScheduledTime - System.currentTimeMillis()
                                    onSchedule(delay)
                                    
                                    val newTotalSeconds = alarm.getTotalSeconds() + 60
                                    val h = newTotalSeconds / 3600
                                    val m = (newTotalSeconds % 3600) / 60
                                    val s = newTotalSeconds % 60
                                    onUpdate(alarm.copy(
                                        hours = h,
                                        minutes = m,
                                        seconds = s,
                                        scheduledTime = newScheduledTime,
                                        totalDuration = newTotalDuration
                                    ))
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text("+ 1:00", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        // Stop Button (square icon, above pause)
                        Button(
                            onClick = {
                                // Stop the timer completely
                                val stopIntent = Intent(context, ChainService::class.java).apply {
                                    action = ChainService.ACTION_STOP_CHAIN
                                }
                                context.startService(stopIntent)
                                
                                onUpdate(alarm.copy(
                                    isActive = false,
                                    scheduledTime = 0L,
                                    state = AlarmState.RESET,
                                    totalDuration = 0L,
                                    startTime = 0L
                                ))
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(24.dp),
                            modifier = Modifier.size(80.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // Play/Pause Button (Icon only)
                        Button(
                            onClick = {
                                when (alarm.state) {
                                    AlarmState.RESET -> {
                                        // Starting from reset state
                                        val delay = alarm.getTotalSeconds() * 1000L
                                        val now = System.currentTimeMillis()
                                        onSchedule(delay)
                                        onUpdate(alarm.copy(
                                            isActive = true,
                                            scheduledTime = now + delay,
                                            state = AlarmState.RUNNING,
                                            totalDuration = delay,
                                            startTime = now,
                                            pausedRemainingMs = 0L
                                        ))
                                    }
                                    AlarmState.PAUSED -> {
                                        // Resuming from paused state - use remaining time
                                        val delay = alarm.pausedRemainingMs
                                        val now = System.currentTimeMillis()
                                        
                                        Log.d("AlarmItem", "RESUME clicked - remaining: ${delay}ms")
                                        
                                        // CRITICAL: Tell ChainService to resume (syncs notification)
                                        val resumeIntent = Intent(context, ChainService::class.java).apply {
                                            action = ChainService.ACTION_RESUME_CHAIN
                                        }
                                        context.startService(resumeIntent)
                                        
                                        // Update local alarm state
                                        onUpdate(alarm.copy(
                                            isActive = true,
                                            scheduledTime = now + delay,
                                            state = AlarmState.RUNNING,
                                            startTime = now,
                                            totalDuration = alarm.totalDuration, // Keep original total duration
                                            pausedRemainingMs = 0L // Clear paused time
                                        ))
                                    }
                                    AlarmState.RUNNING -> {
                                        // Pausing - calculate and store remaining time
                                        val remaining = (alarm.scheduledTime - System.currentTimeMillis()).coerceAtLeast(0)
                                        
                                        Log.d("AlarmItem", "PAUSE clicked - remaining: ${remaining}ms")
                                        
                                        // CRITICAL: Tell ChainService to pause (syncs notification)
                                        val pauseIntent = Intent(context, ChainService::class.java).apply {
                                            action = ChainService.ACTION_PAUSE_CHAIN
                                        }
                                        context.startService(pauseIntent)
                                        
                                        // Update local alarm state
                                        onUpdate(alarm.copy(
                                            isActive = false,
                                            state = AlarmState.PAUSED,
                                            pausedRemainingMs = remaining
                                        ))
                                    }
                                    AlarmState.EXPIRED -> {
                                        // Reset when expired
                                        onUpdate(alarm.copy(
                                            isActive = false,
                                            scheduledTime = 0L,
                                            state = AlarmState.RESET,
                                            totalDuration = 0L,
                                            startTime = 0L
                                        ))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(24.dp),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                imageVector = when (alarm.state) {
                                    AlarmState.RESET, AlarmState.PAUSED -> Icons.Outlined.PlayArrow
                                    AlarmState.RUNNING -> Icons.Outlined.Pause
                                    AlarmState.EXPIRED -> Icons.Outlined.Refresh
                                },
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
            

        }
    }
    
    // Name edit dialog
    if (showNameDialog) {
        var tempName by remember { mutableStateOf(alarm.name) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Edit Alarm Name") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(alarm.copy(name = tempName))
                        showNameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Duration picker (reuse existing bottom sheet)
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
                        onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false, state = AlarmState.RESET))
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
                                onUpdate(alarm.copy(hours = h, minutes = m, seconds = s, isActive = false, state = AlarmState.RESET))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                        ) {
                            Text("+${if (sec < 60) "${sec}s" else "${sec/60}m"}", fontSize = 12.sp)
                        }
                    }
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
            Icon(Icons.Outlined.Edit, null)
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
            Icon(Icons.Outlined.MoreVert, null)
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
            Icon(Icons.Outlined.PlayArrow, null)
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
                Icon(Icons.Outlined.Delete, null)
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


// Helper extension for swapping items in SnapshotStateList (which implements MutableList)
fun <T> MutableList<T>.swap(index1: Int, index2: Int) {
    if (index1 in indices && index2 in indices && index1 != index2) {
        val tmp = this[index1]
        this[index1] = this[index2]
        this[index2] = tmp
    }
}
