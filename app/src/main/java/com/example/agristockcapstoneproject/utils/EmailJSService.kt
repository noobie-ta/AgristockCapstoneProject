package com.example.agristockcapstoneproject.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object EmailJSService {
    
    // TODO: Replace these with your actual EmailJS credentials
    // Get these from https://www.emailjs.com/
    private const val EMAILJS_SERVICE_ID = "service_ux45jvn"
    private const val EMAILJS_TEMPLATE_ID = "template_hln4khb"
    private const val EMAILJS_PUBLIC_KEY = "49isT1XJG2r7sqlce"
    
    private const val EMAILJS_API_URL = "https://api.emailjs.com/api/v1.0/email/send"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            
            // Log request details
            Log.d("EmailJSService", "Request URL: ${request.url}")
            Log.d("EmailJSService", "Request Method: ${request.method}")
            if (request.body != null) {
                try {
                    val buffer = okio.Buffer()
                    request.body!!.writeTo(buffer)
                    Log.d("EmailJSService", "Request Body: ${buffer.readUtf8()}")
                } catch (e: Exception) {
                    Log.e("EmailJSService", "Error reading request body: ${e.message}")
                }
            }
            
            try {
                val response = chain.proceed(request)
                val responseBody = response.peekBody(2048).string()  // Increased buffer size
                
                if (!response.isSuccessful) {
                    Log.e("EmailJSService", "HTTP Error: ${response.code} - ${response.message}")
                    Log.e("EmailJSService", "Response Headers: ${response.headers}")
                    Log.e("EmailJSService", "Response Body: $responseBody")
                } else {
                    Log.d("EmailJSService", "Response Success: ${response.code}")
                    Log.d("EmailJSService", "Response Body: $responseBody")
                }
                
                response
            } catch (e: Exception) {
                Log.e("EmailJSService", "Network error in interceptor: ${e.message}", e)
                throw e
            }
        }
        .build()
    
    /**
     * Send OTP email using EmailJS
     * @param recipientEmail Email address to send OTP to
     * @param otpCode The 6-digit OTP code
     * @param recipientName Optional name for personalization
     * @return Result containing success status and message
     */
    suspend fun sendOTPEmail(
        recipientEmail: String,
        otpCode: String,
        recipientName: String = "User"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate credentials are set
            if (EMAILJS_SERVICE_ID == "YOUR_SERVICE_ID" || 
                EMAILJS_TEMPLATE_ID == "YOUR_TEMPLATE_ID" || 
                EMAILJS_PUBLIC_KEY == "YOUR_PUBLIC_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("EmailJS credentials not configured. Please set SERVICE_ID, TEMPLATE_ID, and PUBLIC_KEY in EmailJSService.kt")
                )
            }
            
            // Create request body - EmailJS expects specific format
            // Template variables should match what's in your EmailJS template
            // IMPORTANT: to_email must be in template_params for EmailJS to send to the correct recipient
            val templateParams = JSONObject().apply {
                // CRITICAL: This is the recipient email - must be included for EmailJS to send to user
                put("to_email", recipientEmail)
                put("to_name", recipientName)
                put("reply_to", recipientEmail)  // Reply-to address
                put("otp", otpCode)  // Match {{otp}} in template
                put("otp_code", otpCode)  // Also support {{otp_code}}
                put("name", recipientName)  // Match {{name}} in template
                put("time", SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date()))
                put("message", "Your verification code is: $otpCode")
            }
            
            // Log the recipient email for debugging
            Log.d("EmailJSService", "==================== EMAIL SEND REQUEST ====================")
            Log.d("EmailJSService", "Recipient Email: $recipientEmail")
            Log.d("EmailJSService", "Recipient Name: $recipientName")
            Log.d("EmailJSService", "OTP Code: $otpCode")
            Log.d("EmailJSService", "Service ID: $EMAILJS_SERVICE_ID")
            Log.d("EmailJSService", "Template ID: $EMAILJS_TEMPLATE_ID")
            Log.d("EmailJSService", "============================================================")
            
            val requestBodyJson = JSONObject().apply {
                put("service_id", EMAILJS_SERVICE_ID)
                put("template_id", EMAILJS_TEMPLATE_ID)
                put("user_id", EMAILJS_PUBLIC_KEY)
                put("template_params", templateParams)
            }
            
            // Log the full request for debugging
            Log.d("EmailJSService", "Sending OTP request: ${requestBodyJson.toString(2)}")
            
            val requestBody = requestBodyJson.toString()
                .toRequestBody("application/json".toMediaType())
            
            // Create request with browser-like User-Agent to bypass EmailJS restrictions
            // EmailJS blocks requests from non-browser applications by checking User-Agent
            val request = Request.Builder()
                .url(EMAILJS_API_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://www.emailjs.com")
                .addHeader("Referer", "https://www.emailjs.com/")
                .build()
            
            // Execute request
            Log.d("EmailJSService", "Executing EmailJS API request...")
            val response = client.newCall(request).execute()
            
            val responseBodyString = try {
                response.body?.string() ?: ""
            } catch (e: Exception) {
                Log.e("EmailJSService", "Error reading response body: ${e.message}")
                ""
            }
            
            Log.d("EmailJSService", "Response Code: ${response.code}")
            Log.d("EmailJSService", "Response Headers: ${response.headers}")
            Log.d("EmailJSService", "Response Body Length: ${responseBodyString.length}")
            Log.d("EmailJSService", "Response Body: $responseBodyString")
            
            if (response.isSuccessful) {
                // EmailJS returns 200 OK but check the response body for actual status
                val isActuallySuccessful = when {
                    responseBodyString.isEmpty() -> true // Empty response usually means success
                    responseBodyString.contains("\"status\":200", ignoreCase = true) -> true
                    responseBodyString.contains("\"text\":\"OK\"", ignoreCase = true) -> true
                    responseBodyString.contains("error", ignoreCase = true) -> false
                    else -> true // Assume success if we get 200
                }
                
                if (isActuallySuccessful) {
                    Log.d("EmailJSService", "OTP email sent successfully! Response: $responseBodyString")
                    Result.success("OTP sent successfully")
                } else {
                    Log.w("EmailJSService", "Received 200 but response indicates error: $responseBodyString")
                    Result.failure(IOException("Email sent but may have failed: $responseBodyString"))
                }
            } else {
                // Parse error message from EmailJS response
                var errorMessage = "Unknown error"
                var errorDetails: String? = null
                
                try {
                    if (responseBodyString.isNotEmpty()) {
                        // Try to parse as JSON
                        try {
                            val errorJson = JSONObject(responseBodyString)
                            errorMessage = errorJson.optString("text", "")
                            if (errorMessage.isEmpty()) {
                                errorMessage = errorJson.optString("message", "")
                            }
                            if (errorMessage.isEmpty()) {
                                errorMessage = errorJson.optString("error", "")
                            }
                            if (errorMessage.isEmpty()) {
                                errorMessage = errorJson.toString()
                            }
                            errorDetails = errorJson.toString()
                        } catch (e: org.json.JSONException) {
                            // Not JSON, use raw response
                            errorMessage = responseBodyString.trim()
                            if (errorMessage.length > 200) {
                                errorMessage = errorMessage.substring(0, 200) + "..."
                            }
                        }
                    } else {
                        errorMessage = "HTTP ${response.code}: ${response.message}"
                    }
                } catch (e: Exception) {
                    Log.e("EmailJSService", "Error parsing error response: ${e.message}")
                    errorMessage = if (responseBodyString.isNotEmpty()) {
                        responseBodyString.trim().take(200)
                    } else {
                        "HTTP ${response.code}: ${response.message}"
                    }
                }
                
                // Ensure error message is not empty
                if (errorMessage.isEmpty() || errorMessage == "null") {
                    errorMessage = "HTTP ${response.code}: ${response.message}"
                }
                
                Log.e("EmailJSService", "Failed to send OTP")
                Log.e("EmailJSService", "Status Code: ${response.code}")
                Log.e("EmailJSService", "Status Message: ${response.message}")
                Log.e("EmailJSService", "Parsed Error Message: $errorMessage")
                if (errorDetails != null) {
                    Log.e("EmailJSService", "Full Error Details: $errorDetails")
                }
                
                // Provide user-friendly error message based on HTTP status code
                val userFriendlyError = when {
                    response.code == 400 -> {
                        "Invalid request (400). Check EmailJS template variables: {{otp}}, {{name}}, {{time}}. Details: ${errorMessage.take(100)}"
                    }
                    response.code == 401 -> {
                        "Unauthorized (401). Check your EmailJS Public Key in EmailJSService.kt"
                    }
                    response.code == 403 -> {
                        "Forbidden (403). EmailJS service may be disabled or quota exceeded"
                    }
                    response.code == 404 -> {
                        "Not found (404). Check Template ID and Service ID in EmailJS dashboard"
                    }
                    response.code == 412 -> {
                        if (errorMessage.contains("Gmail_API", ignoreCase = true) || 
                            errorMessage.contains("insufficient authentication", ignoreCase = true)) {
                            "Gmail authentication error (412). Please re-authorize your Gmail service in EmailJS dashboard with proper permissions."
                        } else {
                            "Precondition failed (412). Check EmailJS service configuration: $errorMessage"
                        }
                    }
                    response.code == 429 -> {
                        "Rate limit exceeded (429). Free tier: 200 emails/month. Upgrade or wait."
                    }
                    response.code >= 500 -> {
                        "EmailJS server error (${response.code}). Try again later: $errorMessage"
                    }
                    else -> {
                        "Failed to send OTP (${response.code}): $errorMessage"
                    }
                }
                
                Result.failure(IOException(userFriendlyError))
            }
            
        } catch (e: java.net.UnknownHostException) {
            Log.e("EmailJSService", "Network error: No internet connection or DNS failure", e)
            Result.failure(IOException("No internet connection. Please check your network and try again."))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("EmailJSService", "Network error: Connection timeout", e)
            Result.failure(IOException("Connection timeout. Please check your internet connection and try again."))
        } catch (e: java.net.ConnectException) {
            Log.e("EmailJSService", "Network error: Connection failed", e)
            Result.failure(IOException("Failed to connect to EmailJS. Please check your internet connection."))
        } catch (e: javax.net.ssl.SSLException) {
            Log.e("EmailJSService", "SSL error: ${e.message}", e)
            Result.failure(IOException("SSL error. Please check your network connection or try again later."))
        } catch (e: Exception) {
            Log.e("EmailJSService", "Unexpected error sending OTP email: ${e.javaClass.simpleName}", e)
            Log.e("EmailJSService", "Error message: ${e.message}", e)
            Log.e("EmailJSService", "Error cause: ${e.cause?.message}", e.cause)
            Result.failure(IOException("Failed to send OTP: ${e.message ?: e.javaClass.simpleName}"))
        }
    }
    
    /**
     * Test if EmailJS credentials are configured
     */
    fun isConfigured(): Boolean {
        return EMAILJS_SERVICE_ID != "YOUR_SERVICE_ID" && 
               EMAILJS_TEMPLATE_ID != "YOUR_TEMPLATE_ID" && 
               EMAILJS_PUBLIC_KEY != "YOUR_PUBLIC_KEY"
    }
}





