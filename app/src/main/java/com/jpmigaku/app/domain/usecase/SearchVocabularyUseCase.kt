package com.jpmigaku.app.domain.usecase

import com.jpmigaku.app.data.repository.VocabularyRepository
import com.jpmigaku.app.domain.model.VocabularyEntry
import javax.inject.Inject

class SearchVocabularyUseCase @Inject constructor(
    private val repository: VocabularyRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 50): List<VocabularyEntry> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        return repository.search(normalizedQuery, limit)
    }
}
