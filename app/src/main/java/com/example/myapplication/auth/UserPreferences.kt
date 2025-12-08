package com.example.myapplication.auth

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences wrapper for user management
 */
class UserPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "smart_fridge_users"
        private const val KEY_USERS = "users"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
    }
    
    /**
     * Get all registered users
     */
    fun getUsers(): List<User> {
        val jsonString = prefs.getString(KEY_USERS, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val users = mutableListOf<User>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            users.add(User(
                id = obj.getString("id"),
                name = obj.getString("name"),
                createdAt = obj.getLong("createdAt")
            ))
        }
        return users
    }
    
    /**
     * Add a new user
     */
    fun addUser(user: User) {
        val users = getUsers().toMutableList()
        users.add(user)
        saveUsers(users)
    }
    
    /**
     * Delete a user
     */
    fun deleteUser(userId: String) {
        val users = getUsers().filter { it.id != userId }
        saveUsers(users)
        if (getCurrentUserId() == userId) {
            setCurrentUserId(null)
        }
    }
    
    /**
     * Save users list to preferences
     */
    private fun saveUsers(users: List<User>) {
        val jsonArray = JSONArray()
        users.forEach { user ->
            val obj = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("createdAt", user.createdAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_USERS, jsonArray.toString()).apply()
    }
    
    /**
     * Get currently logged in user ID
     */
    fun getCurrentUserId(): String? {
        return prefs.getString(KEY_CURRENT_USER_ID, null)
    }
    
    /**
     * Set current user ID (login)
     */
    fun setCurrentUserId(userId: String?) {
        prefs.edit().putString(KEY_CURRENT_USER_ID, userId).apply()
    }
    
    /**
     * Get current user object
     */
    fun getCurrentUser(): User? {
        val userId = getCurrentUserId() ?: return null
        return getUsers().find { it.id == userId }
    }
    
    /**
     * Clear current user (logout)
     */
    fun logout() {
        setCurrentUserId(null)
    }
}
