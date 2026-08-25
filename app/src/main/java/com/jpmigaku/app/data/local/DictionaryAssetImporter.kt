package com.jpmigaku.app.data.local

import android.content.Context
import android.util.Xml
import androidx.room.withTransaction
import com.jpmigaku.app.data.local.entity.DictionaryKanjiEntity
import com.jpmigaku.app.data.local.entity.DictionaryVocabularyEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DictionaryImportResult(
    val vocabularyEntries: Int,
    val kanjiEntries: Int,
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
        val existingVocabulary = vocabularyDao.count()
        val existingKanji = kanjiDao.count()

        if (existingVocabulary > 0 && existingKanji > 0) {
            return@withContext DictionaryImportResult(existingVocabulary, existingKanji, skipped = true)
        }

        database.withTransaction {
            vocabularyDao.deleteAll()
            kanjiDao.deleteAll()
            val vocabularyCount = importVocabulary(vocabularyDao)
            val kanjiCount = importKanji(kanjiDao)
            DictionaryImportResult(vocabularyCount, kanjiCount, skipped = false)
        }
    }

    private fun importVocabulary(
        dao: com.jpmigaku.app.data.local.dao.DictionaryVocabularyDao
    ): Int {
        val batch = ArrayList<DictionaryVocabularyEntity>(BATCH_SIZE)
        var imported = 0
        openDictionaryAsset(JMDICT_ASSET).use { input ->
            val parser = Xml.newPullParser().apply {
                setInput(input, XML_ENCODING)
            }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "entry") {
                    parseVocabularyEntry(parser)?.let {
                        batch += it
                        if (batch.size == BATCH_SIZE) {
                            dao.insertAll(batch)
                            imported += batch.size
                            batch.clear()
                        }
                    }
                }
            }
        }
        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            imported += batch.size
        }
        check(imported > 0) { "El asset $JMDICT_ASSET no contiene entradas válidas" }
        return imported
    }

    private fun importKanji(
        dao: com.jpmigaku.app.data.local.dao.DictionaryKanjiDao
    ): Int {
        val batch = ArrayList<DictionaryKanjiEntity>(BATCH_SIZE)
        var imported = 0
        openDictionaryAsset(KANJIDIC_ASSET).use { input ->
            val parser = Xml.newPullParser().apply {
                setInput(input, XML_ENCODING)
            }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "character") {
                    parseKanjiEntry(parser)?.let {
                        batch += it
                        if (batch.size == BATCH_SIZE) {
                            dao.insertAll(batch)
                            imported += batch.size
                            batch.clear()
                        }
                    }
                }
            }
        }
        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            imported += batch.size
        }
        check(imported > 0) { "El asset $KANJIDIC_ASSET no contiene entradas válidas" }
        return imported
    }

    private fun parseVocabularyEntry(parser: XmlPullParser): DictionaryVocabularyEntity? {
        var sequenceId: String? = null
        val kanji = mutableListOf<String>()
        val readings = mutableListOf<String>()
        val glosses = mutableListOf<String>()
        val partsOfSpeech = mutableListOf<String>()
        val depth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG &&
                parser.name == "entry" &&
                parser.depth == depth
            ) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "ent_seq" -> sequenceId = parser.readText()
                "keb" -> kanji += parser.readText()
                "reb" -> readings += parser.readText()
                "gloss" -> glosses += parser.readText()
                "pos" -> partsOfSpeech += parser.readText()
            }
        }

        val id = sequenceId?.takeIf(String::isNotBlank)
        val japanese = (kanji.firstOrNull() ?: readings.firstOrNull()).orEmpty()
        if (id == null || japanese.isBlank() || glosses.isEmpty()) return null
        return DictionaryVocabularyEntity(
            sequenceId = id,
            japanese = japanese,
            reading = readings.firstOrNull(),
            glosses = glosses.distinct().joinToString(GLOSS_SEPARATOR),
            partsOfSpeech = partsOfSpeech.distinct().joinToString(GLOSS_SEPARATOR)
        )
    }

    private fun parseKanjiEntry(parser: XmlPullParser): DictionaryKanjiEntity? {
        val depth = parser.depth
        var character: String? = null
        val meanings = mutableListOf<String>()
        val onyomi = mutableListOf<String>()
        val kunyomi = mutableListOf<String>()
        var jlptLevel: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG &&
                parser.name == "character" &&
                parser.depth == depth
            ) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "literal" -> character = parser.readText()
                "reading" -> {
                    val reading = parser.readText()
                    when (parser.getAttributeValue(null, "r_type")) {
                        "ja_on" -> onyomi += reading
                        "ja_kun" -> kunyomi += reading
                    }
                }
                "meaning" -> {
                    val language = parser.getAttributeValue(null, "m_lang")
                    if (language == null || language == "en") {
                        meanings += parser.readText()
                    }
                }
                "jlpt" -> jlptLevel = parser.readText()
            }
        }

        val validCharacter = character?.takeIf(String::isNotBlank)
        if (validCharacter == null || meanings.isEmpty()) return null
        return DictionaryKanjiEntity(
            character = validCharacter,
            meanings = meanings.distinct().joinToString(GLOSS_SEPARATOR),
            onyomi = onyomi.distinct().joinToString(GLOSS_SEPARATOR),
            kunyomi = kunyomi.distinct().joinToString(GLOSS_SEPARATOR),
            jlptLevel = jlptLevel
        )
    }

    private fun openDictionaryAsset(assetName: String) =
        GZIPInputStream(context.assets.open(assetName))

    private fun XmlPullParser.readText(): String {
        require(next() == XmlPullParser.TEXT) { "Se esperaba texto en <$name>" }
        return text.trim()
    }

    private companion object {
        const val BATCH_SIZE = 500
        const val XML_ENCODING = "UTF-8"
        const val JMDICT_ASSET = "dictionaries/JMdict.gz"
        const val KANJIDIC_ASSET = "dictionaries/kanjidic2.xml.gz"
        const val GLOSS_SEPARATOR = "\u001F"
    }
}
