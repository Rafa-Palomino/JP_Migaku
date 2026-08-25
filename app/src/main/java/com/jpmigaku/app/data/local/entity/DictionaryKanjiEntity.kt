package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_kanji",
    indices = [
        Index(value = ["meanings"]),
        Index(value = ["jlptLevel"])
    ]
)
data class DictionaryKanjiEntity(
    @PrimaryKey val character: String,
    val meanings: String,
    val onyomi: String,
    val kunyomi: String,
    val jlptLevel: String?
)
