package com.jpmigaku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jpmigaku.app.data.local.entity.ConjugationFormEntity
import com.jpmigaku.app.data.local.entity.ConjugationPatternEntity
import com.jpmigaku.app.data.local.entity.ConjugationReviewStatsEntity

@Dao
interface ConjugationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: ConjugationPatternEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<ConjugationPatternEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForm(form: ConjugationFormEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForms(forms: List<ConjugationFormEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewStats(stats: ConjugationReviewStatsEntity)

    @Update
    suspend fun updateReviewStats(stats: ConjugationReviewStatsEntity)

    @Query("SELECT * FROM conjugation_patterns ORDER BY wordClass, conjugationType")
    suspend fun getPatterns(): List<ConjugationPatternEntity>

    @Query("SELECT * FROM conjugation_forms ORDER BY patternId, id")
    suspend fun getForms(): List<ConjugationFormEntity>

    @Query(
        """
        SELECT * FROM conjugation_forms
        WHERE patternId = :patternId
        ORDER BY id
        """
    )
    suspend fun getForms(patternId: String): List<ConjugationFormEntity>

    @Query(
        """
        SELECT * FROM conjugation_review_stats
        WHERE conjugationFormId = :conjugationFormId
        """
    )
    suspend fun getReviewStats(conjugationFormId: String): ConjugationReviewStatsEntity?

    @Query("SELECT * FROM conjugation_review_stats ORDER BY conjugationFormId")
    suspend fun getReviewStats(): List<ConjugationReviewStatsEntity>
}
