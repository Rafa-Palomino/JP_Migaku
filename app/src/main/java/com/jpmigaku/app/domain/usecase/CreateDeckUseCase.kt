package com.jpmigaku.app.domain.usecase

import com.jpmigaku.app.data.repository.DeckRepository
import com.jpmigaku.app.domain.model.Deck
import java.util.UUID
import javax.inject.Inject

class CreateDeckUseCase @Inject constructor(
    private val deckRepository: DeckRepository
) {
    suspend operator fun invoke(name: String, kind: String = "VOCABULARY"): Deck {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "El nombre del deck no puede estar vacío" }

        return deckRepository.createDeck(
            Deck(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                createdAt = System.currentTimeMillis(),
                kind = kind
            )
        )
    }
}
