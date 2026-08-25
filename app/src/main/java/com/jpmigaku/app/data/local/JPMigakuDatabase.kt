package com.jpmigaku.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jpmigaku.app.data.local.dao.DeckDao
import com.jpmigaku.app.data.local.dao.DictionaryKanjiDao
import com.jpmigaku.app.data.local.dao.DictionaryVocabularyDao
import com.jpmigaku.app.data.local.dao.VocabularyDao
import com.jpmigaku.app.data.local.entity.DictionaryKanjiEntity
import com.jpmigaku.app.data.local.entity.DictionaryVocabularyEntity
import com.jpmigaku.app.data.local.entity.DeckEntity
import com.jpmigaku.app.data.local.entity.VocabularyEntity

@Database(
    entities = [
        DeckEntity::class,
        VocabularyEntity::class,
        DictionaryVocabularyEntity::class,
        DictionaryKanjiEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(DictionaryConverters::class)
abstract class JPMigakuDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun dictionaryVocabularyDao(): DictionaryVocabularyDao
    abstract fun dictionaryKanjiDao(): DictionaryKanjiDao
}
