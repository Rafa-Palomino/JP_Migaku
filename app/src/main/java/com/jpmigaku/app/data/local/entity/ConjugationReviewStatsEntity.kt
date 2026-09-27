package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "conjugation_review_stats",
    primaryKeys = ["conjugationFormId"],
    foreignKeys = [
        ForeignKey(
            entity = ConjugationFormEntity::class,
            parentColumns = ["id"],
            childColumns = ["conjugationFormId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ConjugationReviewStatsEntity(
    val conjugationFormId: String,
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
