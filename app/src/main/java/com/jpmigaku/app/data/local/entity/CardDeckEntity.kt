package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "card_decks",
    primaryKeys = ["cardId", "deckId"],
    foreignKeys = [
        ForeignKey(
            entity = StudyCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId"), Index("deckId")]
)
data class CardDeckEntity(
    val cardId: String,
    val deckId: String,
    val addedAt: Long = System.currentTimeMillis()
)
