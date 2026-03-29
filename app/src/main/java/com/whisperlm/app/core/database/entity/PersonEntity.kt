package com.whisperlm.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val age: Int? = null,
    @ColumnInfo(name = "relation_to_user")
    val relationToUser: String = "Other",
    val notes: String = "",
    @ColumnInfo(name = "profile_photo_path")
    val profilePhotoPath: String? = null,
    @ColumnInfo(name = "is_user_self")
    val isUserSelf: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
