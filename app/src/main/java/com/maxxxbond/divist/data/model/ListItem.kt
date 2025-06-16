package com.maxxxbond.divist.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class ShoppingItem(
    var id: String = "",
    var listId: String = "",
    var name: String = "",
    var quantity: Int = 1,
    var estimatedPrice: Float = 0f,
    var actualPrice: Float? = null,
    var assignedTo: String? = null,
    var assignedToName: String? = null,
    @get:PropertyName("isBought") @set:PropertyName("isBought")
    var isBought: Boolean = false,
    var createdBy: String = "",
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
) {
    // Конструктор без параметрів для Firestore
    constructor() : this("", "", "", 1, 0f, null, null, null, false, "", null, null)
    
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "listId" to listId,
        "name" to name,
        "quantity" to quantity,
        "estimatedPrice" to estimatedPrice,
        "actualPrice" to actualPrice,
        "assignedTo" to assignedTo,
        "assignedToName" to assignedToName,
        "isBought" to isBought,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
    
    fun canAssign(currentUserUid: String, listRules: ListRules, isOwner: Boolean): Boolean {
        return when {
            listRules.onlyHostAssign -> isOwner
            listRules.selfAssign -> true
            else -> isOwner
        }
    }
    
    fun canEdit(currentUserUid: String, listOwnerUid: String): Boolean {
        return currentUserUid == createdBy || currentUserUid == listOwnerUid
    }
    
    fun canMarkBought(currentUserUid: String): Boolean {
        return assignedTo == currentUserUid || assignedTo == null
    }
}
