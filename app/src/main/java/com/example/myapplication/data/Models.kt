package com.example.myapplication.data

import java.time.LocalDate
import java.util.UUID


data class FoodItem(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var expiryDate: String = LocalDate.now().plusDays(7).toString(),
    var area: String = "Uncategorized",
    var notes: String = "",
    var quantity: Int = 1
)


data class StorageArea(
    var id: String = UUID.randomUUID().toString(),
    var name: String = ""
)


data class Recipe(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var content: String = ""
)
