package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_vocabulary",
    indices = [
        Index(value = ["japanese"]),
        Index(value = ["reading"]),
        Index(value = ["romaji"])
    ]
)
data class DictionaryVocabularyEntity(
    @PrimaryKey val sequenceId: String,
    val japanese: String,
    val reading: String?,
    val romaji: String,
    val spanishGlosses: String,
    val englishGlosses: String,
    val partsOfSpeech: String
)
