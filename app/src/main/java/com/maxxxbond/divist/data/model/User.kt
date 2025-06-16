package com.maxxxbond.divist.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    // Конструктор без параметрів для Firestore
    constructor() : this("", "", "", "", null, null)
    
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "displayName" to displayName,
        "email" to email,
        "phoneNumber" to phoneNumber,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
