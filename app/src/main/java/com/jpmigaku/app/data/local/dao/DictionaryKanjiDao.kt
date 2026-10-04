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
                WHEN character = :query
                  OR spanishMeanings = :query COLLATE NOCASE
                  OR englishMeanings = :query COLLATE NOCASE
                  OR onyomi = :query
                  OR kunyomi = :query
                THEN 0
                WHEN character LIKE :query || '%'
                  OR spanishMeanings LIKE :query || '%' COLLATE NOCASE
                  OR englishMeanings LIKE :query || '%' COLLATE NOCASE
                  OR onyomi LIKE :query || '%'
                  OR kunyomi LIKE :query || '%'
                THEN 1
                ELSE 2
            END,
            MIN(
                CASE WHEN character LIKE '%' || :query || '%' THEN LENGTH(character) ELSE 2147483647 END,
                CASE WHEN spanishMeanings LIKE '%' || :query || '%' COLLATE NOCASE THEN LENGTH(spanishMeanings) ELSE 2147483647 END,
                CASE WHEN englishMeanings LIKE '%' || :query || '%' COLLATE NOCASE THEN LENGTH(englishMeanings) ELSE 2147483647 END,
                CASE WHEN onyomi LIKE '%' || :query || '%' THEN LENGTH(onyomi) ELSE 2147483647 END,
                CASE WHEN kunyomi LIKE '%' || :query || '%' THEN LENGTH(kunyomi) ELSE 2147483647 END
            ),
            character
        """
    )
    suspend fun search(query: String): List<DictionaryKanjiEntity>
}
