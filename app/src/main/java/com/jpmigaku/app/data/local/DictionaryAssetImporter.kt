package com.jpmigaku.app.data.local

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.room.withTransaction
import com.jpmigaku.app.data.local.dao.DictionaryKanjiDao
import com.jpmigaku.app.data.local.dao.DictionaryVocabularyDao
import com.jpmigaku.app.data.local.entity.DictionaryKanjiEntity
import com.jpmigaku.app.data.local.entity.DictionaryVocabularyEntity
import com.jpmigaku.app.data.local.entity.JlptClassificationEntity
import com.jpmigaku.app.domain.util.toRomaji
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryAssetImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: JPMigakuDatabase,
    private val dictionaryCatalogDatabase: DictionaryCatalogDatabase
) {
    suspend fun importIfNeeded(): Int = withContext(Dispatchers.IO) {
        importCatalogIfNeeded()
        val dao = database.jlptClassificationDao()
        if (dao.count() > 0) return@withContext dao.count()
        val rows = mutableListOf<JlptClassificationEntity>()
        openJsonAsset(JLPT_ASSET).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                var source = ""
                var vocabVersion = ""
                var kanjiVersion = ""
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "metadata" -> {
                            reader.beginObject()
                            while (reader.hasNext()) when (reader.nextName()) {
                                "source" -> source = reader.nextString()
                                "source_version_vocab" -> vocabVersion = reader.nextString()
                                "source_version_kanji" -> kanjiVersion = reader.nextString()
                                else -> reader.skipValue()
                            }
                            reader.endObject()
                        }
                        "classifications" -> {
                            reader.beginArray()
                            while (reader.hasNext()) parseRow(reader, source, vocabVersion, kanjiVersion)?.let(rows::add)
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        dao.insertAll(rows)
        rows.size
    }

    private suspend fun importCatalogIfNeeded() {
        val vocabularyDao = dictionaryCatalogDatabase.dictionaryVocabularyDao()
        val kanjiDao = dictionaryCatalogDatabase.dictionaryKanjiDao()
        if (vocabularyDao.count() > 0 && kanjiDao.count() > 0) return

        dictionaryCatalogDatabase.withTransaction {
            vocabularyDao.deleteAll()
            kanjiDao.deleteAll()
            importVocabulary(vocabularyDao)
            importKanji(kanjiDao)
        }
    }

    private suspend fun importVocabulary(dao: DictionaryVocabularyDao) {
        val spanishById = readVocabularyAsset(SPANISH_VOCABULARY_ASSET)
        val importedIds = HashSet<String>(spanishById.size)
        val batch = ArrayList<DictionaryVocabularyEntity>(BATCH_SIZE)

        readVocabularyAsset(ENGLISH_VOCABULARY_ASSET) { english ->
            batch += english.toEntity(spanishById[english.id]?.glosses.orEmpty())
            importedIds += english.id
            if (batch.size == BATCH_SIZE) {
                dao.insertAll(batch)
                batch.clear()
            }
        }
        spanishById.values.asSequence().filterNot { it.id in importedIds }.forEach { spanish ->
            batch += spanish.toEntity(spanish.glosses)
            if (batch.size == BATCH_SIZE) {
                dao.insertAll(batch)
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) dao.insertAll(batch)
    }

    private suspend fun importKanji(dao: DictionaryKanjiDao) {
        val batch = ArrayList<DictionaryKanjiEntity>(BATCH_SIZE)
        readKanjiAsset { entry ->
            batch += entry
            if (batch.size == BATCH_SIZE) {
                dao.insertAll(batch)
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) dao.insertAll(batch)
    }

    private fun parseRow(reader: JsonReader, source: String, vocabVersion: String, kanjiVersion: String): JlptClassificationEntity? {
        var text = ""; var reading = ""; var kind = ""; var level = ""; var id = ""; var retrieved = ""
        reader.beginObject()
        while (reader.hasNext()) when (reader.nextName()) {
            "text" -> text = reader.nextString()
            "reading" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else reading = reader.nextString()
            "kind" -> kind = reader.nextString()
            "level" -> level = reader.nextString()
            "jmdict_seq" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else id = reader.nextString()
            "source_retrieved" -> retrieved = reader.nextString()
            else -> reader.skipValue()
        }
        reader.endObject()
        if (text.isBlank() || kind !in setOf("vocab", "kanji") || level !in LEVELS) return null
        return JlptClassificationEntity(kind, if (kind == "kanji") text else id, text, reading, level, source, if (kind == "vocab") vocabVersion else kanjiVersion, retrieved)
    }

    private suspend fun readVocabularyAsset(assetName: String): Map<String, VocabularyRecord> {
        val entries = LinkedHashMap<String, VocabularyRecord>()
        readVocabularyAsset(assetName) { entries[it.id] = it }
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
                            while (reader.hasNext()) parseVocabulary(reader)?.let { onEntry(it) }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
    }

    private fun parseVocabulary(reader: JsonReader): VocabularyRecord? {
        var id = ""
        var japanese: String? = null
        var reading: String? = null
        val glosses = mutableListOf<String>()
        val partsOfSpeech = mutableListOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "kanji" -> japanese = readTextObjects(reader).firstOrNull()
                "kana" -> {
                    reading = readTextObjects(reader).firstOrNull()
                    if (japanese == null) japanese = reading
                }
                "sense" -> readSenses(reader, glosses, partsOfSpeech)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return japanese?.takeIf(String::isNotBlank)?.let {
            VocabularyRecord(id, it, reading, glosses.distinct(), partsOfSpeech.distinct())
        }?.takeIf { it.id.isNotBlank() }
    }

    private fun readTextObjects(reader: JsonReader): List<String> {
        val values = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "text") values += reader.nextString() else reader.skipValue()
            }
            reader.endObject()
        }
        reader.endArray()
        return values
    }

    private fun readSenses(reader: JsonReader, glosses: MutableList<String>, partsOfSpeech: MutableList<String>) {
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
                text?.takeIf(String::isNotBlank)?.let(glosses::add)
            }
        }
        reader.endArray()
    }

    private fun readStringArray(reader: JsonReader, values: MutableList<String>) {
        reader.beginArray()
        while (reader.hasNext()) values += reader.nextString()
        reader.endArray()
    }

    private suspend fun readKanjiAsset(onEntry: suspend (DictionaryKanjiEntity) -> Unit) {
        openJsonAsset(KANJI_ASSET).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "characters" -> {
                            reader.beginArray()
                            while (reader.hasNext()) parseKanji(reader)?.let { onEntry(it) }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
    }

    private fun parseKanji(reader: JsonReader): DictionaryKanjiEntity? {
        var character: String? = null
        val spanish = mutableListOf<String>()
        val english = mutableListOf<String>()
        val onyomi = mutableListOf<String>()
        val kunyomi = mutableListOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "literal" -> character = reader.nextString()
                "readingMeaning" -> if (reader.peek() == JsonToken.NULL) reader.nextNull()
                else readKanjiReadingsAndMeanings(reader, spanish, english, onyomi, kunyomi)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return character?.takeIf(String::isNotBlank)?.let {
            DictionaryKanjiEntity(
                character = it,
                spanishMeanings = spanish.distinct().joinToString(LIST_SEPARATOR),
                englishMeanings = english.distinct().joinToString(LIST_SEPARATOR),
                onyomi = onyomi.distinct().joinToString(LIST_SEPARATOR),
                kunyomi = kunyomi.distinct().joinToString(LIST_SEPARATOR),
                jlptLevel = null
            )
        }
    }

    private fun readKanjiReadingsAndMeanings(
        reader: JsonReader,
        spanish: MutableList<String>,
        english: MutableList<String>,
        onyomi: MutableList<String>,
        kunyomi: MutableList<String>
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "groups") {
                reader.skipValue()
                continue
            }
            reader.beginArray()
            while (reader.hasNext()) readKanjiGroup(reader, spanish, english, onyomi, kunyomi)
            reader.endArray()
        }
        reader.endObject()
    }

    private fun readKanjiGroup(
        reader: JsonReader,
        spanish: MutableList<String>,
        english: MutableList<String>,
        onyomi: MutableList<String>,
        kunyomi: MutableList<String>
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "readings" -> readKanjiReadings(reader, onyomi, kunyomi)
                "meanings" -> readKanjiMeanings(reader, spanish, english)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readKanjiReadings(reader: JsonReader, onyomi: MutableList<String>, kunyomi: MutableList<String>) {
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
                "ja_on" -> value?.let(onyomi::add)
                "ja_kun" -> value?.let(kunyomi::add)
            }
        }
        reader.endArray()
    }

    private fun readKanjiMeanings(reader: JsonReader, spanish: MutableList<String>, english: MutableList<String>) {
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
                    "es" -> spanish += it
                    "en" -> english += it
                }
            }
        }
        reader.endArray()
    }

    private fun openJsonAsset(assetName: String): InputStream {
        val input = try {
            context.assets.open(assetName)
        } catch (error: IOException) {
            if (!assetName.endsWith(".gz")) throw error
            context.assets.open(assetName.removeSuffix(".gz"))
        }
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
    }

    private data class VocabularyRecord(
        val id: String,
        val japanese: String,
        val reading: String?,
        val glosses: List<String>,
        val partsOfSpeech: List<String>
    ) {
        fun toEntity(spanishGlosses: List<String>) = DictionaryVocabularyEntity(
            sequenceId = id,
            japanese = japanese,
            reading = reading,
            romaji = reading.orEmpty().toRomaji(),
            spanishGlosses = spanishGlosses.joinToString(LIST_SEPARATOR),
            englishGlosses = if (spanishGlosses === glosses) "" else glosses.joinToString(LIST_SEPARATOR),
            partsOfSpeech = partsOfSpeech.joinToString(LIST_SEPARATOR)
        )
    }

    private companion object {
        const val BATCH_SIZE = 500
        const val LIST_SEPARATOR = "\u001F"
        const val GZIP_MAGIC_FIRST = 0x1F
        const val GZIP_MAGIC_SECOND = 0x8B
        const val SPANISH_VOCABULARY_ASSET = "dictionaries/jmdict-spa-3.6.2.json.gz"
        const val ENGLISH_VOCABULARY_ASSET = "dictionaries/jmdict-eng-3.6.2.json.gz"
        const val KANJI_ASSET = "dictionaries/kanjidic2-all-3.6.2.json.gz"
        const val JLPT_ASSET = "dictionaries/jlpt-classifications.json.gz"
        val LEVELS = setOf("N5", "N4", "N3", "N2", "N1")
    }
}
