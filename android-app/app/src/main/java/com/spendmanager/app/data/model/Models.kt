package com.spendmanager.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

// Consent state
data class ConsentState(
    val localOnlyMode: Boolean = true,
    val cloudAiEnabled: Boolean = false,
    val uploadRawEnabled: Boolean = false,
    val whatsappEnabled: Boolean = false
)

// User info
data class UserInfo(
    val userId: String,
    val deviceId: String,
    val token: String,
    val isNewUser: Boolean
)

// Event to be uploaded
@Entity(tableName = "event_queue")
data class EventQueueItem(
    @PrimaryKey
    val eventId: String,
    val deviceId: String,
    val appSource: String,
    val postedAt: Long, // Epoch millis
    val textRedacted: String,
    val textRaw: String?, // Only if user enabled upload raw
    val locale: String = "en-IN",
    val timezone: String = "Asia/Kolkata",
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class UploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}

// Parsed transaction from backend
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey
    val id: String,
    val occurredAt: Long,
    val amount: Double,
    val currency: String = "INR",
    val direction: TransactionDirection,
    val instrument: String?,
    val merchant: String?,
    val payee: String?,
    val category: String?,
    val appSource: String,
    val syncedAt: Long = System.currentTimeMillis()
)

enum class TransactionDirection {
    DEBIT,
    CREDIT
}

// Weekly summary
@Entity(tableName = "weekly_summaries")
data class WeeklySummary(
    @PrimaryKey
    val id: String,
    val weekStart: Long,
    val weekEnd: Long,
    val totalSpent: Double,
    val totalReceived: Double,
    val netFlow: Double,
    val transactionCount: Int,
    val topMerchantsJson: String, // JSON array
    val categoriesJson: String, // JSON array
    val syncedAt: Long = System.currentTimeMillis()
)

// Device info
@Entity(tableName = "device_info")
data class DeviceInfo(
    @PrimaryKey
    val id: Int = 1, // Single row
    val deviceId: String?,
    val userId: String?,
    val token: String?,
    val platform: String = "android",
    val appVersion: String?,
    val osVersion: String?,
    val registeredAt: Long?
)

// App sources for notification listening
@Entity(tableName = "app_sources")
data class AppSource(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val category: AppCategory = AppCategory.UPI
)

enum class AppCategory {
    UPI,
    BANK,
    WALLET,
    OTHER
}

// API request/response models
data class OtpRequest(
    val phone: String,
    val countryCode: String = "IN"
)

data class OtpResponse(
    val success: Boolean,
    val message: String,
    val expiresAt: String?
)

data class VerifyOtpRequest(
    val phone: String,
    val otp: String,
    val deviceInfo: DeviceInfoRequest?
)

data class DeviceInfoRequest(
    val platform: String = "android",
    val appVersion: String?,
    val osVersion: String?
)

data class VerifyOtpResponse(
    val success: Boolean,
    val message: String,
    val token: String?,
    val userId: String?,
    val deviceId: String?,
    val isNewUser: Boolean?
)

data class ConsentUpdateRequest(
    val cloudAiEnabled: Boolean?,
    val uploadRawEnabled: Boolean?,
    val whatsappEnabled: Boolean?
)

data class EventBatch(
    val events: List<EventPayload>
)

data class EventPayload(
    val eventId: String,
    val deviceId: String,
    val appSource: String,
    val postedAt: String, // ISO 8601
    val textRedacted: String,
    val textRaw: String?,
    val locale: String,
    val timezone: String
)

data class IngestResponse(
    val success: Boolean,
    val accepted: Int,
    val duplicates: Int,
    val errors: Int
)

data class TransactionResponse(
    val id: String,
    val occurredAt: String,
    val amount: Double,
    val currency: String,
    val direction: String,
    val instrument: String?,
    val merchant: String?,
    val payee: String?,
    val category: String?,
    val appSource: String
)

data class TransactionsListResponse(
    val transactions: List<TransactionResponse>,
    val pagination: PaginationInfo
)

data class PaginationInfo(
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean
)

data class SummaryResponse(
    val weekStart: String,
    val weekEnd: String,
    val totals: SummaryTotals,
    val topMerchants: List<MerchantTotal>,
    val categories: List<CategoryTotal>,
    val transactionCount: Int
)

data class SummaryTotals(
    val totalSpent: Double,
    val totalReceived: Double,
    val netFlow: Double,
    val transactionCount: Int
)

data class MerchantTotal(
    val merchant: String,
    val total: Double,
    val count: Int
)

data class CategoryTotal(
    val category: String,
    val total: Double,
    val count: Int
)
