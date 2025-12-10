package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.myapplication.auth.BiometricHelper
import com.example.myapplication.auth.User
import com.example.myapplication.auth.UserPreferences
import com.example.myapplication.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    userPreferences: UserPreferences,
    onLoginSuccess: (User) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isLoginMode by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()

    // If previously logged in → use biometric
    val savedUser = userPreferences.getCurrentUser()
    var biometricStatus by remember { mutableStateOf<BiometricHelper.BiometricStatus?>(null) }

    LaunchedEffect(Unit) {
        activity?.let {
            biometricStatus = BiometricHelper(it).isBiometricAvailable()
        }
    }

    fun doBiometricLogin() {
        if (activity == null || savedUser == null) return

        val helper = BiometricHelper(activity)
        helper.authenticate(
            title = "Welcome back",
            subtitle = "Use biometric to log in",
            onSuccess = {
                onLoginSuccess(savedUser)
            },
            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
            onFailed = { Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show() }
        )
    }

    // If a user exists → auto-show biometric login dialog
    LaunchedEffect(savedUser) {
        if (savedUser != null && biometricStatus == BiometricHelper.BiometricStatus.AVAILABLE) {
            doBiometricLogin()
        }
    }

    fun loginWithEmail() {
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(context, "Email and password required", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                isProcessing = false
                val user = result.user!!
                val u = User(id = user.uid, name = user.email ?: "Unknown")
                userPreferences.saveUser(u)
                onLoginSuccess(u)
            }
            .addOnFailureListener {
                isProcessing = false
                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
    }

    fun registerAccount() {
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(context, "Email and password required", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                isProcessing = false
                val user = result.user!!
                val u = User(id = user.uid, name = user.email ?: "Unknown")
                userPreferences.saveUser(u)
                onLoginSuccess(u)
            }
            .addOnFailureListener {
                isProcessing = false
                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        PrimaryGreen.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // App icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Kitchen, contentDescription = null, tint = Color.White, modifier = Modifier.size(50.dp))
            }

            Spacer(Modifier.height(32.dp))

            Text("Smart Fridge", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
            Text(
                if (isLoginMode) "Sign in to continue" else "Create a new account",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isLoginMode) loginWithEmail()
                    else registerAccount()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Text(if (isLoginMode) "Login" else "Register", fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(
                    if (isLoginMode) "Don't have an account? Register"
                    else "Already have an account? Login"
                )
            }

            // Optional biometric login button
            if (savedUser != null && biometricStatus == BiometricHelper.BiometricStatus.AVAILABLE) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = { doBiometricLogin() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometric Login")
                }
            }
        }
    }
}
