package com.jpmigaku.app.data.repository

import androidx.room.withTransaction
import com.jpmigaku.app.data.local.JPMigakuDatabase
import com.jpmigaku.app.data.local.entity.CardDeckEntity
import com.jpmigaku.app.data.local.entity.DeckEntity
import com.jpmigaku.app.data.local.entity.StudyCardEntity
import com.jpmigaku.app.data.local.entity.StudyCardReviewEntity
import com.jpmigaku.app.domain.model.Deck
import com.jpmigaku.app.domain.model.VocabularyEntry
import javax.inject.Inject
import java.util.UUID

interface VocabularyRepository {
    suspend fun save(entry: VocabularyEntry)
    suspend fun saveStudyCard(kind: String, sourceProvider: String, sourceKey: String, sourceVersion: String, japanese: String, reading: String, meaning: String, deckId: String?, romaji: String = "", sourceSnapshot: String? = null, deckIds: List<String> = emptyList()): VocabularyEntry
    suspend fun recordReview(entry: VocabularyEntry, wasCorrect: Boolean): VocabularyEntry
    suspend fun getAll(): List<VocabularyEntry>
    suspend fun getDue(limit: Int = 10): List<VocabularyEntry>
    suspend fun update(entry: VocabularyEntry)
    suspend fun search(query: String, limit: Int = 50): List<VocabularyEntry>
    suspend fun getByDeck(deckId: String): List<VocabularyEntry>
    suspend fun removeFromDeck(cardId: String, deckId: String)
}

interface DeckRepository { suspend fun createDeck(deck: Deck): Deck; suspend fun listDecks(): List<Deck> }

class RoomVocabularyRepository @Inject constructor(private val database: JPMigakuDatabase) : VocabularyRepository, DeckRepository {
    private val cards = database.studyCardDao()
    private val decks = database.deckDao()

    override suspend fun save(entry: VocabularyEntry) {
        saveStudyCard("VOCABULARY", "manual", entry.id, "1", entry.japanese, entry.reading, entry.meaningEs, entry.deckId, existing = entry)
    }

    override suspend fun saveStudyCard(kind: String, sourceProvider: String, sourceKey: String, sourceVersion: String, japanese: String, reading: String, meaning: String, deckId: String?, romaji: String, sourceSnapshot: String?, deckIds: List<String>): VocabularyEntry =
        saveStudyCard(kind, sourceProvider, sourceKey, sourceVersion, japanese, reading, meaning, deckId, romaji, sourceSnapshot, deckIds, null)

    private suspend fun saveStudyCard(kind: String, provider: String, key: String, version: String, japanese: String, reading: String, meaning: String, deckId: String?, romaji: String = "", sourceSnapshot: String? = null, deckIds: List<String> = emptyList(), existing: VocabularyEntry? = null): VocabularyEntry {
        val card = cards.findByIdentity(kind, provider, key)
        val id = card?.id ?: existing?.id ?: UUID.randomUUID().toString()
        val created = card?.createdAt ?: existing?.createdAt ?: System.currentTimeMillis()
        val priorReview = card?.let { cards.getReview(it.id) }
        database.withTransaction {
            val updatedCard = StudyCardEntity(
                id = id,
                kind = kind,
                sourceProvider = provider,
                sourceKey = key,
                sourceVersion = version,
                displayJapanese = japanese,
                displayReading = reading,
                displayMeaning = meaning,
                displayRomaji = romaji,
                sourceSnapshot = sourceSnapshot,
                createdAt = created,
                updatedAt = System.currentTimeMillis()
            )
            if (card == null) cards.insertCard(updatedCard) else cards.updateCard(updatedCard)
            cards.insertReview(
                priorReview ?: StudyCardReviewEntity(cardId = id)
            )
            (deckIds + listOfNotNull(deckId)).distinct().forEach {
                cards.insertDeckLink(CardDeckEntity(id, it))
            }
        }
        return VocabularyEntry(
            id, japanese, reading, meaning, deckId, created,
            priorReview?.lastReviewedAt,
            priorReview?.intervalDays ?: 1,
            priorReview?.easeFactor ?: 2.5,
            priorReview?.attempts ?: 0
        )
    }

    override suspend fun getAll() = cards.getAllCards().map { it.toEntry() }
    override suspend fun getDue(limit: Int): List<VocabularyEntry> = cards.getAllCards().map { it.toEntry() }.filter { it.lastReviewed == null || it.interval <= 1 }.take(limit)
    override suspend fun update(entry: VocabularyEntry) {
        val review = cards.getReview(entry.id) ?: StudyCardReviewEntity(cardId = entry.id)
        cards.insertReview(
            review.copy(
                lastReviewedAt = entry.lastReviewed,
                intervalDays = entry.interval,
                easeFactor = entry.easeFactor,
                attempts = entry.reviewCount
            )
        )
    }
    override suspend fun recordReview(entry: VocabularyEntry, wasCorrect: Boolean): VocabularyEntry {
        val review = cards.getReview(entry.id) ?: StudyCardReviewEntity(cardId = entry.id)
        val now = System.currentTimeMillis()
        val updated = review.copy(
            attempts = review.attempts + 1,
            correctCount = review.correctCount + if (wasCorrect) 1 else 0,
            incorrectCount = review.incorrectCount + if (wasCorrect) 0 else 1,
            currentStreak = if (wasCorrect) review.currentStreak + 1 else 0,
            bestStreak = if (wasCorrect) {
                maxOf(review.bestStreak, review.currentStreak + 1)
            } else {
                review.bestStreak
            },
            lastCorrectAt = if (wasCorrect) now else review.lastCorrectAt,
            lastIncorrectAt = if (!wasCorrect) now else review.lastIncorrectAt,
            lastReviewedAt = now,
            intervalDays = entry.interval,
            easeFactor = entry.easeFactor,
            repetitions = review.repetitions + if (wasCorrect) 1 else 0,
            dueAt = now + entry.interval * 24L * 60L * 60L * 1000L
        )
        cards.insertReview(updated)
        return entry.copy(
            lastReviewed = updated.lastReviewedAt,
            interval = updated.intervalDays,
            easeFactor = updated.easeFactor,
            reviewCount = updated.attempts
        )
    }
    override suspend fun search(query: String, limit: Int) = getAll().filter { it.japanese.contains(query, true) || it.reading.contains(query, true) || it.meaningEs.contains(query, true) }.take(limit)
    override suspend fun getByDeck(deckId: String) = cards.getCardsByDeck(deckId).map { it.toEntry() }
    override suspend fun removeFromDeck(cardId: String, deckId: String) = cards.removeDeckLink(cardId, deckId)

    override suspend fun createDeck(deck: Deck): Deck { decks.insert(DeckEntity(deck.id, deck.name, deck.createdAt)); return deck }
    override suspend fun listDecks() = decks.getAll().map { Deck(it.id, it.name, it.createdAt) }

    private suspend fun StudyCardEntity.toEntry(): VocabularyEntry {
        val review = cards.getReview(id)
        val deckId = cards.getDeckIds(id).firstOrNull()
        return VocabularyEntry(id, displayJapanese, displayReading, displayMeaning, deckId, createdAt, review?.lastReviewedAt, review?.intervalDays ?: 1, review?.easeFactor ?: 2.5, review?.attempts ?: 0)
    }
}
