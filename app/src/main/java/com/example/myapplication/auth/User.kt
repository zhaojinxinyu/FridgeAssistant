package com.example.myapplication.auth
data class User(
    val id: String,          // Firebase UID
    val name: String,        // Email
    val createdAt: Long = System.currentTimeMillis()
)
