package com.jpmigaku.app.domain.model

data class VocabularyEntry(
    val id: String,
    val japanese: String,
    val reading: String,
    val meaningEs: String,
    val kind: String = "VOCABULARY",
    val romaji: String = "",
    val deckId: String? = null,
    val createdAt: Long,
    val lastReviewed: Long? = null,
    val interval: Int = 1,
    val easeFactor: Double = 2.5,
    val reviewCount: Int = 0
)
