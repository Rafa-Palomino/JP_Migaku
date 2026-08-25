package com.jpmigaku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jpmigaku.app.data.local.entity.VocabularyEntity

@Dao
interface VocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VocabularyEntity)

    @Query("SELECT * FROM vocabulary ORDER BY createdAt DESC")
    suspend fun getAll(): List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary WHERE deckId = :deckId ORDER BY createdAt DESC")
    suspend fun getByDeck(deckId: String): List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary WHERE (interval <= :interval OR lastReviewed IS NULL) ORDER BY createdAt DESC LIMIT :limit")
    suspend fun due(limit: Int, interval: Int = 1): List<VocabularyEntity>

    @Query("""
        SELECT * FROM vocabulary
        WHERE japanese LIKE '%' || :query || '%'
           OR reading LIKE '%' || :query || '%'
           OR meaningEs LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<VocabularyEntity>

    @Update
    suspend fun update(entity: VocabularyEntity)
}
