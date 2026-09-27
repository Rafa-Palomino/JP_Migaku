package com.jpmigaku.app.domain.util

import com.jpmigaku.app.domain.model.GrammarClassification
import com.jpmigaku.app.domain.model.InflectionFormRule
import com.jpmigaku.app.domain.model.InflectionPatternRule
import com.jpmigaku.app.domain.model.InflectionResult

class JapaneseInflectionEngine {
    fun inflect(
        dictionaryForm: String,
        classification: GrammarClassification,
        formKey: String,
        patterns: List<InflectionPatternRule>,
        forms: List<InflectionFormRule>
    ): InflectionResult {
        val normalizedDictionaryForm = dictionaryForm.trim()
        if (normalizedDictionaryForm.isEmpty()) {
            return InflectionResult.Unavailable("La forma diccionario está vacía")
        }

        val pattern = patterns.firstOrNull {
            it.wordClass.equals(classification.wordClass, ignoreCase = true) &&
                it.type.equals(classification.type, ignoreCase = true) &&
                it.sourceTag.equals(classification.sourceTag, ignoreCase = true)
        } ?: return InflectionResult.Unavailable(
            "No existe un patrón para ${classification.wordClass}/${classification.type}"
        )

        val form = forms.firstOrNull { it.formKey == formKey }
            ?: return InflectionResult.Unavailable("No existe la forma $formKey")
        val dictionaryEnding = pattern.dictionaryEnding
        if (!normalizedDictionaryForm.endsWith(dictionaryEnding)) {
            return InflectionResult.Unavailable(
                "La forma diccionario no termina en la terminación esperada"
            )
        }

        if (form.formKey == form.sourceFormKey) {
            return InflectionResult.Success(normalizedDictionaryForm, pattern, form)
        }

        val ending = form.endingsByPatternId[pattern.id]
            ?: return InflectionResult.Unavailable(
                "La forma $formKey no tiene terminación para ${pattern.id}"
            )
        val base = normalizedDictionaryForm.removeSuffix(dictionaryEnding)
        return InflectionResult.Success(base + ending, pattern, form)
    }

    fun resolveClassifications(partsOfSpeech: List<String>): List<GrammarClassification> =
        partsOfSpeech.mapNotNull { tag ->
            val normalizedTag = tag.trim().lowercase()
            TAG_CLASSIFICATIONS[normalizedTag]
        }.distinct()

    private companion object {
        val TAG_CLASSIFICATIONS = mapOf(
            "v1" to GrammarClassification("VERB", "V1", "v1"),
            "v5u" to GrammarClassification("VERB", "V5U", "v5u"),
            "v5k" to GrammarClassification("VERB", "V5K", "v5k"),
            "v5g" to GrammarClassification("VERB", "V5G", "v5g"),
            "v5s" to GrammarClassification("VERB", "V5S", "v5s"),
            "v5t" to GrammarClassification("VERB", "V5T", "v5t"),
            "v5n" to GrammarClassification("VERB", "V5N", "v5n"),
            "v5b" to GrammarClassification("VERB", "V5B", "v5b"),
            "v5m" to GrammarClassification("VERB", "V5M", "v5m"),
            "vk" to GrammarClassification("VERB", "VK", "vk"),
            "vs" to GrammarClassification("VERB", "VS", "vs"),
            "vz" to GrammarClassification("VERB", "VZ", "vz"),
            "adj-i" to GrammarClassification("ADJECTIVE", "I", "adj-i"),
            "adj-na" to GrammarClassification("ADJECTIVE", "NA", "adj-na"),
        )
    }
}
