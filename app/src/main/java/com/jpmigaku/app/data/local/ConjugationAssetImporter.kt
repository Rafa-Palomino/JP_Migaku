package com.jpmigaku.app.data.local

import android.content.Context
import androidx.room.withTransaction
import com.jpmigaku.app.data.local.dao.ConjugationDao
import com.jpmigaku.app.data.local.entity.ConjugationFormEntity
import com.jpmigaku.app.data.local.entity.ConjugationPatternEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConjugationAssetImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: JPMigakuDatabase
) {
    suspend fun importIfNeeded(): Int = withContext(Dispatchers.IO) {
        val dao = database.conjugationDao()
        val existingForms = dao.getForms()
        if (dao.getPatterns().isNotEmpty() && existingForms.none { it.whenTo.isBlank() || it.howTo.isBlank() }) {
            return@withContext existingForms.size
        }

        val patterns = readPatterns()
        val forms = readForms(patterns, readCards())
        database.withTransaction {
            dao.insertPatterns(patterns)
            dao.insertForms(forms)
        }
        forms.size
    }

    private fun readPatterns(): List<ConjugationPatternEntity> =
        context.assets.open(PATTERNS_ASSET).bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val fields = line.split(',')
                require(fields.size == 3) { "Invalid conjugation pattern row: $line" }
                ConjugationPatternEntity(fields[0], fields[1], fields[2])
            }.toList()
        }

    private fun readForms(
        patterns: List<ConjugationPatternEntity>,
        cards: Map<String, CardText>
    ): List<ConjugationFormEntity> =
        context.assets.open(FORMS_ASSET).bufferedReader().useLines { lines ->
            val rows = lines.toList()
            val headers = rows.first().split(',')
            val patternIds = patterns.map { it.id }.toSet()
            rows.drop(1).filter(String::isNotBlank).flatMap { line ->
                val fields = line.split(',')
                require(fields.size == headers.size) { "Invalid conjugation form row: $line" }
                val formId = fields[0]
                val formKey = fields[2]
                val displayName = fields[3]
                patternIds.mapNotNull { patternId ->
                    val ending = fields[headers.indexOf(patternColumn(patternId))]
                    if (ending.isEmpty() && formKey != "DICTIONARY") null else ConjugationFormEntity(
                        id = "${patternId}_${formId}",
                        patternId = patternId,
                        formKey = formKey,
                        displayName = displayName,
                        compositionJson = ending,
                        whenTo = cards.getValue(cardKey(topicId(patternId), displayName)).whenTo,
                        howTo = cards.getValue(cardKey(topicId(patternId), displayName)).howTo
                    )
                }
            }.toList()
        }

    private fun readCards(): Map<String, CardText> =
        context.assets.open(CARDS_ASSET).bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val fields = parseCsvLine(line)
                require(fields.size == 4) { "Invalid conjugation card row: $line" }
                cardKey(fields[0], fields[1]) to CardText(fields[2], fields[3])
            }.toMap()
        }

    private fun cardKey(patternId: String, displayName: String): String =
        "$patternId\u0000$displayName"

    private fun topicId(patternId: String): String =
        if (patternId.startsWith("verb_")) "verb" else "adjective"

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val character = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    quoted = !quoted
                }
                ',' -> if (quoted) current.append(character) else {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
            index++
        }
        fields += current.toString()
        return fields
    }

    private fun patternColumn(patternId: String): String =
        when {
            patternId.startsWith("adj_") -> patternId
            patternId.startsWith("verb_") -> patternId
            else -> error("Unsupported conjugation pattern: $patternId")
        }

    private companion object {
        const val PATTERNS_ASSET = "conjugations/conjugation_patterns.csv"
        const val FORMS_ASSET = "conjugations/conjugation_forms.csv"
        const val CARDS_ASSET = "conjugations/conjugation_card.csv"
    }

    private data class CardText(val whenTo: String, val howTo: String)
}
