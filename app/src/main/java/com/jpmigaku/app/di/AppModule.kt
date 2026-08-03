package com.jpmigaku.app.di

import android.content.Context
import androidx.room.Room
import com.jpmigaku.app.data.local.JPMigakuDatabase
import com.jpmigaku.app.data.repository.DeckRepository
import com.jpmigaku.app.data.repository.RoomVocabularyRepository
import com.jpmigaku.app.data.repository.VocabularyRepository
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
            .fallbackToDestructiveMigration()
            .build()
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
}
