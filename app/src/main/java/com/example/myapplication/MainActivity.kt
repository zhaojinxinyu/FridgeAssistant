package com.example.myapplication

import android.Manifest
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Import new modules
import com.example.myapplication.data.FoodItem
import com.example.myapplication.data.Recipe
import com.example.myapplication.data.StorageArea
import com.example.myapplication.ai.AIService
import com.example.myapplication.ui.theme.*
import com.example.myapplication.ui.components.DeleteConfirmationDialog
import com.example.myapplication.ui.components.SearchBar
import com.example.myapplication.ui.components.SwipeToDeleteContainer
import com.example.myapplication.ui.components.FormattedRecipeText
import com.example.myapplication.notification.NotificationHelper
import com.example.myapplication.notification.ExpiryCheckWorker
import com.example.myapplication.auth.User
import com.example.myapplication.auth.UserPreferences
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.data.FirebaseRepository


// --- Activity ---
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup notification channel
        NotificationHelper.createNotificationChannel(this)
        
        // Schedule daily expiry check
        scheduleExpiryCheck()
        
        setContent {
            AppTheme {
                SmartFridgeApp()
            }
        }
    }
    
    private fun scheduleExpiryCheck() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
            
        val workRequest = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

// --- Main UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFridgeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val userPreferences = remember { UserPreferences(context) }
    var currentUser by remember { mutableStateOf(userPreferences.getCurrentUser()) }

    // ------------------------------
    // Firebase Repository (替代 DBHelper)
    // ------------------------------
    val repo = remember(currentUser?.id) {
        currentUser?.id?.let { FirebaseRepository(it) }
    }

    // ------------------------------
    // Firebase 实时数据：Foods
    // ------------------------------
    var foodList by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    LaunchedEffect(currentUser) {
        currentUser?.let {
            repo?.listenFoods { list ->
                foodList = list.sortedBy { it.expiryDate }
            }
        }
    }

    // ------------------------------
    // Firebase 实时数据：Areas
    // ------------------------------
    var storageAreas by remember { mutableStateOf<List<StorageArea>>(emptyList()) }
    LaunchedEffect(currentUser) {
        currentUser?.let {
            repo?.listenAreas { list ->
                storageAreas = list
            }
        }
    }

    // ------------------------------
    // Firebase 实时数据：Recipes
    // ------------------------------
    var savedRecipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    LaunchedEffect(currentUser) {
        currentUser?.let {
            repo?.listenRecipes { list ->
                savedRecipes = list
            }
        }
    }

    // ------------------------------
    // UI States
    // ------------------------------
    var showEditDialog by remember { mutableStateOf(false) }
    var showBulkAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf(FoodItem()) }
    var scannedItems by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<FoodItem?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ------------------------------
    // LOGIN
    // ------------------------------
    if (currentUser == null) {
        LoginScreen(
            userPreferences = userPreferences,
            onLoginSuccess = { user -> currentUser = user }
        )
        return
    }

    // ------------------------------
    // MAIN SCAFFOLD
    // ------------------------------
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { padding ->

        NavHost(navController, "inventory", Modifier.padding(padding)) {

            // ---------------- INVENTORY ----------------
            composable("inventory") {
                InventoryScreen(
                    foodList = foodList,
                    areas = storageAreas,
                    onAddClick = {
                        val defaultArea = storageAreas.firstOrNull()?.name ?: "Other"
                        editingItem = FoodItem(area = defaultArea)
                        showEditDialog = true
                    },
                    onCameraClick = {},
                    onGalleryClick = {},
                    onEditItem = { item ->
                        editingItem = item
                        showEditDialog = true
                    },
                    onDeleteItem = { item ->
                        itemToDelete = item
                        showDeleteConfirmation = true
                    }
                )
            }

            // ---------------- RECIPES ----------------
            composable("recipes") {
                RecipeScreen(
                    foodList = foodList,
                    savedRecipes = savedRecipes,

                    // FIXED: 使用 Firestore 保存 recipe
                    onSaveRecipe = { name ->
                        scope.launch {
                            isProcessing = true
                            val content = AIService.generateFullRecipe(name)

                            repo?.addRecipe(
                                Recipe(name = name, content = content)
                            )

                            isProcessing = false
                        }
                    },

                    // FIXED: 删除 recipe
                    onDeleteRecipe = { id ->
                        repo?.deleteRecipe(id)
                    },

                    navController = navController
                )
            }

            // ---------------- RECIPE DETAIL ----------------
            composable("recipe_detail/{recipeId}") { entry ->
                val recipe = savedRecipes.find { it.id == entry.arguments?.getString("recipeId") }
                if (recipe != null) RecipeDetailScreen(recipe) { navController.popBackStack() }
            }

            // ---------------- SETTINGS ----------------
            composable("settings") {
                SettingsScreen(
                    currentUser = currentUser,
                    areas = storageAreas,

                    // FIXED: Firestore create area
                    onAddArea = { name ->
                        repo?.addArea(StorageArea(name = name))
                    },

                    // FIXED: Firestore update area
                    onUpdateArea = { area ->
                        repo?.updateArea(area)
                    },

                    // FIXED: Firestore delete area
                    onDeleteArea = { id ->
                        repo?.deleteArea(id)
                    },

                    onLogout = {
                        userPreferences.logout()
                        currentUser = null
                    }
                )
            }
        }

        // ------------------------------
        // EDIT ITEM DIALOG
        // ------------------------------
        if (showEditDialog) {
            EditItemDialog(
                item = editingItem,
                areas = storageAreas,
                onDismiss = { showEditDialog = false },

                // FIXED: Firestore save food
                onSave = { item ->
                    repo?.addOrUpdateFood(item)
                    showEditDialog = false
                    Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ------------------------------
        // BULK ADD (FIXED)
        // ------------------------------
        if (showBulkAddDialog) {
            BulkAddDialog(
                initialItems = scannedItems,
                areas = storageAreas,
                onDismiss = { showBulkAddDialog = false },

                // FIXED: Firestore bulk insert
                onSave = { items ->
                    items.forEach { food -> repo?.addOrUpdateFood(food) }
                    showBulkAddDialog = false
                    Toast.makeText(context, "Saved ${items.size} items!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ------------------------------
        // DELETE CONFIRMATION
        // ------------------------------
        if (showDeleteConfirmation && itemToDelete != null) {
            DeleteConfirmationDialog(
                itemName = itemToDelete?.name,
                onConfirm = {
                    itemToDelete?.let { item ->
                        repo?.deleteFood(item.id)
                        showDeleteConfirmation = false
                        Toast.makeText(context, "Deleted!", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {
                    showDeleteConfirmation = false
                    itemToDelete = null
                }
            )
        }

        if (isProcessing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(16.dp))
                        Text("Processing…")
                    }
                }
            }
        }
    }
}


// --- Inventory Screen with Search ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    foodList: List<FoodItem>,
    areas: List<StorageArea>,
    onAddClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onEditItem: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit
) {
    var showAddOptions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter food list based on search query
    val filteredFoodList = remember(foodList, searchQuery) {
        if (searchQuery.isBlank()) {
            foodList
        } else {
            foodList.filter { 
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.area.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    val grouped = filteredFoodList.groupBy { it.area }

    Box(Modifier.fillMaxSize()) {
        Column {
            // Modern Header
            CenterAlignedTopAppBar(
                title = { Text("Inventory", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { showAddOptions = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            )
            
            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search food or category...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (filteredFoodList.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (searchQuery.isNotBlank()) "No results found."
                                else "Your fridge is empty.\nTap + to add items.",
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                areas.forEach { area ->
                    val items = grouped[area.name] ?: emptyList()
                    if (items.isNotEmpty()) {
                        item {
                            Text(
                                area.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(items, key = { it.id }) { item ->
                            SwipeToDeleteContainer(
                                onDelete = { onDeleteItem(item) }
                            ) {
                                FoodCard(item, onEditItem)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                val others = filteredFoodList.filter { item -> areas.none { it.name == item.area } }
                if (others.isNotEmpty()) {
                    item {
                        Text("Others", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    }
                    items(others, key = { it.id }) { item ->
                        SwipeToDeleteContainer(
                            onDelete = { onDeleteItem(item) }
                        ) {
                            FoodCard(item, onEditItem)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        if (showAddOptions) {
            AlertDialog(
                onDismissRequest = { showAddOptions = false },
                icon = { Icon(Icons.Default.Inventory2, null) },
                title = { Text("Add Food") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {


                        OutlinedButton(onClick = { showAddOptions = false; onAddClick() }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Manual Input")
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
fun FoodCard(item: FoodItem, onClick: (FoodItem) -> Unit) {
    val days = try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(item.expiryDate)) } catch (e: Exception) { 0L }
    val (bgColor, textColor, label) = getFoodItemColors(days)

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick(item) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(textColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Restaurant, null, tint = textColor)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (item.quantity > 1) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "×${item.quantity}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text("Expires: ${item.expiryDate}", style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            Surface(
                color = textColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

// --- Recipe Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    foodList: List<FoodItem>,
    savedRecipes: List<Recipe>,
    onSaveRecipe: (String) -> Unit,
    onDeleteRecipe: (String) -> Unit,
    navController: NavHostController
) {
    var dishName by remember { mutableStateOf("") }
    var aiSmartResult by remember { mutableStateOf<String?>(null) }

    var showIngredientSelector by remember { mutableStateOf(false) }

    var selectedIngredients by remember { mutableStateOf(listOf<String>()) }

    val scope = rememberCoroutineScope()
    var isThinking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        CenterAlignedTopAppBar(
            title = { Text("Cookbook", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
        )

        // Search & Add
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Search & Save Recipe", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = dishName,
                        onValueChange = { dishName = it },
                        placeholder = { Text("e.g. Pasta") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (dishName.isNotBlank()) { onSaveRecipe(dishName); dishName = "" } },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Icon(Icons.Default.Save, null)
                    }
                }
            }
        }

        // Smart Suggest
        FilledTonalButton(//change_1//
            onClick = { showIngredientSelector = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isThinking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("What can I cook now?", style = MaterialTheme.typography.titleMedium)
            }
        }

        // AI Result Card
        if (aiSmartResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(max = 350.dp),
                colors = CardDefaults.cardColors(containerColor = LightGreen),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Chef's Suggestion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                        IconButton(onClick = { aiSmartResult = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = PrimaryGreen)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        FormattedRecipeText(text = aiSmartResult!!)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Saved Recipes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(savedRecipes) { recipe ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate("recipe_detail/${recipe.id}") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MenuBook, null, tint = PrimaryGreen)
                            Spacer(Modifier.width(16.dp))
                            Text(recipe.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { onDeleteRecipe(recipe.id) }) {
                            Icon(Icons.Outlined.Delete, null, tint = Color.Gray)
                        }
                    }
                }
            }
        }
        //change_2//
        // --- Ingredient Selector Dialog ---
        if (showIngredientSelector) {
            AlertDialog(
                onDismissRequest = { showIngredientSelector = false },
                title = { Text("Select Ingredients") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        foodList.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIngredients =
                                            if (selectedIngredients.contains(item.name))
                                                selectedIngredients - item.name
                                            else
                                                selectedIngredients + item.name
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedIngredients.contains(item.name),
                                    onCheckedChange = {
                                        selectedIngredients =
                                            if (selectedIngredients.contains(item.name))
                                                selectedIngredients - item.name
                                            else
                                                selectedIngredients + item.name
                                    }
                                )
                                Text(item.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showIngredientSelector = false
                            isThinking = true

                            // change3
                            val inputList =
                                if (selectedIngredients.isEmpty()) {
                                    foodList.map { it.name }     // All ingredients are used by default
                                } else {
                                    selectedIngredients.toList() // Otherwise, use the ingredients selected by the user
                                }
                            Log.d("RecipeScreen", "Selected ingredients for AI: $inputList")
                            scope.launch {
                                aiSmartResult = AIService.recommendSmartRecipe(inputList)
                                isThinking = false
                            }
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showIngredientSelector = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        //change_2 over//
    }
}

// --- Recipe Detail Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(recipe: Recipe, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = { Text(recipe.name, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                FormattedRecipeText(text = recipe.content)
            }
        }
    }
}

// --- Settings Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: User?,
    areas: List<StorageArea>,
    onAddArea: (String) -> Unit,
    onUpdateArea: (StorageArea) -> Unit,
    onDeleteArea: (String) -> Unit,
    onLogout: () -> Unit
) {
    var newArea by remember { mutableStateOf("") }
    var editingArea by remember { mutableStateOf<StorageArea?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        CenterAlignedTopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
        )
        
        // User Profile Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(PrimaryGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        currentUser?.name?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        currentUser?.name ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Logged in",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryGreen
                    )
                }
                FilledTonalButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Logout")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Add Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newArea,
                        onValueChange = { newArea = it },
                        placeholder = { Text("e.g. Pantry") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (newArea.isNotBlank()) { onAddArea(newArea); newArea = "" } },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Add") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(areas) { area ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { editingArea = area },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(area.name, style = MaterialTheme.typography.bodyLarge)
                        Row {
                            IconButton(onClick = { editingArea = area }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDeleteArea(area.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Delete, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingArea != null) {
        var editedName by remember { mutableStateOf(editingArea!!.name) }
        AlertDialog(
            onDismissRequest = { editingArea = null },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Edit Category") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editedName.isNotBlank()) {
                        onUpdateArea(editingArea!!.copy(name = editedName))
                        editingArea = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingArea = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        Triple("inventory", Icons.Default.Kitchen, "Inventory"),
        Triple("recipes", Icons.Default.MenuBook, "Recipes"),
        Triple("settings", Icons.Default.Settings, "Settings")
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEach { (route, icon, label) ->
            val selected = currentRoute == route
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = selected,
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryGreen,
                    selectedTextColor = PrimaryGreen,
                    indicatorColor = LightGreen
                )
            )
        }
    }
}

// --- Edit Dialog with quantity and date picker ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemDialog(item: FoodItem, areas: List<StorageArea>, onDismiss: () -> Unit, onSave: (FoodItem) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var date by remember { mutableStateOf(item.expiryDate) }
    var selectedArea by remember { mutableStateOf(item.area) }
    var quantity by remember { mutableStateOf(item.quantity.toString()) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Validation state
    var nameError by remember { mutableStateOf(false) }
    
    fun showDatePicker() {
        val calendar = Calendar.getInstance()
        try {
            val localDate = LocalDate.parse(date)
            calendar.set(localDate.year, localDate.monthValue - 1, localDate.dayOfMonth)
        } catch (e: Exception) { }
        
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                date = LocalDate.of(year, month + 1, dayOfMonth).toString()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Name *") },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Name is required") }} else null,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { 
                            if (it.isEmpty() || it.toIntOrNull() != null) {
                                quantity = it
                            }
                        },
                        label = { Text("Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    OutlinedTextField(
                        value = date,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expiry Date") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker() }) {
                                Icon(Icons.Default.CalendarMonth, null)
                            }
                        },
                        modifier = Modifier.weight(0.7f).clickable { showDatePicker() },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                
                Box {
                    OutlinedTextField(
                        value = selectedArea,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        areas.forEach { area ->
                            DropdownMenuItem(text = { Text(area.name) }, onClick = { selectedArea = area.name; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onSave(item.copy(
                            name = name,
                            expiryDate = date,
                            area = selectedArea,
                            quantity = quantity.toIntOrNull() ?: 1
                        ))
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- Bulk Add Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAddDialog(initialItems: List<FoodItem>, areas: List<StorageArea>, onDismiss: () -> Unit, onSave: (List<FoodItem>) -> Unit) {
    val items = remember(initialItems) { mutableStateListOf(*initialItems.toTypedArray()) }
    val context = LocalContext.current

    fun getDaysLeft(dateStr: String): String = try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dateStr)).toString() } catch (e: Exception) { "" }
    fun getDateFromDays(days: Int): String = LocalDate.now().plusDays(days.toLong()).toString()
    fun showDatePicker(currentDate: String, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        try { val date = LocalDate.parse(currentDate); calendar.set(date.year, date.monthValue - 1, date.dayOfMonth) } catch (e: Exception) { }
        DatePickerDialog(context, { _, y, m, d -> onDateSelected(LocalDate.of(y, m + 1, d).toString()) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Items", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("${items.size} items found.", style = MaterialTheme.typography.bodyMedium, color = TextGray)
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(items) { index, item ->
                        key(item.id) {
                            var daysText by remember { mutableStateOf(getDaysLeft(item.expiryDate)) }
                            var expanded by remember { mutableStateOf(false) }
                            var quantityText by remember { mutableStateOf(item.quantity.toString()) }

                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = item.name,
                                            onValueChange = { items[index] = item.copy(name = it) },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Name") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedBorderColor = PrimaryGreen
                                            )
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Box(Modifier.width(100.dp)) {
                                            OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                                Text(item.area, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 12.sp)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                areas.forEach { area -> DropdownMenuItem(text = { Text(area.name) }, onClick = { items[index] = item.copy(area = area.name); expanded = false }) }
                                            }
                                        }
                                        IconButton(onClick = { items.removeAt(index) }) { Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = quantityText,
                                            onValueChange = {
                                                if (it.isEmpty() || it.toIntOrNull() != null) {
                                                    quantityText = it
                                                    items[index] = item.copy(quantity = it.toIntOrNull() ?: 1)
                                                }
                                            },
                                            label = { Text("Qty") },
                                            modifier = Modifier.weight(0.25f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = daysText,
                                            onValueChange = {
                                                daysText = it
                                                it.toIntOrNull()?.let { d -> items[index] = item.copy(expiryDate = getDateFromDays(d)) }
                                            },
                                            label = { Text("Days") }, modifier = Modifier.weight(0.3f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = item.expiryDate, onValueChange = {}, readOnly = true, label = { Text("Date") }, modifier = Modifier.weight(0.45f).clickable {
                                                showDatePicker(item.expiryDate) { newDate -> items[index] = item.copy(expiryDate = newDate); daysText = getDaysLeft(newDate) }
                                            }, enabled = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(items.toList()) }) { Text("Save All") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}