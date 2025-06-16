package com.maxxxbond.divist.data.model

import com.google.firebase.Timestamp

data class ShoppingList(
    val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val members: List<String> = emptyList(),
    val inviteToken: String = "",
    val rules: ListRules = ListRules(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    // Конструктор без параметрів для Firestore
    constructor() : this("", "", "", emptyList(), "", ListRules(), null, null)
    
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "ownerUid" to ownerUid,
        "members" to members,
        "inviteToken" to inviteToken,
        "rules" to rules.toMap(),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
    
    fun isOwner(uid: String): Boolean = ownerUid == uid
    fun isMember(uid: String): Boolean = members.contains(uid)
}

data class ListRules(
    val selfAssign: Boolean = true,
    val onlyHostAssign: Boolean = false
) {
    // Конструктор без параметрів для Firestore
    constructor() : this(true, false)
    
    fun toMap(): Map<String, Any> = mapOf(
        "selfAssign" to selfAssign,
        "onlyHostAssign" to onlyHostAssign
    )
}
