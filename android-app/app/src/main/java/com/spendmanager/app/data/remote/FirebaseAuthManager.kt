package com.spendmanager.app.data.remote

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class FirebaseAuthState {
    object Idle : FirebaseAuthState()
    object Loading : FirebaseAuthState()
    data class CodeSent(val verificationId: String) : FirebaseAuthState()
    data class AutoVerified(val credential: PhoneAuthCredential) : FirebaseAuthState()
    data class Verified(val idToken: String, val uid: String) : FirebaseAuthState()
    data class Error(val message: String) : FirebaseAuthState()
}

@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun sendOtp(phoneNumber: String, activity: Activity): Flow<FirebaseAuthState> = callbackFlow {
        trySend(FirebaseAuthState.Loading)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verification (instant verification or auto-retrieval)
                trySend(FirebaseAuthState.AutoVerified(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                val errorMsg = when {
                    e.message?.contains("blocked") == true ->
                        "Too many requests. Please try again later."
                    e.message?.contains("invalid") == true ->
                        "Invalid phone number. Please check and try again."
                    e.message?.contains("quota") == true ->
                        "Service temporarily unavailable. Please try again later."
                    else -> e.message ?: "Verification failed. Please try again."
                }
                trySend(FirebaseAuthState.Error(errorMsg))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                resendToken = token
                trySend(FirebaseAuthState.CodeSent(verificationId))
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)

        awaitClose { }
    }

    fun resendOtp(phoneNumber: String, activity: Activity): Flow<FirebaseAuthState> = callbackFlow {
        trySend(FirebaseAuthState.Loading)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                trySend(FirebaseAuthState.AutoVerified(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                trySend(FirebaseAuthState.Error(e.message ?: "Verification failed"))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                resendToken = token
                trySend(FirebaseAuthState.CodeSent(verificationId))
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        resendToken?.let { optionsBuilder.setForceResendingToken(it) }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())

        awaitClose { }
    }

    suspend fun verifyOtp(otp: String): FirebaseAuthState {
        val verificationId = storedVerificationId
            ?: return FirebaseAuthState.Error("No verification in progress")

        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            signInWithCredential(credential)
        } catch (e: Exception) {
            FirebaseAuthState.Error(e.message ?: "Verification failed")
        }
    }

    suspend fun signInWithCredential(credential: PhoneAuthCredential): FirebaseAuthState {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user

            if (user != null) {
                val idToken = user.getIdToken(false).await().token
                if (idToken != null) {
                    FirebaseAuthState.Verified(idToken, user.uid)
                } else {
                    FirebaseAuthState.Error("Failed to get ID token")
                }
            } else {
                FirebaseAuthState.Error("Sign in failed")
            }
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("invalid") == true ->
                    "Invalid OTP. Please try again."
                e.message?.contains("expired") == true ->
                    "OTP expired. Please request a new one."
                else -> e.message ?: "Verification failed"
            }
            FirebaseAuthState.Error(errorMsg)
        }
    }

    fun signOut() {
        auth.signOut()
        storedVerificationId = null
        resendToken = null
    }

    fun getCurrentUser() = auth.currentUser

    suspend fun getIdToken(): String? {
        return auth.currentUser?.getIdToken(false)?.await()?.token
    }
}
