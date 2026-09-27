package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "conjugation_patterns",
    indices = [
        Index(value = ["wordClass", "conjugationType"], unique = true)
    ]
)
data class ConjugationPatternEntity(
    @androidx.room.PrimaryKey val id: String,
    val wordClass: String,
    val conjugationType: String
)
