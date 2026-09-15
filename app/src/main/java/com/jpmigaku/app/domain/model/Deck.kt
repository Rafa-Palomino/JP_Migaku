package com.jpmigaku.app.domain.model

data class Deck(
    val id: String,
    val name: String,
    val createdAt: Long,
    val kind: String = "VOCABULARY"
)
