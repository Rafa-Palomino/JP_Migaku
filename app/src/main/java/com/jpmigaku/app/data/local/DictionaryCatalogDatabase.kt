package com.jpmigaku.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jpmigaku.app.data.local.dao.DictionaryKanjiDao
import com.jpmigaku.app.data.local.dao.DictionaryVocabularyDao
import com.jpmigaku.app.data.local.entity.DictionaryKanjiEntity
import com.jpmigaku.app.data.local.entity.DictionaryVocabularyEntity

@Database(entities = [DictionaryVocabularyEntity::class, DictionaryKanjiEntity::class], version = 1, exportSchema = false)
abstract class DictionaryCatalogDatabase : RoomDatabase() {
    abstract fun dictionaryVocabularyDao(): DictionaryVocabularyDao
    abstract fun dictionaryKanjiDao(): DictionaryKanjiDao
}
