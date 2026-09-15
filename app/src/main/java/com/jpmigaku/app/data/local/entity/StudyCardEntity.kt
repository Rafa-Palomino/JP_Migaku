package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "study_cards", indices = [Index(value = ["kind", "sourceProvider", "sourceKey"], unique = true)])
data class StudyCardEntity(
    @androidx.room.PrimaryKey val id: String,
    val kind: String,
    val sourceProvider: String,
    val sourceKey: String,
    val sourceVersion: String,
    val displayJapanese: String,
    val displayReading: String,
    val displayMeaning: String,
    val createdAt: Long,
    val displayRomaji: String = "",
    val jlptLevel: String = "",
    val sourceSnapshot: String? = null,
    val updatedAt: Long = createdAt,
    val suspended: Boolean = false
)
