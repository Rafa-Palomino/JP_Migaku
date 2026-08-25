package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "jlpt_classification",
    primaryKeys = ["kind", "canonicalId", "japaneseText", "reading", "level"],
    indices = [
        Index(value = ["kind", "level"]),
        Index(value = ["japaneseText", "reading"])
    ]
)
data class JlptClassificationEntity(
    val kind: String,
    val canonicalId: String,
    val japaneseText: String,
    val reading: String,
    val level: String,
    val source: String,
    val sourceVersion: String,
    val sourceRetrieved: String
)
