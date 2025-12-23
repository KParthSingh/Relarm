package com.medicinereminder.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onThemeChanged: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    
    var selectedTheme by remember { mutableStateOf(repository.getThemeMode()) }
    var closeOnStart by remember { mutableStateOf(repository.getCloseOnStart()) }
    var hideStopButton by remember { mutableStateOf(repository.getHideStopButton()) }
    var dismissableCounter by remember { mutableStateOf(repository.getDismissableCounter()) }
    
    var themeExpanded by remember { mutableStateOf(false) }
    
    val themeOptions = mapOf(
        SettingsRepository.THEME_LIGHT to "Light",
        SettingsRepository.THEME_DARK to "Dark",
        SettingsRepository.THEME_AUTO to "Auto"
    )
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text("Settings")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Theme Section
            SettingsSectionHeader("Appearance")
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp
            ) {
                Column {
                    SettingsDropdownItem(
                        title = "Theme",
                        subtitle = themeOptions[selectedTheme] ?: "Auto",
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it },
                        options = themeOptions,
                        onOptionSelected = { value ->
                            selectedTheme = value
                            repository.setThemeMode(value)
                            onThemeChanged()
                            themeExpanded = false
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Behavior Section
            SettingsSectionHeader("Behavior")
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp
            ) {
                Column {
                    SettingsSwitchItem(
                        title = "Minimize on Start",
                        subtitle = "When you click start chain sequence it will minimize the app to home screen",
                        checked = closeOnStart,
                        onCheckedChange = { enabled ->
                            closeOnStart = enabled
                            repository.setCloseOnStart(enabled)
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    
                    SettingsSwitchItem(
                        title = "Hide Stop Button",
                        subtitle = "Hides the stop sequence button so you can't accidentally click it",
                        checked = hideStopButton,
                        onCheckedChange = { enabled ->
                            hideStopButton = enabled
                            repository.setHideStopButton(enabled)
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    
                    SettingsSwitchItem(
                        title = "Hide Counter Notification",
                        subtitle = "Hides the countdown notification completely. Only the alarm notification will appear when each alarm rings",
                        checked = dismissableCounter,
                        onCheckedChange = { enabled ->
                            dismissableCounter = enabled
                            repository.setDismissableCounter(enabled)
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    
                    // Default Alarm Time Option
                    var showTimeDialog by remember { mutableStateOf(false) }
                    var defaultTime by remember { mutableStateOf(repository.getDefaultAlarmTime()) }
                    
                    SettingsClickableItem(
                        title = context.getString(R.string.settings_default_time),
                        subtitle = context.getString(R.string.settings_default_time_desc),
                        value = String.format("%02d:%02d:%02d", 
                            defaultTime / 3600,
                            (defaultTime % 3600) / 60,
                            defaultTime % 60
                        ),
                        onClick = { showTimeDialog = true }
                    )
                    
                    // Time Picker Dialog
                    if (showTimeDialog) {
                        val hours = defaultTime / 3600
                        val minutes = (defaultTime % 3600) / 60
                        val seconds = defaultTime % 60
                        
                        AlertDialog(
                            onDismissRequest = { showTimeDialog = false },
                            title = {
                                Text(context.getString(R.string.settings_default_time_dialog_title))
                            },
                            text = {
                                SimpleDurationPicker(
                                    hours = hours,
                                    minutes = minutes,
                                    seconds = seconds,
                                    onTimeChange = { h, m, s ->
                                        defaultTime = h * 3600 + m * 60 + s
                                    }
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        repository.setDefaultAlarmTime(defaultTime)
                                        showTimeDialog = false
                                    }
                                ) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { 
                                    defaultTime = repository.getDefaultAlarmTime()
                                    showTimeDialog = false
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // System Section
            SettingsSectionHeader("System")
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp
            ) {
                Column {
                    SettingsClickableItem(
                        title = context.getString(R.string.settings_autostart),
                        subtitle = context.getString(R.string.settings_autostart_desc),
                        onClick = { AutostartHelper.openAutostartSettings(context) }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    
                    // Clear App Data Option
                    var showClearDialog by remember { mutableStateOf(false) }
                    
                    SettingsClickableItem(
                        title = context.getString(R.string.settings_clear_data),
                        subtitle = context.getString(R.string.settings_clear_data_desc),
                        onClick = { showClearDialog = true },
                        isDestructive = true
                    )
                    
                    // Clear Data Confirmation Dialog
                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = {
                                Text(context.getString(R.string.settings_clear_data_confirm_title))
                            },
                            text = {
                                Text(context.getString(R.string.settings_clear_data_confirm_message))
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        // Clear all data
                                        AlarmRepository(context).clearAllAlarms()
                                        context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                                            .edit()
                                            .clear()
                                            .apply()
                                        showClearDialog = false
                                        onNavigateBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(context.getString(R.string.settings_clear_data_confirm_yes))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearDialog = false }) {
                                    Text(context.getString(R.string.settings_clear_data_confirm_no))
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Developer Options
            SettingsSectionHeader("Developer Options")
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp
            ) {
                Column {
                    var forceBatteryWarning by remember { mutableStateOf(repository.getForceBatteryWarning()) }
                    
                    SettingsSwitchItem(
                        title = "Force Battery Warning",
                        subtitle = "Show battery optimization warning on any device (for testing)",
                        checked = forceBatteryWarning,
                        onCheckedChange = { enabled ->
                            forceBatteryWarning = enabled
                            repository.setForceBatteryWarning(enabled)
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    
                    // Export Debug Logs
                    SettingsClickableItem(
                        title = "Export Debug Logs",
                        subtitle = "Share debug log file for troubleshooting",
                        onClick = {
                            val exportFile = DebugLogger.exportLogs(context)
                            if (exportFile != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    exportFile
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Debug Logs"))
                            }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    
                    // Clear Debug Logs
                    var showClearLogsDialog by remember { mutableStateOf(false) }
                    
                    SettingsClickableItem(
                        title = "Clear Debug Logs",
                        subtitle = "Delete all debug log files",
                        onClick = { showClearLogsDialog = true },
                        isDestructive = true
                    )
                    
                    if (showClearLogsDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearLogsDialog = false },
                            title = { Text("Clear All Logs?") },
                            text = { Text("This will permanently delete all debug log files. This action cannot be undone.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        DebugLogger.clearLogs()
                                        showClearLogsDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Clear")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearLogsDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String,
    value: String? = null,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDestructive) 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f) 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownItem(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: Map<String, String>,
    onOptionSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .clickable { onExpandedChange(!expanded) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onOptionSelected(value) },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}
