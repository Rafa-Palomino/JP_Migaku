package com.jpmigaku.app.domain.model

data class InflectionPatternRule(
    val id: String,
    val wordClass: String,
    val type: String,
    val dictionaryEnding: String,
    val sourceTag: String
)

data class InflectionFormRule(
    val formKey: String,
    val sourceFormKey: String,
    val endingsByPatternId: Map<String, String>
)

data class GrammarClassification(
    val wordClass: String,
    val type: String,
    val sourceTag: String
)

sealed interface InflectionResult {
    data class Success(
        val text: String,
        val pattern: InflectionPatternRule,
        val form: InflectionFormRule
    ) : InflectionResult

    data class Unavailable(val reason: String) : InflectionResult
}
