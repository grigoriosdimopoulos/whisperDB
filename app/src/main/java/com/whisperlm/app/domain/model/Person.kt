package com.whisperlm.app.domain.model

data class Person(
    val id: Long = 0,
    val name: String,
    val age: Int? = null,
    val relationToUser: String = "Other",
    val notes: String = "",
    val profilePhotoPath: String? = null,
    val isUserSelf: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val voiceSampleCount: Int = 0
)
