package com.jpmigaku.app.domain.model

data class DictionaryKanji(
    val character: String,
    val onyomi: String,
    val kunyomi: String,
    val meaning: String,
    val isEnglishFallback: Boolean,
    val jlptLevel: String = ""
)
