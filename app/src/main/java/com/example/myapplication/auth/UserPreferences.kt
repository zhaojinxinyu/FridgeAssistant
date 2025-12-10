package com.example.myapplication.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.auth.User

/**
 * User preferences for Firebase-authenticated users
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "smart_fridge_user"
        private const val KEY_USER_ID = "firebase_user_id"
        private const val KEY_USER_EMAIL = "firebase_user_email"
    }

    /**
     * Save user (called after Firebase login/register)
     */
    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_EMAIL, user.name)
            .apply()
    }

    /**
     * Load current user
     */
    fun getCurrentUser(): User? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        return User(id = id, name = email)
    }

    /**
     * Firebase logout
     */
    fun logout() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .apply()
    }
}
