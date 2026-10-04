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
                WHEN japanese = :query
                  OR reading = :query
                  OR romaji = :query COLLATE NOCASE
                  OR spanishGlosses = :query COLLATE NOCASE
                  OR englishGlosses = :query COLLATE NOCASE
                THEN 0
                WHEN japanese LIKE :query || '%'
                  OR reading LIKE :query || '%'
                  OR romaji LIKE :query || '%' COLLATE NOCASE
                  OR spanishGlosses LIKE :query || '%' COLLATE NOCASE
                  OR englishGlosses LIKE :query || '%' COLLATE NOCASE
                THEN 1
                ELSE 2
            END,
            MIN(
                CASE WHEN japanese LIKE '%' || :query || '%' THEN LENGTH(japanese) ELSE 2147483647 END,
                CASE WHEN reading LIKE '%' || :query || '%' THEN LENGTH(reading) ELSE 2147483647 END,
                CASE WHEN romaji LIKE '%' || :query || '%' COLLATE NOCASE THEN LENGTH(romaji) ELSE 2147483647 END,
                CASE WHEN spanishGlosses LIKE '%' || :query || '%' COLLATE NOCASE THEN LENGTH(spanishGlosses) ELSE 2147483647 END,
                CASE WHEN englishGlosses LIKE '%' || :query || '%' COLLATE NOCASE THEN LENGTH(englishGlosses) ELSE 2147483647 END
            ),
            japanese,
            sequenceId
        """
    )
    suspend fun search(query: String): List<DictionaryVocabularyEntity>

    @Query(
        """
        SELECT * FROM dictionary_vocabulary
        WHERE sequenceId = :sequenceId
        LIMIT 1
        """
    )
    suspend fun findBySequenceId(sequenceId: String): DictionaryVocabularyEntity?

    @Query(
        """
        SELECT * FROM dictionary_vocabulary
        WHERE japanese = :japanese
          AND (:reading = '' OR reading = :reading)
        ORDER BY sequenceId
        """
    )
    suspend fun findExact(
        japanese: String,
        reading: String
    ): List<DictionaryVocabularyEntity>
}
