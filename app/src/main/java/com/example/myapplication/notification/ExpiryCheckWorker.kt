package com.example.myapplication.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.auth.UserPreferences
import com.example.myapplication.data.FoodItem
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ExpiryCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "expiry_check_work"
        private const val DAYS_THRESHOLD = 3
    }

    override suspend fun doWork(): Result {
        return try {
            // ---------------------------
            // 1. Get current logged-in user
            // ---------------------------
            val prefs = UserPreferences(context)
            val currentUser = prefs.getCurrentUser()

            if (currentUser == null) {
                return Result.success() // no user logged in
            }

            val userId = currentUser.id

            // ---------------------------
            // 2. Firestore: Load all food items
            // ---------------------------
            val db = Firebase.firestore

            val snapshot = db.collection("users")
                .document(userId)
                .collection("foods")
                .get()
                .await()

            val allFoods = snapshot.toObjects(FoodItem::class.java)

            // ---------------------------
            // 3. Compute expiring foods
            // ---------------------------
            val today = LocalDate.now()

            val expiringFoods = allFoods.filter { food ->
                try {
                    val expiry = LocalDate.parse(food.expiryDate)
                    val daysLeft = ChronoUnit.DAYS.between(today, expiry)
                    daysLeft in 0..DAYS_THRESHOLD
                } catch (e: Exception) {
                    false
                }
            }

            if (expiringFoods.isNotEmpty()) {
                sendNotification(expiringFoods)
            }

            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    // ---------------------------
    // 4. Show notification
    // ---------------------------
    private fun sendNotification(expiringFoods: List<FoodItem>) {
        val itemNames = expiringFoods.take(3).joinToString(", ") { it.name }

        val message = if (expiringFoods.size > 3) {
            "$itemNames and ${expiringFoods.size - 3} more items are expiring soon!"
        } else {
            "$itemNames ${if (expiringFoods.size == 1) "is" else "are"} expiring soon!"
        }

        NotificationHelper.showExpiryNotification(
            context = context,
            notificationId = 1001,
            title = "Food Expiring Soon",
            message = message
        )
    }
}
