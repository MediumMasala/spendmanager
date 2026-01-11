package com.spendmanager.app.data.remote

import com.spendmanager.app.data.model.*
import com.spendmanager.app.data.model.FirebaseAuthRequest
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/request-otp")
    suspend fun requestOtp(@Body request: OtpRequest): Response<OtpResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

    @POST("auth/firebase")
    suspend fun verifyFirebaseToken(@Body request: FirebaseAuthRequest): Response<VerifyOtpResponse>

    // User
    @GET("user/me")
    suspend fun getUser(): Response<UserResponse>

    @PUT("user/consent")
    suspend fun updateConsent(@Body request: ConsentUpdateRequest): Response<ConsentResponse>

    @POST("user/opt-in-whatsapp")
    suspend fun optInWhatsapp(@Body request: WhatsappOptInRequest): Response<SuccessResponse>

    @DELETE("user/delete")
    suspend fun deleteUser(): Response<SuccessResponse>

    @GET("user/export")
    suspend fun exportData(): Response<ExportResponse>

    @PUT("user/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Response<SuccessResponse>

    @PUT("user/permission-status")
    suspend fun updatePermissionStatus(@Body request: PermissionStatusRequest): Response<SuccessResponse>

    // Events
    @POST("events/ingest")
    suspend fun ingestEvents(@Body batch: EventBatch): Response<IngestResponse>

    @POST("events/retry-failed")
    suspend fun retryFailed(): Response<RetryResponse>

    // Transactions
    @GET("transactions/recent")
    suspend fun getRecentTransactions(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<TransactionsListResponse>

    // Summary
    @GET("summary/latest")
    suspend fun getLatestSummary(): Response<SummaryResponse>

    @POST("summary/compute")
    suspend fun computeSummary(): Response<ComputeSummaryResponse>
}

// Additional response models
data class UserResponse(
    val id: String,
    val country: String,
    val timezone: String,
    val createdAt: String,
    val whatsappOptedIn: Boolean,
    val consent: ConsentResponse?
)

data class ConsentResponse(
    val cloudAiEnabled: Boolean,
    val uploadRawEnabled: Boolean,
    val whatsappEnabled: Boolean
)

data class WhatsappOptInRequest(
    val whatsappNumber: String
)

data class SuccessResponse(
    val success: Boolean
)

data class RetryResponse(
    val retried: Int
)

data class ComputeSummaryResponse(
    val summary: SummaryResponse,
    val whatsappSent: Boolean,
    val whatsappError: String?
)

data class ExportResponse(
    val exportedAt: String,
    val user: UserExport,
    val transactions: List<TransactionResponse>,
    val weeklySummaries: List<SummaryExport>
)

data class UserExport(
    val id: String,
    val country: String,
    val timezone: String,
    val createdAt: String
)

data class SummaryExport(
    val weekStart: String,
    val weekEnd: String,
    val totals: SummaryTotals,
    val categories: List<CategoryTotal>
)
