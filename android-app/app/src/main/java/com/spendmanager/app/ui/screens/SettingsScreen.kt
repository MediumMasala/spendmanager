package com.spendmanager.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendmanager.app.service.TransactionNotificationListener
import com.spendmanager.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Privacy Settings
            ListItem(
                headlineContent = { Text("Privacy Settings") },
                supportingContent = { Text("Configure data collection preferences") },
                leadingContent = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                }
            )

            // Cloud AI Toggle
            ListItem(
                headlineContent = { Text("Cloud AI Parsing") },
                supportingContent = {
                    Text(
                        if (uiState.consent.cloudAiEnabled)
                            "Enabled - Transactions are parsed by AI"
                        else
                            "Disabled - Local storage only"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = uiState.consent.cloudAiEnabled,
                        onCheckedChange = { viewModel.setCloudAiEnabled(it) }
                    )
                }
            )

            if (uiState.consent.cloudAiEnabled) {
                // Upload Raw Toggle
                ListItem(
                    headlineContent = { Text("Upload Raw Messages") },
                    supportingContent = {
                        Text("Send unredacted text for better accuracy")
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.consent.uploadRawEnabled,
                            onCheckedChange = { viewModel.setUploadRawEnabled(it) }
                        )
                    }
                )

                // WhatsApp Toggle
                ListItem(
                    headlineContent = { Text("WhatsApp Summaries") },
                    supportingContent = {
                        Text("Receive weekly spending summaries")
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.consent.whatsappEnabled,
                            onCheckedChange = { viewModel.setWhatsappEnabled(it) }
                        )
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // App Sources
            ListItem(
                headlineContent = { Text("Notification Sources") },
                supportingContent = { Text("Select apps to monitor") },
                leadingContent = {
                    Icon(Icons.Default.Apps, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            )

            // Notification Listener
            ListItem(
                headlineContent = { Text("Notification Access") },
                supportingContent = {
                    Text(
                        if (TransactionNotificationListener.isEnabled(context))
                            "Enabled"
                        else
                            "Disabled - Tap to enable"
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    context.startActivity(TransactionNotificationListener.getSettingsIntent())
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Data Management
            ListItem(
                headlineContent = { Text("Export Data") },
                supportingContent = { Text("Download your transaction data") },
                leadingContent = {
                    Icon(Icons.Default.Download, contentDescription = null)
                },
                modifier = Modifier.clickable { viewModel.exportData() }
            )

            ListItem(
                headlineContent = { Text("Delete All Data") },
                supportingContent = { Text("Remove all local and cloud data") },
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { showDeleteDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Account
            ListItem(
                headlineContent = { Text("Logout") },
                leadingContent = {
                    Icon(Icons.Default.Logout, contentDescription = null)
                },
                modifier = Modifier.clickable { showLogoutDialog = true }
            )

            // Version info
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Data?") },
            text = {
                Text("This will permanently delete all your transaction data from this device and our servers. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteDialog = false
                        onLogout()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout?") },
            text = { Text("Your local data will be preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

