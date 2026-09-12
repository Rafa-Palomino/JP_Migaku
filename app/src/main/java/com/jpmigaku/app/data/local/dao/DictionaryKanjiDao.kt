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
           OR spanishMeanings LIKE '%' || :query || '%'
           OR englishMeanings LIKE '%' || :query || '%'
           OR onyomi LIKE '%' || :query || '%'
           OR kunyomi LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN character LIKE :query || '%'
                  OR spanishMeanings LIKE :query || '%'
                  OR englishMeanings LIKE :query || '%'
                  OR onyomi LIKE :query || '%'
                  OR kunyomi LIKE :query || '%'
                THEN 0
                ELSE 1
            END,
            character DESC
        """
    )
    suspend fun search(query: String): List<DictionaryKanjiEntity>
}
