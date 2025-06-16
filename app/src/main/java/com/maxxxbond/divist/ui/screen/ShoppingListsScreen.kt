package com.maxxxbond.divist.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseUser
import com.maxxxbond.divist.data.model.ShoppingList
import com.maxxxbond.divist.data.model.ListRules
import com.maxxxbond.divist.data.repository.FirestoreRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListsScreen(
    user: FirebaseUser,
    onSignOut: () -> Unit,
    onSelectList: (ShoppingList) -> Unit,
    paddingValues: PaddingValues,
    firestoreRepository: FirestoreRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State для діалогу створення списку
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<ShoppingList?>(null) }
    var newListName by remember { mutableStateOf("") }
    var selfAssign by remember { mutableStateOf(true) }
    var onlyHostAssign by remember { mutableStateOf(false) }
    
    // Отримуємо списки користувача
    val lists by firestoreRepository.getUserShoppingLists().collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Логування для debug
    LaunchedEffect(user.uid) {
        Log.d("ShoppingListsScreen", "User logged in: ${user.uid}, email: ${user.email}")
    }
    
    LaunchedEffect(lists.size) {
        Log.d("ShoppingListsScreen", "Lists updated: ${lists.size} lists found")
        lists.forEach { list ->
            Log.d("ShoppingListsScreen", "List: ${list.name} (ID: ${list.id}, Owner: ${list.ownerUid})")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        // Хедер з інформацією про користувача та кнопкою виходу
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Divist - Списки покупок",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (user.displayName != null) {
                            Text(
                                text = user.displayName!!,
                                fontSize = 16.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        Text(
                            text = user.email ?: "",
                            fontSize = 14.sp,
                            color = Color(0xFF888888)
                        )
                    }
                    
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = "Вийти",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        // Хедер списків з кнопкою створення
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Мої списки",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Створити список")
            }
        }
        
        // Список покупок
        if (lists.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Text(
                    text = "Поки що немає списків покупок.\nСтворіть перший список!",
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF666666)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lists) { list ->
                    ShoppingListCard(
                        list = list,
                        currentUserUid = user.uid,
                        onClick = { onSelectList(list) },
                        onShare = {
                            // Генеруємо два типи посилань для тестування
                            val httpsUrl = "https://divist.app/invite?listId=${list.id}&token=${list.inviteToken}"
                            val customSchemeUrl = "divist://invite?listId=${list.id}&token=${list.inviteToken}"
                            
                            val shareText = """
                                Приєднуйтесь до мого списку покупок "${list.name}"!
                                
                                Посилання: $httpsUrl
                                
                                Або скористайтесь цим посиланням для тестування: $customSchemeUrl
                            """.trimIndent()
                            
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Invite Link", shareText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Посилання скопійовано в буфер обміну", Toast.LENGTH_SHORT).show()
                        },
                        onEdit = if (list.isOwner(user.uid)) {
                            {
                                selectedList = list
                                newListName = list.name
                                selfAssign = list.rules.selfAssign
                                onlyHostAssign = list.rules.onlyHostAssign
                                showEditDialog = true
                            }
                        } else null,
                        onDelete = if (list.isOwner(user.uid)) {
                            {
                                selectedList = list
                                showDeleteConfirmDialog = true
                            }
                        } else null,
                        firestoreRepository = firestoreRepository
                    )
                }
            }
        }
    }
    
    // Діалог створення нового списку
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Створити список покупок") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        label = { Text("Назва списку") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Правила списку:", fontWeight = FontWeight.Medium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selfAssign,
                            onCheckedChange = { selfAssign = it }
                        )
                        Text("Учасники можуть самі призначати товари")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = onlyHostAssign,
                            onCheckedChange = { onlyHostAssign = it }
                        )
                        Text("Тільки власник може призначати")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            scope.launch {
                                val rules = ListRules(
                                    selfAssign = selfAssign,
                                    onlyHostAssign = onlyHostAssign
                                )
                                val result = firestoreRepository.createShoppingList(
                                    name = newListName.trim(),
                                    rules = rules
                                )
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(context, "Список створено", Toast.LENGTH_SHORT).show()
                                        newListName = ""
                                        selfAssign = true
                                        onlyHostAssign = false
                                        showCreateDialog = false
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, "Помилка: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text("Створити")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }
    
    // Діалог редагування списку
    if (showEditDialog && selectedList != null) {
        AlertDialog(
            onDismissRequest = { 
                showEditDialog = false
                selectedList = null
            },
            title = { Text("Редагувати список") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        label = { Text("Назва списку") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Правила списку:", fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selfAssign,
                            onCheckedChange = { selfAssign = it }
                        )
                        Text("Учасники можуть самі призначати собі товари")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = onlyHostAssign,
                            onCheckedChange = { onlyHostAssign = it }
                        )
                        Text("Тільки власник може призначати товари")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedList?.let { list ->
                            scope.launch {
                                try {
                                    val updatedList = list.copy(
                                        name = newListName.trim(),
                                        rules = list.rules.copy(
                                            selfAssign = selfAssign,
                                            onlyHostAssign = onlyHostAssign
                                        )
                                    )
                                    firestoreRepository.updateShoppingList(updatedList)
                                    Toast.makeText(context, "Список оновлено", Toast.LENGTH_SHORT).show()
                                    showEditDialog = false
                                    selectedList = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Помилка оновлення: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = newListName.isNotBlank()
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showEditDialog = false
                        selectedList = null
                    }
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
    
    // Діалог підтвердження видалення
    if (showDeleteConfirmDialog && selectedList != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                selectedList = null
            },
            title = { Text("Видалити список") },
            text = { 
                Text("Ви впевнені, що хочете видалити список '${selectedList!!.name}'?\n\nВсі товари в списку також будуть видалені. Цю дію неможливо скасувати.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedList?.let { list ->
                            scope.launch {
                                try {
                                    firestoreRepository.deleteShoppingList(list.id)
                                    Toast.makeText(context, "Список видалено", Toast.LENGTH_SHORT).show()
                                    showDeleteConfirmDialog = false
                                    selectedList = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Помилка видалення: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteConfirmDialog = false
                        selectedList = null
                    }
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@Composable
fun ShoppingListCard(
    list: ShoppingList,
    currentUserUid: String,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    firestoreRepository: FirestoreRepository
) {
    // Отримуємо реальних учасників
    val members by firestoreRepository.getShoppingListMembers(list.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                if (list.isOwner(currentUserUid)) {
                    Row {
                        IconButton(onClick = onShare) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Поділитися",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        onEdit?.let { editAction ->
                            IconButton(onClick = editAction) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Редагувати",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        
                        onDelete?.let { deleteAction ->
                            IconButton(onClick = deleteAction) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Видалити",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Информация о списке
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (list.isOwner(currentUserUid)) Icons.Default.Person else Icons.Default.People,
                        contentDescription = if (list.isOwner(currentUserUid)) "Власник" else "Учасник",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (list.isOwner(currentUserUid)) "Власник" else "Учасник",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "${members.size} учасник${if (members.size != 1) "ів" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Правила списку
            if (list.rules.selfAssign || list.rules.onlyHostAssign) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        list.rules.onlyHostAssign -> "Тільки власник призначає товари"
                        list.rules.selfAssign -> "Можна самостійно призначати товари"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
