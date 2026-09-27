package com.jpmigaku.app.di

import android.content.Context
import androidx.room.Room
import com.jpmigaku.app.data.local.DictionaryCatalogDatabase
import com.jpmigaku.app.data.local.DatabaseMigrations
import com.jpmigaku.app.data.local.JPMigakuDatabase
import com.jpmigaku.app.data.repository.DeckRepository
import com.jpmigaku.app.data.repository.DictionaryVocabularyRepository
import com.jpmigaku.app.data.repository.RoomDictionaryVocabularyRepository
import com.jpmigaku.app.data.repository.DictionaryKanjiRepository
import com.jpmigaku.app.data.repository.RoomDictionaryKanjiRepository
import com.jpmigaku.app.data.repository.RoomVocabularyRepository
import com.jpmigaku.app.data.repository.VocabularyRepository
import com.jpmigaku.app.data.repository.ConjugationRepository
import com.jpmigaku.app.data.repository.RoomConjugationRepository
import com.jpmigaku.app.domain.util.JapaneseInflectionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JPMigakuDatabase {
        return Room.databaseBuilder(
            context,
            JPMigakuDatabase::class.java,
            "jpmigaku.db"
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .addMigrations(DatabaseMigrations.MIGRATION_3_4)
            .build()
    }

    @Provides
    @Singleton
    fun provideDictionaryCatalogDatabase(@ApplicationContext context: Context): DictionaryCatalogDatabase {
        return Room.databaseBuilder(
            context,
            DictionaryCatalogDatabase::class.java,
            "jpmigaku-dictionary.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideVocabularyRepository(database: JPMigakuDatabase): VocabularyRepository {
        return RoomVocabularyRepository(database)
    }

    @Provides
    @Singleton
    fun provideDeckRepository(database: JPMigakuDatabase): DeckRepository {
        return RoomVocabularyRepository(database)
    }

    @Provides
    @Singleton
    fun provideDictionaryVocabularyRepository(catalog: DictionaryCatalogDatabase): DictionaryVocabularyRepository {
        return RoomDictionaryVocabularyRepository(catalog.dictionaryVocabularyDao())
    }

    @Provides
    @Singleton
    fun provideDictionaryKanjiRepository(catalog: DictionaryCatalogDatabase): DictionaryKanjiRepository {
        return RoomDictionaryKanjiRepository(catalog.dictionaryKanjiDao())
    }

    @Provides
    @Singleton
    fun provideInflectionEngine(): JapaneseInflectionEngine = JapaneseInflectionEngine()

    @Provides
    @Singleton
    fun provideConjugationRepository(
        database: JPMigakuDatabase,
        dictionaryVocabularyRepository: DictionaryVocabularyRepository,
        engine: JapaneseInflectionEngine
    ): ConjugationRepository =
        RoomConjugationRepository(database.conjugationDao(), dictionaryVocabularyRepository, engine)
}
