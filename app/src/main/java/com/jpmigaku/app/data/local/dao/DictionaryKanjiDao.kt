package com.jpmigaku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jpmigaku.app.data.local.entity.DictionaryKanjiEntity

@Dao
interface DictionaryKanjiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryKanjiEntity>)

    @Query("SELECT COUNT(*) FROM dictionary_kanji")
    suspend fun count(): Int

    @Query("DELETE FROM dictionary_kanji")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM dictionary_kanji
        WHERE character LIKE '%' || :query || '%'
           OR meanings LIKE '%' || :query || '%'
        ORDER BY character
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<DictionaryKanjiEntity>
}
