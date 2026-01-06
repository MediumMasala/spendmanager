package com.spendmanager.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendmanager.app.data.model.Transaction
import com.spendmanager.app.data.model.TransactionDirection
import com.spendmanager.app.ui.theme.*
import com.spendmanager.app.ui.viewmodel.HomeViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTransactions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    Scaffold(
        containerColor = White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Spend",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = White,
                    titleContentColor = Charcoal
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                            tint = CharcoalMuted
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = CharcoalMuted
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(White),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero Summary Card
            item {
                Column {
                    Text(
                        text = "This Week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CharcoalMuted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currencyFormat.format(uiState.weeklySpent),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-1).sp
                        ),
                        color = Charcoal
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "spent across ${uiState.transactionCount} transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CharcoalMuted
                    )
                }
            }

            // Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Spent Card
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Spent",
                        value = currencyFormat.format(uiState.weeklySpent),
                        icon = Icons.Outlined.ArrowUpward,
                        iconColor = AccentRed,
                        backgroundColor = AccentRedLight
                    )

                    // Received Card
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Received",
                        value = currencyFormat.format(uiState.weeklyReceived),
                        icon = Icons.Outlined.ArrowDownward,
                        iconColor = AccentGreen,
                        backgroundColor = AccentGreenLight
                    )
                }
            }

            // Pending uploads
            if (uiState.pendingUploads > 0) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = OffWhite,
                        border = BorderStroke(1.dp, Gray200)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${uiState.pendingUploads} events syncing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CharcoalMuted
                            )
                        }
                    }
                }
            }

            // Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Charcoal
                    )
                    TextButton(onClick = onNavigateToTransactions) {
                        Text(
                            "See all",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CharcoalMuted
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CharcoalMuted
                        )
                    }
                }
            }

            // Transaction list
            if (uiState.recentTransactions.isEmpty()) {
                item {
                    EmptyTransactionsCard()
                }
            } else {
                items(uiState.recentTransactions.take(5)) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        currencyFormat = currencyFormat
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    backgroundColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = White,
        border = BorderStroke(1.dp, Gray200)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = CharcoalMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Charcoal
            )
        }
    }
}

@Composable
private fun EmptyTransactionsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OffWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Gray200),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = CharcoalMuted
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No transactions yet",
                style = MaterialTheme.typography.titleMedium,
                color = Charcoal
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "We'll capture them as they happen",
                style = MaterialTheme.typography.bodyMedium,
                color = CharcoalMuted
            )
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    currencyFormat: NumberFormat
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val isDebit = transaction.direction == TransactionDirection.DEBIT

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = White,
        border = BorderStroke(1.dp, Gray200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isDebit) AccentRedLight else AccentGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDebit) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    tint = if (isDebit) AccentRed else AccentGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant ?: transaction.payee ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Charcoal
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = transaction.category ?: transaction.appSource,
                    style = MaterialTheme.typography.bodySmall,
                    color = CharcoalMuted
                )
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isDebit) "-" else "+"}${currencyFormat.format(transaction.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDebit) AccentRed else AccentGreen
                )

                Text(
                    text = dateFormat.format(Date(transaction.occurredAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = CharcoalMuted
                )
            }
        }
    }
}
