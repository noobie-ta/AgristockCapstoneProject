package com.example.agristockcapstoneproject.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OTPManager {
    private const val PREFS_NAME = "otp_prefs"
    private const val KEY_OTP_PREFIX = "otp_"
    private const val KEY_TIMESTAMP_PREFIX = "timestamp_"
    private const val OTP_VALIDITY_MS = 10 * 60 * 1000L // 10 minutes
    
    /**
     * Generate a random 6-digit OTP code
     */
    fun generateOTP(): String {
        return (100000..999999).random().toString()
    }
    
    /**
     * Store OTP code locally with timestamp
     */
    fun storeOTP(context: Context, email: String, otpCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_OTP_PREFIX + email.lowercase().trim()
        val timestampKey = KEY_TIMESTAMP_PREFIX + email.lowercase().trim()
        
        prefs.edit().apply {
            putString(key, otpCode)
            putLong(timestampKey, System.currentTimeMillis())
            apply()
        }
        
        Log.d("OTPManager", "OTP stored for email: $email")
    }
    
    /**
     * Verify OTP code against stored value
     */
    fun verifyOTP(context: Context, email: String, enteredOTP: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_OTP_PREFIX + email.lowercase().trim()
        val timestampKey = KEY_TIMESTAMP_PREFIX + email.lowercase().trim()
        
        val storedOTP = prefs.getString(key, null)
        val timestamp = prefs.getLong(timestampKey, 0)
        
        if (storedOTP == null || timestamp == 0L) {
            Log.d("OTPManager", "No OTP found for email: $email")
            return false
        }
        
        // Check if OTP expired
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - timestamp
        
        if (elapsedTime > OTP_VALIDITY_MS) {
            Log.d("OTPManager", "OTP expired for email: $email. Elapsed: ${elapsedTime / 1000}s")
            // Clear expired OTP
            prefs.edit().remove(key).remove(timestampKey).apply()
            return false
        }
        
        val isValid = storedOTP == enteredOTP
        if (isValid) {
            Log.d("OTPManager", "OTP verified successfully for email: $email")
            // Clear OTP after successful verification
            prefs.edit().remove(key).remove(timestampKey).apply()
        } else {
            Log.d("OTPManager", "OTP verification failed for email: $email")
        }
        
        return isValid
    }
    
    /**
     * Check if a valid OTP exists for the email
     */
    fun hasValidOTP(context: Context, email: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_OTP_PREFIX + email.lowercase().trim()
        val timestampKey = KEY_TIMESTAMP_PREFIX + email.lowercase().trim()
        
        val storedOTP = prefs.getString(key, null)
        val timestamp = prefs.getLong(timestampKey, 0)
        
        if (storedOTP == null || timestamp == 0L) {
            return false
        }
        
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - timestamp
        
        return elapsedTime <= OTP_VALIDITY_MS
    }
    
    /**
     * Get remaining time for OTP in seconds
     */
    fun getRemainingTime(context: Context, email: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestampKey = KEY_TIMESTAMP_PREFIX + email.lowercase().trim()
        val timestamp = prefs.getLong(timestampKey, 0)
        
        if (timestamp == 0L) {
            return 0L
        }
        
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - timestamp
        val remainingTime = (OTP_VALIDITY_MS - elapsedTime) / 1000
        
        return if (remainingTime > 0) remainingTime else 0
    }
    
    /**
     * Resend OTP - generates new OTP and sends via EmailJS
     */
    suspend fun resendOTP(context: Context, email: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val newOTP = generateOTP()
            storeOTP(context, email, newOTP)
            
            val result = EmailJSService.sendOTPEmail(email, newOTP, name)
            result.fold(
                onSuccess = {
                    Log.d("OTPManager", "OTP resent successfully to: $email")
                    Result.success("OTP sent successfully")
                },
                onFailure = { error ->
                    Log.e("OTPManager", "Failed to resend OTP: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e("OTPManager", "Error resending OTP: ${e.message}", e)
            Result.failure(e)
        }
    }
}

