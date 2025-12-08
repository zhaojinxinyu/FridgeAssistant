package com.example.myapplication.auth

import java.util.UUID

/**
 * Data model for a user
 */
data class User(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
