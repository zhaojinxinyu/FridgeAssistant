package com.example.myapplication.ai

import android.util.Log

import com.example.myapplication.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val generativeModel by lazy {
    GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
}

/**
 * AI Service for processing receipts and generating recipes
 */
object AIService {

    /**
     * Generate a full recipe for a given dish name
     */
    suspend fun generateFullRecipe(dishName: String): String =
        withContext(Dispatchers.IO) {
            try {
                val prompt =
                    "Create a practical cooking recipe for '$dishName'.\n" +
                            "Strictly follow this format in English:\n\n" +
                            "**Ingredients & Seasonings:**\n" +
                            "[List detailed ingredients and seasonings with quantities]\n\n" +
                            "**Missing/Key Ingredients:**\n" +
                            "[Mention main items needed]\n\n" +
                            "**Cooking Instructions:**\n" +
                            "[Detailed step-by-step guide]\n\n" +
                            "IMPORTANT: Direct cooking steps only. No introduction."

                val response = generativeModel.generateContent(prompt)
                response.text ?: "Could not generate recipe."
            } catch (e: Exception) {
                Log.e("AIService", "Error in generateFullRecipe", e)
                "Network Error: ${e.message ?: "Unknown error"}"
            }
        }

    /**
     * Recommend a recipe based on available ingredients
     */
    suspend fun recommendSmartRecipe(inventory: List<String>): String =
        withContext(Dispatchers.IO) {
            try {
                val items = inventory.joinToString(", ")
                Log.d("AIService", "Selected ingredients for AI: $inventory")

                val prompt =
                    "I have these ingredients: $items.\n" +
                            "Task: Recommend ONE best dish I can make.\n\n" +
                            "**Dish Name:** [Name]\n\n" +
                            "**Ingredients from My Fridge:**\n" +
                            "[List items I already have]\n\n" +
                            "**Missing Ingredients:**\n" +
                            "[List essential ingredients I may need]\n\n" +
                            "**Cooking Instructions:**\n" +
                            "[Detailed step-by-step guide]\n\n" +
                            "IMPORTANT: Do NOT include introduction."

                val response = generativeModel.generateContent(prompt)

                Log.d("AIService", "Raw AI response: ${response.text}")

                // 如果 AI 返回 null 或空字符串，替换成提示
                response.text?.takeIf { it.isNotBlank() } ?: "No recommendation available."
            } catch (e: Exception) {
                Log.e("AIService", "Error in recommendSmartRecipe", e)
                "Network Error: ${e.message ?: "Unknown error"}"
            }
        }
}
