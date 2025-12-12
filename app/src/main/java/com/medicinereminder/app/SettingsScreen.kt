package com.medicinereminder.app

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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = themeOptions[selectedTheme] ?: "Auto",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false }
                        ) {
                            themeOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedTheme = value
                                        repository.setThemeMode(value)
                                        onThemeChanged()
                                        themeExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    

                }
            }
            
            // Behavior Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Behavior",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Minimize on Start Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Minimize on Start",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "When you click start chain sequence it will minimize the app to home screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = closeOnStart,
                            onCheckedChange = { enabled ->
                                closeOnStart = enabled
                                repository.setCloseOnStart(enabled)
                            }
                        )
                    }
                    
                    Divider()
                    
                    // Hide Stop Button Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Hide Stop Button",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Hides the stop sequence button so you can't accidentally click it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hideStopButton,
                            onCheckedChange = { enabled ->
                                hideStopButton = enabled
                                repository.setHideStopButton(enabled)
                            }
                        )
                    }
                    
                    Divider()
                    
                    // Dismissable Counter Notification Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Hide Counter Notification",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Hides the countdown notification completely. Only the alarm notification will appear when each alarm rings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dismissableCounter,
                            onCheckedChange = { enabled ->
                                dismissableCounter = enabled
                                repository.setDismissableCounter(enabled)
                            }
                        )
                    }
                    
                    Divider()
                    
                    // Default Alarm Time Option
                    var showTimeDialog by remember { mutableStateOf(false) }
                    var defaultTime by remember { mutableStateOf(repository.getDefaultAlarmTime()) }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showTimeDialog = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                context.getString(R.string.settings_default_time),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                context.getString(R.string.settings_default_time_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            String.format("%02d:%02d:%02d", 
                                defaultTime / 3600,
                                (defaultTime % 3600) / 60,
                                defaultTime % 60
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
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
            
            // System Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "System",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Autostart Settings Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AutostartHelper.openAutostartSettings(context)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                context.getString(R.string.settings_autostart),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                context.getString(R.string.settings_autostart_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Divider()
                    
                    // Clear App Data Option
                    var showClearDialog by remember { mutableStateOf(false) }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showClearDialog = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                context.getString(R.string.settings_clear_data),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                context.getString(R.string.settings_clear_data_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
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
        }
    }
}
