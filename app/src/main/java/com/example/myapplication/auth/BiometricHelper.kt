package com.example.myapplication.auth

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity


class BiometricHelper(private val activity: FragmentActivity) {
    
    private val biometricManager: BiometricManager? = try {
        BiometricManager.from(activity)
    } catch (e: Exception) {
        null
    }
    
    companion object {
        fun getAuthenticators(): Int {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Android 11+ (API 30+)
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    // Android 9-10 (API 28-29)
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                }
                else -> {
                    // Android 8.0-8.1 (API 26-27)
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                }
            }
        }
    }
    
    /**
     * Check if any authentication method is available
     */
    fun isBiometricAvailable(): BiometricStatus {
        if (biometricManager == null) {
            return BiometricStatus.UNAVAILABLE
        }
        
        return try {
            when (biometricManager.canAuthenticate(getAuthenticators())) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
                else -> BiometricStatus.UNAVAILABLE
            }
        } catch (e: Exception) {
            // Handle any unexpected errors on specific devices
            BiometricStatus.UNAVAILABLE
        }
    }
    
    /**
     * Show authentication prompt (biometric or device credential)
     */
    fun authenticate(
        title: String = "Authenticate",
        subtitle: String = "Use fingerprint, face, or PIN to login",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
            
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            
            val authenticators = getAuthenticators()
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
            
            // setNegativeButtonText is required when DEVICE_CREDENTIAL is not used
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                promptInfoBuilder.setNegativeButtonText("Cancel")
            }
            
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            // If authentication fails to start, report error
            onError("Authentication unavailable: ${e.message}")
        }
    }
    
    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NOT_ENROLLED,
        UNAVAILABLE
    }
}
