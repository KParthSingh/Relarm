package com.medicinereminder.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit,
    onPermissionsGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val notificationManager = remember { context.getSystemService(NotificationManager::class.java) }
    
    // Track permission states
    var batteryOptimizationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                true // Not applicable on older versions
            }
        )
    }
    
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not required on older versions
            }
        )
    }
    
    var fullscreenPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager?.canUseFullScreenIntent() ?: false
            } else {
                true // Not required on older versions
            }
        )
    }
    
    // Wake lock is a normal permission (granted at install time)
    // We track it here for UI purposes, but it's always granted
    var wakeLockPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WAKE_LOCK
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Function to refresh all permission states
    fun refreshPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            batteryOptimizationGranted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            fullscreenPermissionGranted = notificationManager?.canUseFullScreenIntent() ?: false
        }
        wakeLockPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WAKE_LOCK
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Refresh permissions when app resumes (returns from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Initial refresh
        refreshPermissions()
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.permissions_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Floating Done Button
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 3.dp
            ) {
                Button(
                    onClick = {
                        val repository = SettingsRepository(context)
                        repository.setFirstLaunchComplete(true)
                        onPermissionsGranted()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = context.getString(R.string.permissions_done_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = context.getString(R.string.permissions_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Permissions Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Column {
                    // Battery Optimization
                    PermissionItem(
                        icon = Icons.Default.Battery6Bar,
                        title = context.getString(R.string.permissions_battery_title),
                        description = context.getString(R.string.permissions_battery_desc),
                        isGranted = batteryOptimizationGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    // Open app-specific battery optimization settings
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback to app details page
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(fallbackIntent)
                                    } catch (e2: Exception) {
                                        // Last resort - general settings
                                        val generalIntent = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(generalIntent)
                                    }
                                }
                            }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    
                    // Autostart Permission (2nd - most important for alarm reliability)
                    val settingsRepository = remember { SettingsRepository(context) }
                    var autostartConfirmed by remember { mutableStateOf(settingsRepository.isAutostartConfirmed()) }
                    
                    AutostartPermissionItem(
                        isConfirmed = autostartConfirmed,
                        onOpenSettings = {
                            AutostartHelper.openAutostartSettings(context)
                        },
                        onConfirmChanged = { confirmed ->
                            autostartConfirmed = confirmed
                            settingsRepository.setAutostartConfirmed(confirmed)
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    
                    // Fullscreen Notification Permission
                    PermissionItem(
                        icon = Icons.Default.Fullscreen,
                        title = context.getString(R.string.permissions_fullscreen_title),
                        description = context.getString(R.string.permissions_fullscreen_desc),
                        isGranted = fullscreenPermissionGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                try {
                                    // Open app-specific settings for fullscreen intent
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback to app details
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(fallbackIntent)
                                    } catch (e2: Exception) {
                                        // Last resort
                                        val generalIntent = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(generalIntent)
                                    }
                                }
                            }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    
                    // Wake Lock Permission (Turn On Screen)
                    PermissionItem(
                        icon = Icons.Default.ScreenLockPortrait,
                        title = context.getString(R.string.permissions_wakelock_title),
                        description = context.getString(R.string.permissions_wakelock_desc),
                        isGranted = wakeLockPermissionGranted,
                        onClick = {
                            // Open app-specific settings for wake lock
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to general settings
                                val generalIntent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(generalIntent)
                            }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    
                    // Notification Permission
                    PermissionItem(
                        icon = Icons.Default.Notifications,
                        title = context.getString(R.string.permissions_notification_title),
                        description = context.getString(R.string.permissions_notification_desc),
                        isGranted = notificationPermissionGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                try {
                                    // Try to open app-specific notification settings
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback to app details
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(fallbackIntent)
                                    } catch (e2: Exception) {
                                        // Last resort
                                        val generalIntent = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(generalIntent)
                                    }
                                }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Outlined.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGranted) 
                            context.getString(R.string.permissions_status_granted)
                        else 
                            context.getString(R.string.permissions_status_not_granted),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                
                if (!isGranted) {
                    TextButton(onClick = onClick) {
                        Text(
                            text = context.getString(R.string.permissions_action_grant),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutostartPermissionItem(
    isConfirmed: Boolean,
    onOpenSettings: () -> Unit,
    onConfirmChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icon - using PlayArrow as RocketLaunch may not be available
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Autostart / Background Run",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Allow app to start automatically and run in background. Required for alarms to work when app is closed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Open Settings button
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Autostart Settings")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Manual confirmation checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfirmChanged(!isConfirmed) }
            ) {
                Checkbox(
                    checked = isConfirmed,
                    onCheckedChange = onConfirmChanged
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I have enabled autostart for this app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status indicator (like other permission items)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConfirmed) 
                        context.getString(R.string.permissions_status_granted)
                    else 
                        context.getString(R.string.permissions_status_not_granted),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
