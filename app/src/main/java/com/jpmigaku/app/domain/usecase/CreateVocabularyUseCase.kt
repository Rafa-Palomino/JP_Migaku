package com.jpmigaku.app.domain.usecase

import com.jpmigaku.app.data.repository.VocabularyRepository
import com.jpmigaku.app.domain.model.VocabularyEntry
import java.util.UUID
import javax.inject.Inject

class CreateVocabularyUseCase @Inject constructor(
    private val repository: VocabularyRepository
) {
    suspend operator fun invoke(
        japanese: String,
        reading: String,
        meaningEs: String,
        deckId: String? = null
    ): VocabularyEntry {
        val normalizedJapanese = japanese.trim()
        val normalizedReading = reading.trim()
        val normalizedMeaning = meaningEs.trim()

        require(normalizedJapanese.isNotEmpty()) { "La palabra japonesa no puede estar vacía" }
        require(normalizedMeaning.isNotEmpty()) { "El significado no puede estar vacío" }

        val entry = VocabularyEntry(
            id = UUID.randomUUID().toString(),
            japanese = normalizedJapanese,
            reading = normalizedReading.ifEmpty { normalizedJapanese },
            meaningEs = normalizedMeaning,
            createdAt = System.currentTimeMillis(),
            deckId = deckId
        )

        repository.save(entry)
        return entry
    }
}
