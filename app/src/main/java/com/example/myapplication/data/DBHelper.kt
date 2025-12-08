package com.example.myapplication.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * SQLite database helper for managing food items, storage areas, and recipes
 * Each user gets their own database file for data isolation.
 */
class DBHelper(context: Context, userId: String? = null) : SQLiteOpenHelper(
    context, 
    if (userId != null) "Fridge_$userId.db" else DATABASE_NAME, 
    null, 
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "Fridge.db"
        private const val DATABASE_VERSION = 3

        // Table names
        private const val TABLE_FOODS = "foods"
        private const val TABLE_AREAS = "areas"
        private const val TABLE_RECIPES = "recipes"

        // Food columns
        private const val COL_ID = "id"
        private const val COL_NAME = "name"
        private const val COL_EXPIRY_DATE = "expiryDate"
        private const val COL_AREA = "area"
        private const val COL_NOTES = "notes"
        private const val COL_QUANTITY = "quantity"

        // Recipe columns
        private const val COL_CONTENT = "content"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_FOODS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_NAME TEXT,
                $COL_EXPIRY_DATE TEXT,
                $COL_AREA TEXT,
                $COL_NOTES TEXT,
                $COL_QUANTITY INTEGER DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_AREAS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_NAME TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_RECIPES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_NAME TEXT,
                $COL_CONTENT TEXT
            )
        """.trimIndent())

        // Default categories
        val defaults = listOf("Vegetables", "Meat", "Seafood", "Fruit", "Condiments", "Drinks", "Snacks", "Other")
        defaults.forEach { name ->
            val cv = ContentValues().apply {
                put(COL_ID, UUID.randomUUID().toString())
                put(COL_NAME, name)
            }
            db.insert(TABLE_AREAS, null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_RECIPES ($COL_ID TEXT PRIMARY KEY, $COL_NAME TEXT, $COL_CONTENT TEXT)")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_FOODS ADD COLUMN $COL_QUANTITY INTEGER DEFAULT 1")
        }
    }

    // --- Food Operations ---

    fun addFood(item: FoodItem) {
        val cv = ContentValues().apply {
            put(COL_ID, item.id)
            put(COL_NAME, item.name)
            put(COL_EXPIRY_DATE, item.expiryDate)
            put(COL_AREA, item.area)
            put(COL_NOTES, item.notes)
            put(COL_QUANTITY, item.quantity)
        }
        writableDatabase.replace(TABLE_FOODS, null, cv)
    }

    fun deleteFood(id: String) {
        writableDatabase.delete(TABLE_FOODS, "$COL_ID=?", arrayOf(id))
    }

    fun getAllFoods(): List<FoodItem> {
        val list = mutableListOf<FoodItem>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_FOODS", null)
        while (cursor.moveToNext()) {
            val quantityIndex = cursor.getColumnIndex(COL_QUANTITY)
            val quantity = if (quantityIndex >= 0) cursor.getInt(quantityIndex) else 1

            list.add(FoodItem(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                expiryDate = cursor.getString(cursor.getColumnIndexOrThrow(COL_EXPIRY_DATE)),
                area = cursor.getString(cursor.getColumnIndexOrThrow(COL_AREA)),
                notes = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTES)),
                quantity = quantity
            ))
        }
        cursor.close()
        return list
    }

    fun getExpiringFoods(daysThreshold: Int): List<FoodItem> {
        return getAllFoods().filter { food ->
            try {
                val expiryDate = java.time.LocalDate.parse(food.expiryDate)
                val daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), expiryDate)
                daysUntilExpiry in 0..daysThreshold
            } catch (e: Exception) {
                false
            }
        }
    }

    // --- Area Operations ---

    fun addArea(area: StorageArea) {
        val cv = ContentValues().apply {
            put(COL_ID, area.id)
            put(COL_NAME, area.name)
        }
        writableDatabase.replace(TABLE_AREAS, null, cv)
    }

    fun updateArea(area: StorageArea) {
        val cv = ContentValues().apply {
            put(COL_NAME, area.name)
        }
        writableDatabase.update(TABLE_AREAS, cv, "$COL_ID=?", arrayOf(area.id))
    }

    fun deleteArea(id: String) {
        writableDatabase.delete(TABLE_AREAS, "$COL_ID=?", arrayOf(id))
    }

    fun getAllAreas(): List<StorageArea> {
        val list = mutableListOf<StorageArea>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_AREAS", null)
        while (cursor.moveToNext()) {
            list.add(StorageArea(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
            ))
        }
        cursor.close()
        return list
    }

    // --- Recipe Operations ---

    fun addRecipe(recipe: Recipe) {
        val cv = ContentValues().apply {
            put(COL_ID, recipe.id)
            put(COL_NAME, recipe.name)
            put(COL_CONTENT, recipe.content)
        }
        writableDatabase.replace(TABLE_RECIPES, null, cv)
    }

    fun deleteRecipe(id: String) {
        writableDatabase.delete(TABLE_RECIPES, "$COL_ID=?", arrayOf(id))
    }

    fun getAllRecipes(): List<Recipe> {
        val list = mutableListOf<Recipe>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_RECIPES", null)
        while (cursor.moveToNext()) {
            list.add(Recipe(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT))
            ))
        }
        cursor.close()
        return list
    }
}
