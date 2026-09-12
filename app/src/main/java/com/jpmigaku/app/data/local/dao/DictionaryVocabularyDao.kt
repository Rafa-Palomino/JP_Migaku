package com.jpmigaku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jpmigaku.app.data.local.entity.DictionaryVocabularyEntity

@Dao
interface DictionaryVocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryVocabularyEntity>)

    @Query("SELECT COUNT(*) FROM dictionary_vocabulary")
    suspend fun count(): Int

    @Query("DELETE FROM dictionary_vocabulary")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM dictionary_vocabulary
        WHERE japanese LIKE '%' || :query || '%'
           OR reading LIKE '%' || :query || '%'
           OR romaji LIKE '%' || :query || '%'
           OR spanishGlosses LIKE '%' || :query || '%'
           OR englishGlosses LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN japanese LIKE :query || '%'
                  OR reading LIKE :query || '%'
                  OR romaji LIKE :query || '%'
                  OR spanishGlosses LIKE :query || '%'
                  OR englishGlosses LIKE :query || '%'
                THEN 0
                ELSE 1
            END,
            japanese DESC
        """
    )
    suspend fun search(query: String): List<DictionaryVocabularyEntity>
}
