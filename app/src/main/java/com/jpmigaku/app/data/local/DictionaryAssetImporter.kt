package com.jpmigaku.app.data.local

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.jpmigaku.app.data.local.entity.JlptClassificationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Imports only the small, independently updateable JLPT classification catalog. */
@Singleton
class DictionaryAssetImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: JPMigakuDatabase
) {
    suspend fun importIfNeeded(): Int = withContext(Dispatchers.IO) {
        val dao = database.jlptClassificationDao()
        if (dao.count() > 0) return@withContext dao.count()
        val rows = mutableListOf<JlptClassificationEntity>()
        openAsset().use { input ->
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

    private fun openAsset() = context.assets.open("dictionaries/jlpt-classifications.json.gz").let {
        val input = BufferedInputStream(it)
        input.mark(2)
        val first = input.read(); val second = input.read(); input.reset()
        if (first == 0x1F && second == 0x8B) GZIPInputStream(input) else input
    }

    private companion object { val LEVELS = setOf("N5", "N4", "N3", "N2", "N1") }
}
