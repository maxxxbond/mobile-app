package com.maxxxbond.divist.data.repository

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.maxxxbond.divist.data.model.ShoppingItem
import com.maxxxbond.divist.data.model.ShoppingList
import com.maxxxbond.divist.data.model.ListRules
import com.maxxxbond.divist.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val LISTS_COLLECTION = "shoppingLists"
        private const val ITEMS_COLLECTION = "shoppingItems"
        private const val TAG = "FirestoreRepository"
    }
    
    // === Користувачі ===
    
    suspend fun createOrUpdateUser(): Result<User> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val user = User(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                phoneNumber = firebaseUser.phoneNumber ?: "",
                createdAt = null,
                updatedAt = null
            )
            
            val userRef = db.collection(USERS_COLLECTION).document(firebaseUser.uid)
            val existingUser = userRef.get().await()
            
            if (existingUser.exists()) {
                val updates = mapOf(
                    "displayName" to user.displayName,
                    "email" to user.email,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.update(updates).await()
                Log.d(TAG, "User updated: ${firebaseUser.uid}")
            } else {
                val userData = user.toMap().toMutableMap()
                userData["createdAt"] = FieldValue.serverTimestamp()
                userData["updatedAt"] = FieldValue.serverTimestamp()
                
                userRef.set(userData).await()
                Log.d(TAG, "User created: ${firebaseUser.uid}")
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating user", e)
            Result.failure(e)
        }
    }
    
    suspend fun getUserProfile(): Result<User?> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val userDoc = db.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .get()
                .await()
            
            if (userDoc.exists()) {
                val user = userDoc.toObject(User::class.java)
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateUserProfile(updates: Map<String, Any>): Result<Unit> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val updatesWithTimestamp = updates.toMutableMap()
            updatesWithTimestamp["updatedAt"] = FieldValue.serverTimestamp()
            
            db.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .update(updatesWithTimestamp)
                .await()
            
            Log.d(TAG, "User profile updated")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user profile", e)
            Result.failure(e)
        }
    }
    
    // === Списки покупок ===
    
    suspend fun createShoppingList(name: String, rules: ListRules = ListRules()): Result<String> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val inviteToken = UUID.randomUUID().toString()
            
            val list = mapOf(
                "name" to name,
                "ownerUid" to firebaseUser.uid,
                "members" to listOf(firebaseUser.uid), // Власник також є в members
                "inviteToken" to inviteToken,
                "rules" to rules.toMap(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            val docRef = db.collection(LISTS_COLLECTION).add(list).await()
            
            Log.d(TAG, "Shopping list created: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating shopping list", e)
            Result.failure(e)
        }
    }
    
    fun getUserShoppingLists(): Flow<List<ShoppingList>> = callbackFlow {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Log.d(TAG, "No authenticated user, returning empty list")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        Log.d(TAG, "Setting up listener for user shopping lists: ${firebaseUser.uid}")
        
        val listener: ListenerRegistration = db.collection(LISTS_COLLECTION)
            .whereArrayContains("members", firebaseUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to shopping lists", error)
                    return@addSnapshotListener
                }
                
                val lists = snapshot?.documents?.mapNotNull { doc ->
                    val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                    Log.d(TAG, "Found list: ${list?.name} (ID: ${list?.id})")
                    list
                }?.sortedByDescending { it.updatedAt } ?: emptyList() // Сортуємо на клієнті
                
                Log.d(TAG, "Total lists found: ${lists.size}")
                trySend(lists)
            }
        
        awaitClose { 
            Log.d(TAG, "Removing shopping lists listener")
            listener.remove() 
        }
    }
    
    suspend fun updateShoppingList(listId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            val updatesWithTimestamp = updates.toMutableMap()
            updatesWithTimestamp["updatedAt"] = FieldValue.serverTimestamp()
            
            db.collection(LISTS_COLLECTION)
                .document(listId)
                .update(updatesWithTimestamp)
                .await()
            
            Log.d(TAG, "Shopping list updated: $listId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating shopping list", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteShoppingList(listId: String): Result<Unit> {
        return try {
            // Спочатку видаляємо всі товари цього списку
            val itemsQuery = db.collection(ITEMS_COLLECTION)
                .whereEqualTo("listId", listId)
                .get()
                .await()
            
            val batch = db.batch()
            itemsQuery.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            
            // Видаляємо сам список
            batch.delete(db.collection(LISTS_COLLECTION).document(listId))
            batch.commit().await()
            
            Log.d(TAG, "Shopping list deleted: $listId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting shopping list", e)
            Result.failure(e)
        }
    }
    
    suspend fun joinListByToken(listId: String, token: String): Result<Unit> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val listDoc = db.collection(LISTS_COLLECTION).document(listId).get().await()
            if (!listDoc.exists()) {
                return Result.failure(Exception("List not found"))
            }
            
            val list = listDoc.toObject(ShoppingList::class.java)!!
            if (list.inviteToken != token) {
                return Result.failure(Exception("Invalid invite token"))
            }
            
            if (list.members.contains(firebaseUser.uid)) {
                return Result.failure(Exception("Already a member"))
            }
            
            val updatedMembers = list.members + firebaseUser.uid
            updateShoppingList(listId, mapOf("members" to updatedMembers))
            
            Log.d(TAG, "User joined list: $listId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining list", e)
            Result.failure(e)
        }
    }
    
    // === Товари ===
    
    suspend fun createShoppingItem(
        listId: String, 
        name: String, 
        quantity: Int = 1, 
        estimatedPrice: Float = 0f
    ): Result<String> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val item = mapOf(
                "listId" to listId,
                "name" to name,
                "quantity" to quantity,
                "estimatedPrice" to estimatedPrice,
                "actualPrice" to null,
                "assignedTo" to null,
                "assignedToName" to null,
                "isBought" to false,
                "createdBy" to firebaseUser.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            val docRef = db.collection(ITEMS_COLLECTION).add(item).await()
            
            Log.d(TAG, "Shopping item created: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating shopping item", e)
            Result.failure(e)
        }
    }
    
    fun getListItems(listId: String): Flow<List<ShoppingItem>> = callbackFlow {
        val listener: ListenerRegistration = db.collection(ITEMS_COLLECTION)
            .whereEqualTo("listId", listId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to shopping items", error)
                    return@addSnapshotListener
                }
                
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(items)
            }
        
        awaitClose { listener.remove() }
    }
    
    fun getShoppingListItems(listId: String): Flow<List<ShoppingItem>> = callbackFlow {
        Log.d(TAG, "Starting to get shopping list items for listId: $listId")
        val listenerRegistration = db.collection(ITEMS_COLLECTION)
            .whereEqualTo("listId", listId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting shopping list items for $listId", error)
                    // Замість закриття Flow, надсилаємо порожній список
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                Log.d(TAG, "Received snapshot for items in list $listId. Document count: ${snapshot?.documents?.size ?: 0}")
                
                val items = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Log.d(TAG, "Raw document data for ${doc.id}: ${doc.data}")
                        val item = doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id)
                        Log.d(TAG, "Parsed item: ${item?.name} with id: ${doc.id}, isBought: ${item?.isBought}, actualPrice: ${item?.actualPrice}")
                        item
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing shopping item: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d(TAG, "Total items parsed for list $listId: ${items.size}")
                
                // Сортуємо на клієнті за createdAt
                val sortedItems = items.sortedByDescending { it.createdAt }
                trySend(sortedItems)
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    fun getShoppingListMembers(listId: String): Flow<List<User>> = callbackFlow {
        Log.d(TAG, "Getting members for list: $listId")
        
        val listRef = db.collection(LISTS_COLLECTION).document(listId)
        val listenerRegistration = listRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to shopping list", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            if (snapshot != null && snapshot.exists()) {
                try {
                    val shoppingList = snapshot.toObject(ShoppingList::class.java)
                    if (shoppingList != null) {
                        val allMemberIds = mutableSetOf<String>()
                        allMemberIds.add(shoppingList.ownerUid)
                        allMemberIds.addAll(shoppingList.members)
                        
                        Log.d(TAG, "All member IDs: $allMemberIds")
                        
                        if (allMemberIds.isNotEmpty()) {
                            // Використовуємо Tasks.whenAllComplete для отримання всіх користувачів одночасно
                            val userTasks = allMemberIds.map { uid ->
                                db.collection(USERS_COLLECTION).document(uid).get()
                            }
                            
                            Tasks.whenAllComplete(userTasks).addOnCompleteListener { task ->
                                val users = mutableListOf<User>()
                                
                                userTasks.forEach { userTask ->
                                    if (userTask.isSuccessful) {
                                        val userDoc = userTask.result
                                        if (userDoc.exists()) {
                                            try {
                                                val user = userDoc.toObject(User::class.java)
                                                if (user != null) {
                                                    users.add(user)
                                                    Log.d(TAG, "Found user: ${user.displayName} (${user.uid})")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error parsing user: ${userDoc.id}", e)
                                            }
                                        }
                                    }
                                }
                                
                                Log.d(TAG, "Total users found: ${users.size}")
                                trySend(users)
                            }
                        } else {
                            trySend(emptyList())
                        }
                    } else {
                        Log.w(TAG, "Shopping list is null")
                        trySend(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing shopping list", e)
                    trySend(emptyList())
                }
            } else {
                Log.w(TAG, "Shopping list snapshot is null or doesn't exist")
                trySend(emptyList())
            }
        }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    suspend fun assignItemToUser(itemId: String, userId: String, userName: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "assignedTo" to userId,
                "assignedToName" to userName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            db.collection(ITEMS_COLLECTION)
                .document(itemId)
                .update(updates)
                .await()
            
            Log.d(TAG, "Item assigned: $itemId to $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error assigning item", e)
            Result.failure(e)
        }
    }
    
    suspend fun markItemBought(itemId: String, actualPrice: Float? = null): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "isBought" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            if (actualPrice != null) {
                updates["actualPrice"] = actualPrice
            }
            
            db.collection(ITEMS_COLLECTION)
                .document(itemId)
                .update(updates)
                .await()
            
            Log.d(TAG, "Item marked as bought: $itemId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking item as bought", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateShoppingItem(itemId: String, updates: Map<String, Any?>): Result<Unit> {
        return try {
            val updatesWithTimestamp = updates.toMutableMap()
            updatesWithTimestamp["updatedAt"] = FieldValue.serverTimestamp()
            
            db.collection(ITEMS_COLLECTION)
                .document(itemId)
                .update(updatesWithTimestamp)
                .await()
            
            Log.d(TAG, "Shopping item updated: $itemId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating shopping item", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteShoppingItem(itemId: String): Result<Unit> {
        return try {
            db.collection(ITEMS_COLLECTION)
                .document(itemId)
                .delete()
                .await()
            
            Log.d(TAG, "Shopping item deleted: $itemId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting shopping item", e)
            Result.failure(e)
        }
    }
    
    suspend fun joinShoppingListByToken(listId: String, token: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val listRef = db.collection(LISTS_COLLECTION).document(listId)
            val listSnapshot = listRef.get().await()
            
            if (!listSnapshot.exists()) {
                return Result.failure(Exception("Shopping list not found"))
            }
            
            val shoppingList = listSnapshot.toObject(ShoppingList::class.java)
                ?: return Result.failure(Exception("Error parsing shopping list"))
            
            if (shoppingList.inviteToken != token) {
                return Result.failure(Exception("Invalid invite token"))
            }
            
            if (shoppingList.members.contains(currentUser.uid) || shoppingList.ownerUid == currentUser.uid) {
                return Result.failure(Exception("Already a member of this list"))
            }
            
            // Додаємо користувача до списку
            listRef.update("members", FieldValue.arrayUnion(currentUser.uid)).await()
            
            Log.d(TAG, "User ${currentUser.uid} joined shopping list: $listId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining shopping list", e)
            Result.failure(e)
        }
    }
    
    suspend fun addShoppingItem(item: ShoppingItem): Result<String> {
        return try {
            Log.d(TAG, "Adding shopping item: ${item.name} to list: ${item.listId}")
            val itemRef = db.collection(ITEMS_COLLECTION).document()
            val itemWithId = item.copy(id = itemRef.id)
            
            Log.d(TAG, "Item data to save: ${itemWithId.toMap()}")
            itemRef.set(itemWithId.toMap()).await()
            
            Log.d(TAG, "Shopping item added successfully: ${itemRef.id}")
            Result.success(itemRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding shopping item", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateShoppingItem(item: ShoppingItem): Result<Unit> {
        return try {
            val updates = item.toMap().toMutableMap()
            updates["updatedAt"] = FieldValue.serverTimestamp()
            
            db.collection(ITEMS_COLLECTION)
                .document(item.id)
                .update(updates)
                .await()
            
            Log.d(TAG, "Shopping item updated: ${item.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating shopping item", e)
            Result.failure(e)
        }
    }

    suspend fun updateShoppingList(shoppingList: ShoppingList): Result<Unit> {
        return try {
            val updates = shoppingList.toMap().toMutableMap()
            updates["updatedAt"] = FieldValue.serverTimestamp()
            
            db.collection(LISTS_COLLECTION)
                .document(shoppingList.id)
                .update(updates)
                .await()
            
            Log.d(TAG, "Shopping list updated: ${shoppingList.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating shopping list", e)
            Result.failure(e)
        }
    }
}
