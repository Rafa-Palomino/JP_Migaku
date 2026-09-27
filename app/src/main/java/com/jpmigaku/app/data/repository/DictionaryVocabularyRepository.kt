package com.jpmigaku.app.data.repository

import com.jpmigaku.app.data.local.dao.DictionaryVocabularyDao
import com.jpmigaku.app.data.local.entity.DictionaryVocabularyEntity
import com.jpmigaku.app.domain.model.DictionaryVocabulary
import javax.inject.Inject

interface DictionaryVocabularyRepository {
    suspend fun search(query: String): List<DictionaryVocabulary>
    suspend fun findBySequenceId(sequenceId: String): DictionaryVocabulary?
    suspend fun findExact(japanese: String, reading: String): List<DictionaryVocabulary>
}

class RoomDictionaryVocabularyRepository @Inject constructor(
    private val dao: DictionaryVocabularyDao
) : DictionaryVocabularyRepository {
    override suspend fun search(query: String): List<DictionaryVocabulary> =
        dao.search(query.trim()).map { it.toDomain() }

    override suspend fun findBySequenceId(sequenceId: String): DictionaryVocabulary? =
        dao.findBySequenceId(sequenceId.trim())?.toDomain()

    override suspend fun findExact(
        japanese: String,
        reading: String
    ): List<DictionaryVocabulary> =
        dao.findExact(japanese.trim(), reading.trim()).map { it.toDomain() }

    private fun DictionaryVocabularyEntity.toDomain(): DictionaryVocabulary {
        val spanish = spanishGlosses.split(LIST_SEPARATOR).firstOrNull(String::isNotBlank)
        val english = englishGlosses.split(LIST_SEPARATOR).firstOrNull(String::isNotBlank)
        val meaning = spanish ?: english.orEmpty()
        return DictionaryVocabulary(
            sequenceId = sequenceId,
            japanese = japanese,
            reading = reading.orEmpty(),
            romaji = romaji,
            meaning = meaning,
            isEnglishFallback = spanish == null && english != null,
            partsOfSpeech = partsOfSpeech.split(LIST_SEPARATOR)
                .filter(String::isNotBlank)
                .distinct()
        )
    }

    private companion object {
        const val LIST_SEPARATOR = "\u001F"
    }
}
