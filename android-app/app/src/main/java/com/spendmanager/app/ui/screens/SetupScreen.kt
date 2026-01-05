package com.spendmanager.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
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
import com.spendmanager.app.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = "Configure Your Preferences",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Notification Listener Permission
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Access",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (uiState.notificationListenerEnabled) {
                                    "Enabled - We can read payment notifications"
                                } else {
                                    "Required to capture transaction notifications"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.notificationListenerEnabled) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Enabled",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (!uiState.notificationListenerEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    TransactionNotificationListener.getSettingsIntent()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Notification Access")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Mode Selection
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Privacy Mode",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Local Only Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.consent.localOnlyMode,
                            onClick = { viewModel.setLocalOnlyMode(true) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Local Only (Default)")
                            Text(
                                text = "Data stays on device. No cloud features.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cloud AI Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !uiState.consent.localOnlyMode,
                            onClick = { viewModel.setLocalOnlyMode(false) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Cloud AI Parsing")
                            Text(
                                text = "Enable smart categorization & weekly summaries. Redacted data uploaded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!uiState.consent.localOnlyMode) {
                Spacer(modifier = Modifier.height(16.dp))

                // Additional cloud options
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Cloud Options",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Upload Raw (OFF by default)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.consent.uploadRawEnabled,
                                onCheckedChange = { viewModel.setUploadRaw(it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Upload Raw Messages")
                                Text(
                                    text = "Send unredacted text for better accuracy. OFF by default.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // WhatsApp summaries
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.consent.whatsappEnabled,
                                onCheckedChange = { viewModel.setWhatsappEnabled(it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("WhatsApp Weekly Summaries")
                                Text(
                                    text = "Receive spending summaries on WhatsApp every week.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.saveAndContinue()
                    onSetupComplete()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.notificationListenerEnabled
            ) {
                Text("Continue")
            }

            if (!uiState.notificationListenerEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please enable notification access to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
