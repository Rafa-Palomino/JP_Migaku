package com.jpmigaku.app.data.repository

import com.jpmigaku.app.data.local.dao.ConjugationDao
import com.jpmigaku.app.domain.model.ConjugationFormOption
import com.jpmigaku.app.domain.model.ConjugationQuizQuestion
import com.jpmigaku.app.domain.model.GrammarClassification
import com.jpmigaku.app.domain.model.InflectionFormRule
import com.jpmigaku.app.domain.model.InflectionPatternRule
import com.jpmigaku.app.domain.model.InflectionResult
import com.jpmigaku.app.domain.model.VocabularyEntry
import com.jpmigaku.app.domain.util.JapaneseInflectionEngine
import javax.inject.Inject

interface ConjugationRepository {
    suspend fun getSelectionForms(): List<ConjugationFormOption>
    suspend fun isEligibleEntry(
        entry: VocabularyEntry,
        selectedFormKeys: Set<String>
    ): Boolean
    suspend fun createStudyQuestion(
        entry: VocabularyEntry,
        selectedFormKeys: Set<String>
    ): ConjugationQuizQuestion?
}

class RoomConjugationRepository @Inject constructor(
    private val dao: ConjugationDao,
    private val dictionaryVocabularyRepository: DictionaryVocabularyRepository,
    private val engine: JapaneseInflectionEngine
) : ConjugationRepository {
    override suspend fun getSelectionForms(): List<ConjugationFormOption> {
        val patterns = dao.getPatterns().associateBy { it.id }
        return dao.getForms()
            .mapNotNull { form ->
                val pattern = patterns[form.patternId] ?: return@mapNotNull null
                ConjugationFormOption(
                    selectionKey = selectionKey(pattern.wordClass, form.formKey),
                    wordClass = pattern.wordClass,
                    formKey = form.formKey,
                    displayName = form.displayName,
                    whenTo = form.whenTo,
                    howTo = form.howTo
                )
            }

            .distinctBy { it.selectionKey }
            .sortedWith(compareBy({ it.wordClass }, { it.formKey }))
    }

    override suspend fun isEligibleEntry(
        entry: VocabularyEntry,
        selectedFormKeys: Set<String>
    ): Boolean {
        if (!entry.sourceProvider.equals(JMDICT_PROVIDER, ignoreCase = true)) return false
        val dictionaryEntry = dictionaryVocabularyRepository.findBySequenceId(entry.sourceKey)
            ?: return false
        return engine.resolveClassifications(dictionaryEntry.partsOfSpeech).any { classification ->
            selectedFormKeys.contains(selectionKey(classification.wordClass, DICTIONARY_FORM))
        }
    }

    override suspend fun createStudyQuestion(
        entry: VocabularyEntry,
        selectedFormKeys: Set<String>
    ): ConjugationQuizQuestion? {
        if (!entry.sourceProvider.equals(JMDICT_PROVIDER, ignoreCase = true)) return null
        val dictionaryEntry = dictionaryVocabularyRepository.findBySequenceId(entry.sourceKey)
            ?: return null
        val classifications = engine.resolveClassifications(dictionaryEntry.partsOfSpeech)
        val classification = classifications.firstOrNull { candidate ->
            selectedFormKeys.any { it == selectionKey(candidate.wordClass, DICTIONARY_FORM) }
        } ?: return null
        val pattern = dao.getPatterns().firstOrNull {
            it.wordClass.equals(classification.wordClass, ignoreCase = true) &&
                it.conjugationType.equals(classification.type, ignoreCase = true)
        } ?: return null
        val forms = dao.getForms(pattern.id)
        val selectedForm = forms
            .filter { selectionKey(pattern.wordClass, it.formKey) in selectedFormKeys }
            .randomOrNull() ?: return null
        val dictionaryForm = forms.firstOrNull { it.formKey == DICTIONARY_FORM } ?: return null
        val rules = forms.map {
            InflectionFormRule(
                formKey = it.formKey,
                sourceFormKey = it.sourceFormKey,
                endingsByPatternId = mapOf(pattern.id to it.compositionJson)
            )
        }
        val result = engine.inflect(
            dictionaryForm = dictionaryEntry.japanese,
            classification = GrammarClassification(pattern.wordClass, pattern.conjugationType, classification.sourceTag),
            formKey = selectedForm.formKey,
            patterns = listOf(
                InflectionPatternRule(
                    id = pattern.id,
                    wordClass = pattern.wordClass,
                    type = pattern.conjugationType,
                    dictionaryEnding = dictionaryForm.compositionJson,
                    sourceTag = classification.sourceTag
                )
            ),
            forms = rules
        )
        return (result as? InflectionResult.Success)?.let {
            ConjugationQuizQuestion(entry, it.text, selectedForm.displayName, classification.sourceTag)
        }
    }

    private fun selectionKey(wordClass: String, formKey: String): String =
        "$wordClass:$formKey"

    private companion object {
        const val DICTIONARY_FORM = "DICTIONARY"
        const val JMDICT_PROVIDER = "jmdict"
    }
}
