package com.jpmigaku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jpmigaku.app.data.local.entity.JlptClassificationEntity

@Dao
interface JlptClassificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<JlptClassificationEntity>)

    @Query("SELECT COUNT(*) FROM jlpt_classification")
    suspend fun count(): Int

    @Query("DELETE FROM jlpt_classification")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM jlpt_classification
        WHERE kind = :kind AND level = :level
        ORDER BY japaneseText, reading
        """
    )
    suspend fun findByKindAndLevel(kind: String, level: String): List<JlptClassificationEntity>

    @Query(
        """
        SELECT * FROM jlpt_classification
        WHERE kind = :kind
          AND japaneseText = :japaneseText
          AND (:reading = '' OR reading = :reading)
        ORDER BY level
        """
    )
    suspend fun findForEntry(
        kind: String,
        japaneseText: String,
        reading: String
    ): List<JlptClassificationEntity>
}
