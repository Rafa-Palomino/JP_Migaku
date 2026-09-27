package com.jpmigaku.app.domain.util

import com.jpmigaku.app.domain.model.GrammarClassification
import com.jpmigaku.app.domain.model.InflectionFormRule
import com.jpmigaku.app.domain.model.InflectionPatternRule
import com.jpmigaku.app.domain.model.InflectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseInflectionEngineTest {
    private val engine = JapaneseInflectionEngine()
    private val v1 = InflectionPatternRule("verb_v1", "VERB", "V1", "る", "v1")
    private val adjI = InflectionPatternRule("adj_i", "ADJECTIVE", "I", "い", "adj-i")
    private val adjNa = InflectionPatternRule("adj_na", "ADJECTIVE", "NA", "", "adj-na")
    private val forms = listOf(
        InflectionFormRule("DICTIONARY", "DICTIONARY", mapOf("verb_v1" to "る")),
        InflectionFormRule("NEGATIVE", "DICTIONARY", mapOf("verb_v1" to "ない")),
        InflectionFormRule("PAST", "DICTIONARY", mapOf("verb_v1" to "た"))
    )

    @Test
    fun buildsV1FormFromDictionaryEnding() {
        val result = engine.inflect(
            dictionaryForm = "食べる",
            classification = GrammarClassification("VERB", "V1", "v1"),
            formKey = "NEGATIVE",
            patterns = listOf(v1),
            forms = forms
        )

        assertEquals("食べない", (result as InflectionResult.Success).text)
    }

    @Test
    fun keepsDictionaryFormUnchanged() {
        val result = engine.inflect(
            dictionaryForm = "食べる",
            classification = GrammarClassification("VERB", "V1", "v1"),
            formKey = "DICTIONARY",
            patterns = listOf(v1),
            forms = forms
        )

        assertEquals("食べる", (result as InflectionResult.Success).text)
    }

    @Test
    fun resolvesVerbAndAdjectiveJmdictTags() {
        val classifications = engine.resolveClassifications(listOf("v1", "adj-i"))

        assertTrue(classifications.contains(GrammarClassification("VERB", "V1", "v1")))
        assertTrue(classifications.contains(GrammarClassification("ADJECTIVE", "I", "adj-i")))
    }

    @Test
    fun rejectsUnexpectedDictionaryEnding() {
        val result = engine.inflect(
            dictionaryForm = "飲む",
            classification = GrammarClassification("VERB", "V1", "v1"),
            formKey = "PAST",
            patterns = listOf(v1),
            forms = forms
        )

        assertTrue(result is InflectionResult.Unavailable)
    }

    @Test
    fun inflectsIAdjective() {
        val result = engine.inflect(
            dictionaryForm = "高い",
            classification = GrammarClassification("ADJECTIVE", "I", "adj-i"),
            formKey = "NEGATIVE",
            patterns = listOf(adjI),
            forms = listOf(
                InflectionFormRule("DICTIONARY", "DICTIONARY", mapOf("adj_i" to "い")),
                InflectionFormRule("NEGATIVE", "DICTIONARY", mapOf("adj_i" to "くない"))
            )
        )

        assertEquals("高くない", (result as InflectionResult.Success).text)
    }

    @Test
    fun inflectsNaAdjectiveWithoutDictionarySuffix() {
        val result = engine.inflect(
            dictionaryForm = "静か",
            classification = GrammarClassification("ADJECTIVE", "NA", "adj-na"),
            formKey = "PAST",
            patterns = listOf(adjNa),
            forms = listOf(
                InflectionFormRule("DICTIONARY", "DICTIONARY", mapOf("adj_na" to "")),
                InflectionFormRule("PAST", "DICTIONARY", mapOf("adj_na" to "だった"))
            )
        )

        assertEquals("静かだった", (result as InflectionResult.Success).text)
    }
}
