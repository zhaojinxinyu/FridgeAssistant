package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.fragment.app.FragmentActivity
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
    val activity = context as? FragmentActivity
    
    var users by remember { mutableStateOf(userPreferences.getUsers()) }
    var showAddUserDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var biometricStatus by remember { mutableStateOf<BiometricHelper.BiometricStatus?>(null) }
    
    // Check biometric availability
    LaunchedEffect(Unit) {
        activity?.let {
            val helper = BiometricHelper(it)
            biometricStatus = helper.isBiometricAvailable()
        }
    }
    
    fun authenticateUser(user: User) {
        if (activity == null) {
            Toast.makeText(context, "Error: Activity not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val helper = BiometricHelper(activity)
        
        when (helper.isBiometricAvailable()) {
            BiometricHelper.BiometricStatus.AVAILABLE -> {
                helper.authenticate(
                    title = "Welcome back, ${user.name}",
                    subtitle = "Authenticate to access your fridge",
                    onSuccess = {
                        userPreferences.setCurrentUserId(user.id)
                        onLoginSuccess(user)
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            BiometricHelper.BiometricStatus.NOT_ENROLLED -> {
                // Allow login without biometric if not enrolled
                Toast.makeText(context, "No biometric enrolled. Logging in directly.", Toast.LENGTH_SHORT).show()
                userPreferences.setCurrentUserId(user.id)
                onLoginSuccess(user)
            }
            else -> {
                // Allow login without biometric on unsupported devices
                Toast.makeText(context, "Biometric not available. Logging in directly.", Toast.LENGTH_SHORT).show()
                userPreferences.setCurrentUserId(user.id)
                onLoginSuccess(user)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PrimaryGreen.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // App Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Kitchen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Smart Fridge",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryGreen
            )
            
            Text(
                "Select your profile to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // User List
            if (users.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No users yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Create your first profile to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(users) { user ->
                        UserCard(
                            user = user,
                            onClick = { authenticateUser(user) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Add User Button
            Button(
                onClick = { showAddUserDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add New User", fontSize = 16.sp)
            }
            
            // Biometric status indicator
            biometricStatus?.let { status ->
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        when (status) {
                            BiometricHelper.BiometricStatus.AVAILABLE -> Icons.Default.Fingerprint
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = when (status) {
                            BiometricHelper.BiometricStatus.AVAILABLE -> PrimaryGreen
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (status) {
                            BiometricHelper.BiometricStatus.AVAILABLE -> "Biometric authentication ready"
                            BiometricHelper.BiometricStatus.NOT_ENROLLED -> "No biometric enrolled"
                            BiometricHelper.BiometricStatus.NO_HARDWARE -> "No biometric hardware"
                            else -> "Biometric unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Add User Dialog
    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onAdd = { name ->
                val newUser = User(name = name)
                userPreferences.addUser(newUser)
                users = userPreferences.getUsers()
                showAddUserDialog = false
            }
        )
    }
}

@Composable
private fun UserCard(
    user: User,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    user.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap to login",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.Fingerprint,
                contentDescription = "Login",
                tint = PrimaryGreen,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = PrimaryGreen) },
        title = { Text("Create Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Enter your name to create a personal profile with separate data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Your Name") },
                    placeholder = { Text("e.g., Mom, Dad, Guest") },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Name is required") }} else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onAdd(name.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
