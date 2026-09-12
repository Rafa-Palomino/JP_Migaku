package com.jpmigaku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jpmigaku.app.data.local.entity.DeckEntity

@Dao
interface DeckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deck: DeckEntity)

    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    suspend fun getAll(): List<DeckEntity>

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun delete(deckId: String)
}
