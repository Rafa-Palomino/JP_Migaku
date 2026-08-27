package com.jpmigaku.app.domain.model

data class DictionaryVocabulary(
    val sequenceId: String,
    val japanese: String,
    val reading: String,
    val romaji: String,
    val meaning: String,
    val isEnglishFallback: Boolean
)
