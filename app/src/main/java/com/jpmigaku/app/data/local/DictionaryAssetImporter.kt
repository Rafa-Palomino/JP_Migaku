package com.jpmigaku.app.data.local

import android.content.Context
import android.util.JsonReader
import androidx.room.withTransaction
import com.jpmigaku.app.data.local.dao.DictionaryKanjiDao
import com.jpmigaku.app.data.local.dao.DictionaryVocabularyDao
import com.jpmigaku.app.data.local.dao.JlptClassificationDao
import com.jpmigaku.app.data.local.entity.DictionaryKanjiEntity
import com.jpmigaku.app.data.local.entity.DictionaryVocabularyEntity
import com.jpmigaku.app.data.local.entity.JlptClassificationEntity
import com.jpmigaku.app.domain.util.toRomaji
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DictionaryImportResult(
    val vocabularyEntries: Int,
    val kanjiEntries: Int,
    val jlptClassifications: Int,
    val skipped: Boolean
)

@Singleton
class DictionaryAssetImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: JPMigakuDatabase
) {
    suspend fun importIfNeeded(): DictionaryImportResult = withContext(Dispatchers.IO) {
        val vocabularyDao = database.dictionaryVocabularyDao()
        val kanjiDao = database.dictionaryKanjiDao()
        val jlptDao = database.jlptClassificationDao()
        val existingVocabulary = vocabularyDao.count()
        val existingKanji = kanjiDao.count()
        val existingJlpt = jlptDao.count()

        if (existingVocabulary > 0 && existingKanji > 0 && existingJlpt > 0) {
            return@withContext DictionaryImportResult(
                existingVocabulary,
                existingKanji,
                existingJlpt,
                skipped = true
            )
        }

        database.withTransaction {
            val vocabularyCount = if (existingVocabulary > 0 && existingKanji > 0) {
                existingVocabulary
            } else {
                vocabularyDao.deleteAll()
                kanjiDao.deleteAll()
                importVocabulary(vocabularyDao)
            }
            val kanjiCount = if (existingVocabulary > 0 && existingKanji > 0) {
                existingKanji
            } else {
                importKanji(kanjiDao)
            }
            val jlptCount = if (existingJlpt > 0) {
                existingJlpt
            } else {
                jlptDao.deleteAll()
                importJlpt(jlptDao)
            }
            DictionaryImportResult(vocabularyCount, kanjiCount, jlptCount, skipped = false)
        }
    }

    private suspend fun importJlpt(dao: JlptClassificationDao): Int {
        var imported = 0
        val batch = ArrayList<JlptClassificationEntity>(BATCH_SIZE)
        readJlptAsset { entry ->
            batch += entry
            if (batch.size == BATCH_SIZE) {
                dao.insertAll(batch)
                imported += batch.size
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            imported += batch.size
        }
        check(imported > 0) { "El asset JLPT no contiene clasificaciones válidas" }
        return imported
    }

    private suspend fun importVocabulary(dao: DictionaryVocabularyDao): Int {
        val spanishById = readVocabularyAsset(SPANISH_VOCABULARY_ASSET)
        val importedIds = HashSet<String>(spanishById.size)
        val batch = ArrayList<DictionaryVocabularyEntity>(BATCH_SIZE)
        var imported = 0

        readVocabularyAsset(ENGLISH_VOCABULARY_ASSET) { englishEntry ->
            val spanishEntry = spanishById[englishEntry.id]
            batch += englishEntry.toEntity(
                spanishGlosses = spanishEntry?.glosses.orEmpty()
            )
            importedIds += englishEntry.id
            if (batch.size == BATCH_SIZE) {
                dao.insertAll(batch)
                imported += batch.size
                batch.clear()
            }
        }

        spanishById.values
            .asSequence()
            .filterNot { it.id in importedIds }
            .forEach { spanishEntry ->
                batch += spanishEntry.toEntity(spanishGlosses = spanishEntry.glosses)
                if (batch.size == BATCH_SIZE) {
                    dao.insertAll(batch)
                    imported += batch.size
                    batch.clear()
                }
            }

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            imported += batch.size
        }
        check(imported > 0) { "Los assets JMdict no contienen entradas válidas" }
        return imported
    }

    private suspend fun importKanji(dao: DictionaryKanjiDao): Int {
        val batch = ArrayList<DictionaryKanjiEntity>(BATCH_SIZE)
        var imported = 0

        readKanjiAsset(KANJI_ASSET) { entry ->
            batch += entry
            if (batch.size == BATCH_SIZE) {
                dao.insertAll(batch)
                imported += batch.size
                batch.clear()
            }
        }

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            imported += batch.size
        }
        check(imported > 0) { "El asset KANJIDIC2 no contiene caracteres válidos" }
        return imported
    }

    private suspend fun readVocabularyAsset(assetName: String): Map<String, VocabularyRecord> {
        val entries = LinkedHashMap<String, VocabularyRecord>()
        readVocabularyAsset(assetName) { entry -> entries[entry.id] = entry }
        return entries
    }

    private suspend fun readVocabularyAsset(
        assetName: String,
        onEntry: suspend (VocabularyRecord) -> Unit
    ) {
        openJsonAsset(assetName).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "words" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                parseVocabularyRecord(reader)?.also { onEntry(it) }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
    }

    private fun parseVocabularyRecord(reader: JsonReader): VocabularyRecord? {
        var id: String? = null
        var japanese: String? = null
        var reading: String? = null
        val glosses = mutableListOf<String>()
        val partsOfSpeech = mutableListOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "kanji" -> {
                    val forms = readTextObjects(reader)
                    japanese = forms.firstOrNull()
                }
                "kana" -> {
                    val forms = readTextObjects(reader)
                    reading = forms.firstOrNull()
                    if (japanese == null) japanese = reading
                }
                "sense" -> readSenses(reader, glosses, partsOfSpeech)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val validId = id?.takeIf(String::isNotBlank)
        val validJapanese = japanese?.takeIf(String::isNotBlank)
        if (validId == null || validJapanese == null) return null
        return VocabularyRecord(
            id = validId,
            japanese = validJapanese,
            reading = reading,
            glosses = glosses.distinct(),
            partsOfSpeech = partsOfSpeech.distinct()
        )
    }

    private fun readTextObjects(reader: JsonReader): List<String> {
        val values = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "text" -> values += reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()
        return values
    }

    private fun readSenses(
        reader: JsonReader,
        glosses: MutableList<String>,
        partsOfSpeech: MutableList<String>
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "partOfSpeech" -> readStringArray(reader, partsOfSpeech)
                    "gloss" -> readGlosses(reader, glosses)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()
    }

    private fun readGlosses(reader: JsonReader, glosses: MutableList<String>) {
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var language: String? = null
            var text: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "lang" -> language = reader.nextString()
                    "text" -> text = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (language == null || language == "spa" || language == "eng") {
                text?.takeIf(String::isNotBlank)?.let { glosses += it }
            }
        }
        reader.endArray()
    }

    private fun readStringArray(reader: JsonReader, values: MutableList<String>) {
        reader.beginArray()
        while (reader.hasNext()) values += reader.nextString()
        reader.endArray()
    }

    private suspend fun readKanjiAsset(
        assetName: String,
        onEntry: suspend (DictionaryKanjiEntity) -> Unit
    ) {
        openJsonAsset(assetName).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "characters" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                parseKanjiRecord(reader)?.also { onEntry(it) }
                            }
                            reader.endArray()
                        }

                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
    }

    private suspend fun readJlptAsset(onEntry: suspend (JlptClassificationEntity) -> Unit) {
        openJsonAsset(JLPT_ASSET).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                var source = ""
                var vocabSourceVersion = ""
                var kanjiSourceVersion = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "metadata" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "source" -> source = reader.nextString()
                                    "source_version_vocab" -> vocabSourceVersion = reader.nextString()
                                    "source_version_kanji" -> kanjiSourceVersion = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }
                        "classifications" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                parseJlptRecord(
                                    reader,
                                    source,
                                    vocabSourceVersion,
                                    kanjiSourceVersion
                                )?.also { onEntry(it) }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
    }

    private fun parseJlptRecord(
        reader: JsonReader,
        source: String,
        vocabSourceVersion: String,
        kanjiSourceVersion: String
    ): JlptClassificationEntity? {
        var text: String? = null
        var reading = ""
        var kind: String? = null
        var level: String? = null
        var sequenceId = ""
        var sourceRetrieved = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "text" -> text = reader.nextString()
                "reading" -> if (reader.peek() == android.util.JsonToken.NULL) {
                    reader.nextNull()
                } else {
                    reading = reader.nextString()
                }
                "kind" -> kind = reader.nextString()
                "level" -> level = reader.nextString()
                "jmdict_seq" -> if (reader.peek() == android.util.JsonToken.NULL) {
                    reader.nextNull()
                } else {
                    sequenceId = reader.nextString()
                }
                "source_retrieved" -> sourceRetrieved = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val validText = text?.takeIf(String::isNotBlank)
        val validKind = kind?.takeIf { it == KIND_VOCABULARY || it == KIND_KANJI }
        val validLevel = level?.takeIf { it in JLPT_LEVELS }
        if (validText == null || validKind == null || validLevel == null) return null

        return JlptClassificationEntity(
            kind = validKind,
            canonicalId = if (validKind == KIND_KANJI) validText else sequenceId,
            japaneseText = validText,
            reading = reading,
            level = validLevel,
            source = source,
            sourceVersion = if (validKind == KIND_VOCABULARY) {
                vocabSourceVersion
            } else {
                kanjiSourceVersion
            },
            sourceRetrieved = sourceRetrieved
        )
    }

    private fun parseKanjiRecord(reader: JsonReader): DictionaryKanjiEntity? {
        var character: String? = null
        var jlptLevel: String? = null
        val spanishMeanings = mutableListOf<String>()
        val englishMeanings = mutableListOf<String>()
        val onyomi = mutableListOf<String>()
        val kunyomi = mutableListOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "literal" -> character = reader.nextString()
                "misc" -> readKanjiMisc(reader) { jlptLevel = it }
                "readingMeaning" -> readKanjiReadingsAndMeanings(
                    reader,
                    spanishMeanings,
                    englishMeanings,
                    onyomi,
                    kunyomi
                )
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val validCharacter = character?.takeIf(String::isNotBlank) ?: return null
        return DictionaryKanjiEntity(
            character = validCharacter,
            spanishMeanings = spanishMeanings.distinct().joinToString(LIST_SEPARATOR),
            englishMeanings = englishMeanings.distinct().joinToString(LIST_SEPARATOR),
            onyomi = onyomi.distinct().joinToString(LIST_SEPARATOR),
            kunyomi = kunyomi.distinct().joinToString(LIST_SEPARATOR),
            jlptLevel = jlptLevel
        )
    }

    private fun readKanjiMisc(reader: JsonReader, onJlpt: (String?) -> Unit) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "jlptLevel" -> {
                    if (reader.peek() == android.util.JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        onJlpt(reader.nextInt().toString())
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readKanjiReadingsAndMeanings(
        reader: JsonReader,
        spanishMeanings: MutableList<String>,
        englishMeanings: MutableList<String>,
        onyomi: MutableList<String>,
        kunyomi: MutableList<String>
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "groups" -> {
                    reader.beginArray()
                    while (reader.hasNext()) readKanjiGroup(
                        reader,
                        spanishMeanings,
                        englishMeanings,
                        onyomi,
                        kunyomi
                    )
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readKanjiGroup(
        reader: JsonReader,
        spanishMeanings: MutableList<String>,
        englishMeanings: MutableList<String>,
        onyomi: MutableList<String>,
        kunyomi: MutableList<String>
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "readings" -> readKanjiReadings(reader, onyomi, kunyomi)
                "meanings" -> readKanjiMeanings(reader, spanishMeanings, englishMeanings)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readKanjiReadings(
        reader: JsonReader,
        onyomi: MutableList<String>,
        kunyomi: MutableList<String>
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var type: String? = null
            var value: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> type = reader.nextString()
                    "value" -> value = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            when (type) {
                "ja_on" -> value?.let { onyomi += it }
                "ja_kun" -> value?.let { kunyomi += it }
            }
        }
        reader.endArray()
    }

    private fun readKanjiMeanings(
        reader: JsonReader,
        spanishMeanings: MutableList<String>,
        englishMeanings: MutableList<String>
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var language: String? = null
            var value: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "lang" -> language = reader.nextString()
                    "value" -> value = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            value?.takeIf(String::isNotBlank)?.let {
                when (language) {
                    "es" -> spanishMeanings += it
                    "en" -> englishMeanings += it
                }
            }
        }
        reader.endArray()
    }

    private fun openJsonAsset(assetName: String): InputStream =
        GZIPInputStream(context.assets.open(assetName))

    private data class VocabularyRecord(
        val id: String,
        val japanese: String,
        val reading: String?,
        val glosses: List<String>,
        val partsOfSpeech: List<String>
    ) {
        fun toEntity(spanishGlosses: List<String>): DictionaryVocabularyEntity =
            DictionaryVocabularyEntity(
                sequenceId = id,
                japanese = japanese,
                reading = reading,
                romaji = reading.orEmpty().toRomaji(),
                spanishGlosses = spanishGlosses.joinToString(LIST_SEPARATOR),
                englishGlosses = if (spanishGlosses === glosses) {
                    ""
                } else {
                    glosses.joinToString(LIST_SEPARATOR)
                },
                partsOfSpeech = partsOfSpeech.joinToString(LIST_SEPARATOR)
            )
    }

    private companion object {
        const val BATCH_SIZE = 500
        const val LIST_SEPARATOR = "\u001F"
        const val SPANISH_VOCABULARY_ASSET = "dictionaries/jmdict-spa-3.6.2.json.gz"
        const val ENGLISH_VOCABULARY_ASSET = "dictionaries/jmdict-eng-3.6.2.json.gz"
        const val KANJI_ASSET = "dictionaries/kanjidic2-all-3.6.2.json.gz"
        const val JLPT_ASSET = "dictionaries/jlpt-classifications.json.gz"
        const val KIND_VOCABULARY = "vocab"
        const val KIND_KANJI = "kanji"
        val JLPT_LEVELS = setOf("N5", "N4", "N3", "N2", "N1")
    }
}
