package com.jpmigaku.app.data.repository

import com.jpmigaku.app.data.local.dao.DictionaryKanjiDao
import com.jpmigaku.app.data.local.entity.DictionaryKanjiEntity
import com.jpmigaku.app.domain.model.DictionaryKanji
import javax.inject.Inject

interface DictionaryKanjiRepository {
    suspend fun search(query: String): List<DictionaryKanji>
}

class RoomDictionaryKanjiRepository @Inject constructor(
    private val dao: DictionaryKanjiDao
) : DictionaryKanjiRepository {
    override suspend fun search(query: String): List<DictionaryKanji> =
        dao.search(query.trim()).map { it.toDomain() }

    private fun DictionaryKanjiEntity.toDomain(): DictionaryKanji {
        val spanish = spanishMeanings.split(LIST_SEPARATOR).firstOrNull(String::isNotBlank)
        val english = englishMeanings.split(LIST_SEPARATOR).firstOrNull(String::isNotBlank)
        return DictionaryKanji(
            character = character,
            onyomi = onyomi,
            kunyomi = kunyomi,
            meaning = spanish ?: english.orEmpty(),
            isEnglishFallback = spanish == null && english != null
        )
    }

    private companion object {
        const val LIST_SEPARATOR = "\u001F"
    }
}
