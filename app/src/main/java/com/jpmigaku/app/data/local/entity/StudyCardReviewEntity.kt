package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "study_card_reviews",
    foreignKeys = [
        ForeignKey(
            entity = StudyCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StudyCardReviewEntity(
    @androidx.room.PrimaryKey val cardId: String,
    val attempts: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCorrectAt: Long? = null,
    val lastIncorrectAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val intervalDays: Int = 1,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val dueAt: Long? = null
)
