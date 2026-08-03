package com.jpmigaku.app.domain.usecase

import com.jpmigaku.app.data.repository.VocabularyRepository
import com.jpmigaku.app.domain.model.VocabularyEntry
import kotlin.math.roundToInt
import javax.inject.Inject

class ReviewVocabularyUseCase @Inject constructor(
    private val repository: VocabularyRepository
) {
    suspend operator fun invoke(entry: VocabularyEntry, wasCorrect: Boolean): VocabularyEntry {
        val newEaseFactor = if (wasCorrect) {
            (entry.easeFactor + 0.1).coerceAtLeast(1.3)
        } else {
            (entry.easeFactor - 0.2).coerceAtLeast(1.3)
        }

        val nextInterval = if (wasCorrect) {
            if (entry.interval <= 1) {
                2
            } else {
                (entry.interval * newEaseFactor).roundToInt().coerceAtLeast(1)
            }
        } else {
            1
        }

        val updated = entry.copy(
            lastReviewed = System.currentTimeMillis(),
            interval = nextInterval,
            easeFactor = newEaseFactor,
            reviewCount = entry.reviewCount + 1
        )

        repository.update(updated)
        return updated
    }
}
