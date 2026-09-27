package com.jpmigaku.app.domain.model

data class KanjiQuizQuestion(
    val vocabularyEntry: VocabularyEntry,
    val targetKanji: VocabularyEntry,
    val maskedWord: String,
    val reading: String,
    val answer: String
)
