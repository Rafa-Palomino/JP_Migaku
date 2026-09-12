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

/**
 * Stores study cards and their review state.
 *
 * The repository exposes the consolidated study-card model used by both
 * vocabulary and kanji quizzes.
 */
interface VocabularyRepository {
    suspend fun save(entry: VocabularyEntry)
    suspend fun saveStudyCard(
        kind: String,
        sourceProvider: String,
        sourceKey: String,
        sourceVersion: String,
        japanese: String,
        reading: String,
        meaning: String,
        deckId: String?,
        romaji: String = "",
        sourceSnapshot: String? = null,
        deckIds: List<String> = emptyList()
    ): VocabularyEntry
    suspend fun recordReview(entry: VocabularyEntry, wasCorrect: Boolean): VocabularyEntry
    suspend fun getAll(): List<VocabularyEntry>
    suspend fun getDue(limit: Int = 10, kind: String? = null): List<VocabularyEntry>
    suspend fun update(entry: VocabularyEntry)
    suspend fun search(query: String, limit: Int = 50): List<VocabularyEntry>
    suspend fun getByDeck(deckId: String): List<VocabularyEntry>
    suspend fun removeFromDeck(cardId: String, deckId: String)
}

interface DeckRepository {
    suspend fun createDeck(deck: Deck): Deck
    suspend fun listDecks(): List<Deck>
    suspend fun deleteDeckIfEmpty(deckId: String): Boolean
}

class RoomVocabularyRepository @Inject constructor(private val database: JPMigakuDatabase) : VocabularyRepository, DeckRepository {
    private val cards = database.studyCardDao()
    private val decks = database.deckDao()

    override suspend fun save(entry: VocabularyEntry) {
        saveStudyCard("VOCABULARY", "manual", entry.id, "1", entry.japanese, entry.reading, entry.meaningEs, entry.deckId, existing = entry)
    }

    override suspend fun saveStudyCard(
        kind: String,
        sourceProvider: String,
        sourceKey: String,
        sourceVersion: String,
        japanese: String,
        reading: String,
        meaning: String,
        deckId: String?,
        romaji: String,
        sourceSnapshot: String?,
        deckIds: List<String>
    ): VocabularyEntry =
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
            id, japanese, reading, meaning, kind, romaji, deckId, created,
            priorReview?.lastReviewedAt,
            priorReview?.intervalDays ?: 1,
            priorReview?.easeFactor ?: 2.5,
            priorReview?.attempts ?: 0
        )
    }

    override suspend fun getAll(): List<VocabularyEntry> = cards.getAllCards().map { it.toEntry() }

    override suspend fun getDue(limit: Int, kind: String?): List<VocabularyEntry> {
        if (limit <= 0) return emptyList()
        val now = System.currentTimeMillis()
        val reviewsByCard = cards.getReviews().associateBy { it.cardId }

        return cards.getCardsForReview(kind)
            .mapNotNull { card ->
                val review = reviewsByCard[card.id]
                val dueAt = resolveDueAt(review)
                val urgent = review.isUrgent(now)
                if (!isCardDue(review, now, dueAt) && !urgent) return@mapNotNull null

                DueCandidate(
                    card = card,
                    review = review,
                    dueAt = dueAt,
                    urgent = urgent,
                    appearanceScore = review.appearanceScore(now)
                )
            }
            .sortedWith(
                compareByDescending<DueCandidate> { it.urgent }
                    .thenByDescending { it.appearanceScore }
                    .thenBy { it.dueAt ?: Long.MIN_VALUE }
                    .thenBy { it.card.createdAt }
                    .thenBy { it.card.id }
            )
            .take(limit)
            .map { candidate -> candidate.card.toEntry(candidate.review) }
    }

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
        val scheduledInterval = if (wasCorrect) entry.interval.coerceAtLeast(1) else 1
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
            intervalDays = scheduledInterval,
            easeFactor = entry.easeFactor,
            repetitions = if (wasCorrect) review.repetitions + 1 else 0,
            dueAt = now + scheduledInterval * MILLIS_PER_DAY
        )
        cards.insertReview(updated)
        return entry.copy(
            lastReviewed = updated.lastReviewedAt,
            interval = updated.intervalDays,
            easeFactor = updated.easeFactor,
            reviewCount = updated.attempts
        )
    }
    override suspend fun search(query: String, limit: Int): List<VocabularyEntry> =
        getAll()
            .filter {
                it.japanese.contains(query, true) ||
                    it.reading.contains(query, true) ||
                    it.meaningEs.contains(query, true)
            }
            .take(limit)

    override suspend fun getByDeck(deckId: String): List<VocabularyEntry> =
        cards.getCardsByDeck(deckId).map { it.toEntry() }

    override suspend fun removeFromDeck(cardId: String, deckId: String) {
        cards.removeDeckLink(cardId, deckId)
    }

    override suspend fun createDeck(deck: Deck): Deck {
        decks.insert(DeckEntity(deck.id, deck.name, deck.createdAt))
        return deck
    }

    override suspend fun listDecks(): List<Deck> =
        decks.getAll().map { Deck(it.id, it.name, it.createdAt) }

    override suspend fun deleteDeckIfEmpty(deckId: String): Boolean {
        return database.withTransaction {
            if (cards.countCardsByDeck(deckId) != 0) {
                false
            } else {
                decks.delete(deckId)
                true
            }
        }
    }

    private suspend fun StudyCardEntity.toEntry(): VocabularyEntry {
        val review = cards.getReview(id)
        return toEntry(review)
    }

    private suspend fun StudyCardEntity.toEntry(review: StudyCardReviewEntity?): VocabularyEntry {
        val deckId = cards.getDeckIds(id).firstOrNull()
        return VocabularyEntry(
            id = id,
            japanese = displayJapanese,
            reading = displayReading,
            meaningEs = displayMeaning,
            kind = kind,
            romaji = displayRomaji,
            deckId = deckId,
            createdAt = createdAt,
            lastReviewed = review?.lastReviewedAt,
            interval = review?.intervalDays ?: 1,
            easeFactor = review?.easeFactor ?: 2.5,
            reviewCount = review?.attempts ?: 0
        )
    }

    private fun isCardDue(review: StudyCardReviewEntity?, now: Long, dueAt: Long?): Boolean {
        if (review == null) return true
        val scheduledDueAt = dueAt ?: return true
        return scheduledDueAt <= now
    }

    /*
     * A failed card gets a short target (one day in recordReview). Keep the
     * appearance rule defensive for old/imported reviews whose dueAt may be
     * farther away: a recent failure must reappear within 72 hours.
     */
    private fun StudyCardReviewEntity?.isUrgent(now: Long): Boolean {
        if (this == null || lastIncorrectAt == null) return false
        if (lastCorrectAt != null && lastIncorrectAt <= lastCorrectAt) return false
        /*
         * The latest failure is authoritative. This also repairs imported
         * rows with a stale dueAt: they must not postpone a failed card past
         * the 72-hour target.
         */
        return lastIncorrectAt <= now + URGENT_TARGET_MILLIS
    }

    /*
     * Higher failure count and longer time since the last correct answer make
     * a card more visible; a growing current streak makes it less visible.
     * The integer weighting is deliberate and deterministic.
     */
    private fun StudyCardReviewEntity?.appearanceScore(now: Long): Long {
        if (this == null) return 0L
        val daysSinceCorrect = lastCorrectAt?.let {
            ((now - it).coerceAtLeast(0L) / MILLIS_PER_DAY)
        } ?: if (attempts == 0) 0L else (now / MILLIS_PER_DAY)
        return incorrectCount.toLong() * FAILURE_WEIGHT +
            daysSinceCorrect -
            currentStreak.toLong() * STREAK_WEIGHT
    }

    private fun resolveDueAt(review: StudyCardReviewEntity?): Long? {
        if (review == null) return null
        review.dueAt?.let { return it }
        val lastReviewed = review.lastReviewedAt ?: return null
        val intervalDays = review.intervalDays.coerceAtLeast(1)
        return lastReviewed + intervalDays * MILLIS_PER_DAY
    }

    private data class DueCandidate(
        val card: StudyCardEntity,
        val review: StudyCardReviewEntity?,
        val dueAt: Long?,
        val urgent: Boolean,
        val appearanceScore: Long
    )

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        const val URGENT_TARGET_MILLIS = 72L * 60L * 60L * 1000L
        const val FAILURE_WEIGHT = 3L
        const val STREAK_WEIGHT = 1L
    }
}
