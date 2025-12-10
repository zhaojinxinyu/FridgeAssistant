package com.example.myapplication.data

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase

class FirebaseRepository(private val userId: String) {

    private val db = Firebase.firestore

    private fun userCollection(path: String) =
        db.collection("users").document(userId).collection(path)

    // ----------------------
    // Food CRUD
    // ----------------------
    fun listenFoods(onChanged: (List<FoodItem>) -> Unit) {
        userCollection("foods")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects<FoodItem>() ?: emptyList()
                onChanged(list)
            }
    }

    fun addOrUpdateFood(item: FoodItem) {
        userCollection("foods")
            .document(item.id)
            .set(item)
    }

    fun deleteFood(id: String) {
        userCollection("foods")
            .document(id)
            .delete()
    }

    // ----------------------
    // Areas
    // ----------------------
    fun listenAreas(onChanged: (List<StorageArea>) -> Unit) {
        userCollection("areas")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects<StorageArea>() ?: emptyList()
                onChanged(list)
            }
    }

    fun addArea(area: StorageArea) {
        userCollection("areas")
            .document(area.id)
            .set(area)
    }

    fun updateArea(area: StorageArea) {
        userCollection("areas")
            .document(area.id)
            .set(area)
    }

    fun deleteArea(id: String) {
        userCollection("areas")
            .document(id)
            .delete()
    }

    // ----------------------
    // Recipes
    // ----------------------
    fun listenRecipes(onChanged: (List<Recipe>) -> Unit) {
        userCollection("recipes")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects<Recipe>() ?: emptyList()
                onChanged(list)
            }
    }

    fun addRecipe(recipe: Recipe) {
        userCollection("recipes")
            .document(recipe.id)
            .set(recipe)
    }

    fun deleteRecipe(id: String) {
        userCollection("recipes")
            .document(id)
            .delete()
    }
}
