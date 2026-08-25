package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocabulary",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("deckId")]
)
data class VocabularyEntity(
    @PrimaryKey val id: String,
    val japanese: String,
    val reading: String,
    val meaningEs: String,
    val deckId: String? = null,
    val createdAt: Long,
    val lastReviewed: Long? = null,
    val interval: Int = 1,
    val easeFactor: Double = 2.5,
    val reviewCount: Int = 0
)
