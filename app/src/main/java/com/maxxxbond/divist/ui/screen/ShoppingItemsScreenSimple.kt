package com.maxxxbond.divist.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp
import com.maxxxbond.divist.data.model.ShoppingItem
import com.maxxxbond.divist.data.model.ShoppingList
import com.maxxxbond.divist.data.model.User
import com.maxxxbond.divist.data.repository.FirestoreRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingItemsScreenSimple(
    user: FirebaseUser,
    shoppingList: ShoppingList,
    onBack: () -> Unit,
    firestoreRepository: FirestoreRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State для діалогів
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // State для полів
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemEstimatedPrice by remember { mutableStateOf("") }
    
    // State для вибраного товару
    var selectedItem by remember { mutableStateOf<ShoppingItem?>(null) }
    
    // Отримуємо елементи списку
    val items by firestoreRepository.getShoppingListItems(shoppingList.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    
    val isOwner = user.uid == shoppingList.ownerUid
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Простий заголовок
        TopAppBar(
            title = { Text(shoppingList.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Додати")
                }
            }
        )
        
        // Список товарів
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Спочатку не куплені, потім куплені
            items(items.sortedWith(compareBy<ShoppingItem> { it.isBought }.thenBy { it.name })) { item ->
                SimpleShoppingItemCard(
                    item = item,
                    currentUser = user,
                    isOwner = isOwner,
                    onEdit = {
                        selectedItem = item
                        itemName = item.name
                        itemQuantity = item.quantity.toString()
                        itemEstimatedPrice = item.estimatedPrice.toString()
                        showEditDialog = true
                    },
                    onDelete = {
                        selectedItem = item
                        showDeleteDialog = true
                    },
                    onToggleBought = { newIsBought ->
                        scope.launch {
                            try {
                                val updates = mapOf<String, Any?>(
                                    "isBought" to newIsBought,
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                
                                firestoreRepository.updateShoppingItem(item.id, updates)
                                Toast.makeText(
                                    context,
                                    if (newIsBought) "Куплено!" else "Повернуто в список",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onUpdateActualPrice = { actualPrice ->
                        scope.launch {
                            try {
                                val updates = mapOf<String, Any?>(
                                    "actualPrice" to actualPrice,
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                
                                firestoreRepository.updateShoppingItem(item.id, updates)
                                Toast.makeText(context, "Ціна оновлена", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    firestoreRepository = firestoreRepository
                )
            }
        }
    }
    
    // Діалог додавання товару
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Додати товар") },
            text = {
                Column {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Назва товару") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it },
                        label = { Text("Кількість") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = itemEstimatedPrice,
                        onValueChange = { itemEstimatedPrice = it },
                        label = { Text("Ціна (грн)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val item = ShoppingItem(
                                    listId = shoppingList.id,
                                    name = itemName.trim(),
                                    quantity = itemQuantity.toIntOrNull() ?: 1,
                                    estimatedPrice = itemEstimatedPrice.toFloatOrNull() ?: 0f,
                                    createdBy = user.uid,
                                    createdAt = Timestamp.now(),
                                    updatedAt = Timestamp.now()
                                )
                                
                                firestoreRepository.addShoppingItem(item)
                                
                                itemName = ""
                                itemQuantity = "1"
                                itemEstimatedPrice = ""
                                showAddDialog = false
                                
                                Toast.makeText(context, "Товар додано", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = itemName.isNotBlank()
                ) {
                    Text("Додати")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }
    
    // Діалог редагування товару
    if (showEditDialog && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Редагувати товар") },
            text = {
                Column {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Назва товару") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it },
                        label = { Text("Кількість") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = itemEstimatedPrice,
                        onValueChange = { itemEstimatedPrice = it },
                        label = { Text("Ціна (грн)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val updates = mapOf<String, Any?>(
                                    "name" to itemName.trim(),
                                    "quantity" to (itemQuantity.toIntOrNull() ?: 1),
                                    "estimatedPrice" to (itemEstimatedPrice.toFloatOrNull() ?: 0f),
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                
                                firestoreRepository.updateShoppingItem(selectedItem!!.id, updates)
                                showEditDialog = false
                                Toast.makeText(context, "Товар оновлено", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = itemName.isNotBlank()
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }
    
    // Діалог видалення товару
    if (showDeleteDialog && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Видалити товар") },
            text = { Text("Ви впевнені, що хочете видалити \"${selectedItem!!.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                firestoreRepository.deleteShoppingItem(selectedItem!!.id)
                                showDeleteDialog = false
                                Toast.makeText(context, "Товар видалено", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Видалити")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@Composable
fun SimpleShoppingItemCard(
    item: ShoppingItem,
    currentUser: FirebaseUser,
    isOwner: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleBought: (Boolean) -> Unit,
    onUpdateActualPrice: (Float?) -> Unit,
    firestoreRepository: FirestoreRepository
) {
    val context = LocalContext.current
    var showActualPriceDialog by remember { mutableStateOf(false) }
    var actualPriceText by remember { mutableStateOf(item.actualPrice?.toString() ?: "") }
    
    val canEdit = item.canEdit(currentUser.uid, if (isOwner) currentUser.uid else "")
    val canMarkBought = item.canMarkBought(currentUser.uid) || isOwner
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.isBought) {
                    showActualPriceDialog = true
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isBought) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = item.isBought,
                onCheckedChange = { checked ->
                    if (checked && !item.isBought) {
                        // При позначенні як куплений - показуємо діалог фактичної ціни
                        showActualPriceDialog = true
                    } else {
                        onToggleBought(checked)
                    }
                },
                enabled = canMarkBought
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Інформація про товар
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.isBought) {
                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                    } else null
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "x${item.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (item.estimatedPrice > 0) {
                        Text(
                            text = " • ~${item.estimatedPrice.toInt()} грн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Фактична ціна - завжди показуємо, якщо є
                    if (item.actualPrice != null && item.actualPrice > 0) {
                        Text(
                            text = " • ${item.actualPrice.toInt()} грн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Меню дій
            if (canEdit) {
                var showMenu by remember { mutableStateOf(false) }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Дії")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Редагувати") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("Видалити") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Діалог фактичної ціни
    if (showActualPriceDialog) {
        AlertDialog(
            onDismissRequest = { showActualPriceDialog = false },
            title = { 
                Text(if (item.isBought) "Змінити фактичну ціну" else "Куплено!") 
            },
            text = {
                Column {
                    Text("${item.name}")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = actualPriceText,
                        onValueChange = { actualPriceText = it },
                        label = { Text("Фактична ціна (грн)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val actualPrice = actualPriceText.toFloatOrNull()
                        onUpdateActualPrice(actualPrice)
                        
                        // Якщо товар ще не куплений, позначаємо як куплений
                        if (!item.isBought) {
                            onToggleBought(true)
                        }
                        
                        showActualPriceDialog = false
                    }
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showActualPriceDialog = false }
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}
