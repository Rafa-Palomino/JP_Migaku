package com.jpmigaku.app.data.repository

import com.jpmigaku.app.data.local.JPMigakuDatabase
import com.jpmigaku.app.data.local.entity.DeckEntity
import com.jpmigaku.app.data.local.entity.VocabularyEntity
import com.jpmigaku.app.domain.model.Deck
import com.jpmigaku.app.domain.model.VocabularyEntry
import javax.inject.Inject

interface VocabularyRepository {
    suspend fun save(entry: VocabularyEntry)
    suspend fun getAll(): List<VocabularyEntry>
    suspend fun getDue(limit: Int = 10): List<VocabularyEntry>
    suspend fun update(entry: VocabularyEntry)
    suspend fun search(query: String, limit: Int = 50): List<VocabularyEntry>
}

interface DeckRepository {
    suspend fun createDeck(deck: Deck): Deck
    suspend fun listDecks(): List<Deck>
}

class RoomVocabularyRepository @Inject constructor(
    private val database: JPMigakuDatabase
) : VocabularyRepository, DeckRepository {
    private val deckDao = database.deckDao()
    private val vocabularyDao = database.vocabularyDao()

    override suspend fun save(entry: VocabularyEntry) {
        vocabularyDao.insert(
            VocabularyEntity(
                id = entry.id,
                japanese = entry.japanese,
                reading = entry.reading,
                meaningEs = entry.meaningEs,
                deckId = entry.deckId,
                createdAt = entry.createdAt,
                lastReviewed = entry.lastReviewed,
                interval = entry.interval,
                easeFactor = entry.easeFactor,
                reviewCount = entry.reviewCount
            )
        )
    }

    override suspend fun getAll(): List<VocabularyEntry> {
        return vocabularyDao.getAll().map { entity ->
            entity.toDomain()
        }
    }

    override suspend fun getDue(limit: Int): List<VocabularyEntry> {
        return vocabularyDao.due(limit = limit).map { entity -> entity.toDomain() }
    }

    override suspend fun update(entry: VocabularyEntry) {
        vocabularyDao.update(entry.toEntity())
    }

    override suspend fun search(query: String, limit: Int): List<VocabularyEntry> {
        return vocabularyDao.search(query = query, limit = limit).map { entity -> entity.toDomain() }
    }

    override suspend fun createDeck(deck: Deck): Deck {
        deckDao.insert(
            DeckEntity(
                id = deck.id,
                name = deck.name,
                createdAt = deck.createdAt
            )
        )
        return deck
    }

    override suspend fun listDecks(): List<Deck> {
        return deckDao.getAll().map { entity ->
            Deck(id = entity.id, name = entity.name, createdAt = entity.createdAt)
        }
    }

    private fun VocabularyEntity.toDomain(): VocabularyEntry {
        return VocabularyEntry(
            id = id,
            japanese = japanese,
            reading = reading,
            meaningEs = meaningEs,
            deckId = deckId,
            createdAt = createdAt,
            lastReviewed = lastReviewed,
            interval = interval,
            easeFactor = easeFactor,
            reviewCount = reviewCount
        )
    }

    private fun VocabularyEntry.toEntity(): VocabularyEntity {
        return VocabularyEntity(
            id = id,
            japanese = japanese,
            reading = reading,
            meaningEs = meaningEs,
            deckId = deckId,
            createdAt = createdAt,
            lastReviewed = lastReviewed,
            interval = interval,
            easeFactor = easeFactor,
            reviewCount = reviewCount
        )
    }
}
