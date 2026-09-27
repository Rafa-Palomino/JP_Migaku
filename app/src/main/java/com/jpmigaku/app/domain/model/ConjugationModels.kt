package com.jpmigaku.app.domain.model

data class ConjugationFormOption(
    val selectionKey: String,
    val wordClass: String,
    val formKey: String,
    val displayName: String,
    val whenTo: String,
    val howTo: String
)

data class ConjugationQuizQuestion(
    val vocabularyEntry: VocabularyEntry,
    val conjugatedText: String,
    val formDisplayName: String,
    val classificationTag: String
)
