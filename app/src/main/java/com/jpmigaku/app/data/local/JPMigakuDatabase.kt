package com.jpmigaku.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jpmigaku.app.data.local.dao.DeckDao
import com.jpmigaku.app.data.local.dao.ConjugationDao
import com.jpmigaku.app.data.local.dao.JlptClassificationDao
import com.jpmigaku.app.data.local.dao.StudyCardDao
import com.jpmigaku.app.data.local.entity.CardDeckEntity
import com.jpmigaku.app.data.local.entity.ConjugationFormEntity
import com.jpmigaku.app.data.local.entity.ConjugationPatternEntity
import com.jpmigaku.app.data.local.entity.ConjugationReviewStatsEntity
import com.jpmigaku.app.data.local.entity.DeckEntity
import com.jpmigaku.app.data.local.entity.JlptClassificationEntity
import com.jpmigaku.app.data.local.entity.StudyCardEntity
import com.jpmigaku.app.data.local.entity.StudyCardReviewEntity

@Database(
    entities = [
        DeckEntity::class,
        StudyCardEntity::class,
        CardDeckEntity::class,
        StudyCardReviewEntity::class,
        JlptClassificationEntity::class,
        ConjugationPatternEntity::class,
        ConjugationFormEntity::class,
        ConjugationReviewStatsEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class JPMigakuDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun conjugationDao(): ConjugationDao
    abstract fun studyCardDao(): StudyCardDao
    abstract fun jlptClassificationDao(): JlptClassificationDao
}
