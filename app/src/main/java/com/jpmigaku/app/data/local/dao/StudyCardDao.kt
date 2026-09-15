package com.jpmigaku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jpmigaku.app.data.local.entity.CardDeckEntity
import com.jpmigaku.app.data.local.entity.StudyCardEntity
import com.jpmigaku.app.data.local.entity.StudyCardReviewEntity

@Dao
interface StudyCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: StudyCardEntity)

    @Update
    suspend fun updateCard(card: StudyCardEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeckLink(link: CardDeckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: StudyCardReviewEntity)

    @Update
    suspend fun updateReview(review: StudyCardReviewEntity)

    @Query("SELECT * FROM study_cards ORDER BY createdAt DESC")
    suspend fun getAllCards(): List<StudyCardEntity>

    @Query(
        """
        SELECT * FROM study_cards
        WHERE suspended = 0
          AND (:kind IS NULL OR kind = :kind)
          AND (
            :deckId IS NULL OR EXISTS (
                SELECT 1 FROM card_decks
                WHERE card_decks.cardId = study_cards.id
                  AND card_decks.deckId = :deckId
            )
          )
        ORDER BY createdAt ASC
        """
    )
    suspend fun getCardsForReview(
        kind: String? = null,
        deckId: String? = null
    ): List<StudyCardEntity>

    @Query("SELECT * FROM study_cards WHERE id = :id")
    suspend fun getCard(id: String): StudyCardEntity?

    @Query(
        """
        SELECT * FROM study_cards
        WHERE kind = :kind AND sourceProvider = :sourceProvider AND sourceKey = :sourceKey
        LIMIT 1
        """
    )
    suspend fun findByIdentity(
        kind: String,
        sourceProvider: String,
        sourceKey: String
    ): StudyCardEntity?

    @Query("SELECT * FROM study_card_reviews WHERE cardId = :cardId")
    suspend fun getReview(cardId: String): StudyCardReviewEntity?

    @Query("SELECT * FROM study_card_reviews ORDER BY cardId")
    suspend fun getReviews(): List<StudyCardReviewEntity>

    @Query("SELECT cardId FROM card_decks WHERE deckId = :deckId")
    suspend fun getCardIdsByDeck(deckId: String): List<String>

    @Query("SELECT COUNT(*) FROM card_decks WHERE deckId = :deckId")
    suspend fun countCardsByDeck(deckId: String): Int

    @Query(
        """
        SELECT study_cards.* FROM study_cards
        INNER JOIN card_decks ON card_decks.cardId = study_cards.id
        WHERE card_decks.deckId = :deckId
        ORDER BY study_cards.createdAt DESC
        """
    )
    suspend fun getCardsByDeck(deckId: String): List<StudyCardEntity>

    @Query("DELETE FROM card_decks WHERE cardId = :cardId AND deckId = :deckId")
    suspend fun removeDeckLink(cardId: String, deckId: String)

    @Query("SELECT deckId FROM card_decks WHERE cardId = :cardId")
    suspend fun getDeckIds(cardId: String): List<String>
}
