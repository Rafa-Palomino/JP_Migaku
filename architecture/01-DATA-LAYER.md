# 01 - DATA LAYER: Room Database, DAOs, Repositories

**Fecha**: 2026-07-08  
**Descripción**: Capa de persistencia con Room SQLite, entities, DAOs y repositories.

---

## 📦 COMPONENTES DATA LAYER

```
Data Layer
├── Entities (Room @Entity)
│   ├── VocabularyEntity
│   ├── KanjiEntity
│   ├── DeckEntity
│   ├── SRSHistoryEntity
│   ├── GlobalStreakEntity
│   └── DeckStreakEntity
│
├── DAOs (Room @Dao)
│   ├── VocabularyDao
│   ├── KanjiDao
│   ├── DeckDao
│   ├── SRSHistoryDao
│   ├── GlobalStreakDao
│   └── DeckStreakDao
│
├── Database
│   └── JPMigakuDatabase (@Database)
│
└── Repositories
    ├── VocabularyRepository
    ├── KanjiRepository
    ├── DeckRepository
    ├── SRSRepository
    └── ClipboardRepository
```

---

## 🏗️ ROOM ENTITIES

### VocabularyEntity

```kotlin
// PSEUDOCÓDIGO
@Entity(
    tableName = "vocabulary",
    indices = [
        Index(value = ["japanese"], name = "idx_vocab_japanese"),
        Index(value = ["spanish_meaning"], name = "idx_vocab_spanish"),
        Index(value = ["deck_id", "last_reviewed"], name = "idx_vocab_srs")
    ]
)
@Serializable
data class VocabularyEntity(
    @PrimaryKey
    val id: String,                    // UUID
    
    @ColumnInfo(name = "deck_id")
    val deckId: Long,                  // Foreign key → Deck
    
    val japanese: String,              // "水", "飲む", etc
    val reading: String?,              // "みず", "のむ" (hiragana)
    
    @ColumnInfo(name = "spanish_meaning")
    val spanishMeaning: String,        // "agua", "beber"
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,               // timestamp milliseconds
    
    @ColumnInfo(name = "last_reviewed")
    val lastReviewed: Long? = null,    // timestamp or null (never reviewed)
    
    // SRS SM-2 fields
    val interval: Int = 1,             // Days until next review (1, 3, 7, 14, 30+)
    val easeFactor: Double = 2.5,      // SM-2 ease factor (1.3-2.5)
    val repetitions: Int = 0           // Number of correct reviews
)

// DB Schema SQL (inferred):
/*
CREATE TABLE vocabulary (
    id TEXT PRIMARY KEY,
    deck_id INTEGER NOT NULL,
    japanese TEXT NOT NULL,
    reading TEXT,
    spanish_meaning TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_reviewed INTEGER,
    interval INTEGER NOT NULL DEFAULT 1,
    easeFactor REAL NOT NULL DEFAULT 2.5,
    repetitions INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(deck_id) REFERENCES deck(id)
);

CREATE INDEX idx_vocab_japanese ON vocabulary(japanese);
CREATE INDEX idx_vocab_spanish ON vocabulary(spanish_meaning);
CREATE INDEX idx_vocab_srs ON vocabulary(deck_id, last_reviewed);
*/
```

### KanjiEntity

```kotlin
@Entity(
    tableName = "kanji",
    indices = [
        Index(value = ["kanji"], name = "idx_kanji_char"),
        Index(value = ["spanish_meaning"], name = "idx_kanji_meaning")
    ]
)
data class KanjiEntity(
    @PrimaryKey
    val id: String,                    // UUID
    
    val kanji: String,                 // "水", "火", etc
    
    @ColumnInfo(name = "spanish_meaning")
    val spanishMeaning: String,        // "agua", "fuego"
    
    val onyomi: List<String>,          // Stored via TypeConverter
    val kunyomi: List<String>,         // Stored via TypeConverter
    
    val level: String? = null,         // "N5", "N4" (JLPT level)
    
    @ColumnInfo(name = "last_reviewed")
    val lastReviewed: Long? = null,
    
    // SRS fields (same as vocabulary)
    val interval: Int = 1,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0
)

class ListStringConverter {
    @TypeConverter
    fun fromString(value: String?): List<String> = value?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    @TypeConverter
    fun toString(values: List<String>): String = values.joinToString(separator = "|")
}
```

### DeckEntity

```kotlin
@Entity(tableName = "deck")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,                  // "JLPT N5", "Mi deck", etc
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,               // timestamp
    
    @ColumnInfo(name = "description")
    val description: String? = null
)
```

### SRSHistoryEntity

```kotlin
@Entity(
    tableName = "srs_history",
    indices = [
        Index(value = ["vocabulary_id", "review_date"], name = "idx_srs_history")
    ],
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabulary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SRSHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "vocabulary_id")
    val vocabularyId: String,          // FK → vocabulary.id
    
    @ColumnInfo(name = "review_date")
    val reviewDate: Long,              // timestamp
    
    val isCorrect: Boolean,            // true = correct answer, false = wrong
    
    @ColumnInfo(name = "old_interval")
    val oldInterval: Int,              // Previous interval
    
    @ColumnInfo(name = "new_interval")
    val newInterval: Int,              // Updated interval post-review
    
    @ColumnInfo(name = "old_ease_factor")
    val oldEaseFactor: Double,
    
    @ColumnInfo(name = "new_ease_factor")
    val newEaseFactor: Double
)
```

### StreakEntities

```kotlin
@Entity(tableName = "global_streak")
data class GlobalStreakEntity(
    @PrimaryKey
    val id: Int = 1,                   // Single row
    
    @ColumnInfo(name = "current_streak")
    val currentStreak: Int = 0,        // Days in current streak
    
    @ColumnInfo(name = "best_streak")
    val bestStreak: Int = 0,           // Longest streak ever
    
    @ColumnInfo(name = "last_practice_date")
    val lastPracticeDate: Long? = null,
    
    @ColumnInfo(name = "total_practice_days")
    val totalPracticeDays: Int = 0     // Total days with at least 1 quiz
)

@Entity(
    tableName = "deck_streak",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DeckStreakEntity(
    @PrimaryKey
    @ColumnInfo(name = "deck_id")
    val deckId: Long,                  // FK → deck.id
    
    @ColumnInfo(name = "current_streak")
    val currentStreak: Int = 0,        // Days in current streak
    
    @ColumnInfo(name = "best_streak")
    val bestStreak: Int = 0,
    
    @ColumnInfo(name = "last_practice_date")
    val lastPracticeDate: Long? = null,
    
    @ColumnInfo(name = "total_practice_days")
    val totalPracticeDays: Int = 0
)
```

---

## 🔍 ROOM DAOs

### VocabularyDao (Pseudocódigo)

```kotlin
@Dao
interface VocabularyDao {
    
    // INSERT
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vocabulary: VocabularyEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vocabularies: List<VocabularyEntity>)
    
    // QUERY by ID
    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getById(id: String): VocabularyEntity?
    
    // SEARCH: Búsqueda LIKE indexada
    @Query("""
        SELECT * FROM vocabulary 
        WHERE japanese LIKE '%' || :query || '%' 
           OR spanish_meaning LIKE '%' || :query || '%'
        ORDER BY (CASE WHEN japanese LIKE :query || '%' THEN 0 ELSE 1 END)
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchPaginated(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<VocabularyEntity>
    
    // SRS: Obtener vocabulario vencido para revisar
    @Query("""
        SELECT * FROM vocabulary 
        WHERE deck_id = :deckId 
          AND (last_reviewed IS NULL 
               OR CAST((strftime('%s','now') - last_reviewed) / 86400.0 AS INTEGER) >= interval)
        ORDER BY CASE WHEN last_reviewed IS NULL THEN 0 ELSE 1 END,
                 last_reviewed ASC
        LIMIT :limit
    """)
    suspend fun getVocabularyDueForReview(
        deckId: Long,
        limit: Int = 20
    ): List<VocabularyEntity>
    
    // UPDATE
    @Update
    suspend fun update(vocabulary: VocabularyEntity)
    
    @Query("""
        UPDATE vocabulary 
        SET interval = :newInterval,
            easeFactor = :newEaseFactor,
            repetitions = :newRepetitions,
            last_reviewed = :lastReviewed
        WHERE id = :id
    """)
    suspend fun updateAfterReview(
        id: String,
        newInterval: Int,
        newEaseFactor: Double,
        newRepetitions: Int,
        lastReviewed: Long
    )
    
    // DELETE
    @Delete
    suspend fun delete(vocabulary: VocabularyEntity)
    
    // STATISTICS
    @Query("SELECT COUNT(*) FROM vocabulary WHERE deck_id = :deckId")
    suspend fun countByDeck(deckId: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM vocabulary 
        WHERE deck_id = :deckId 
          AND CAST((strftime('%s','now') - COALESCE(last_reviewed, 0)) / 86400.0 AS INTEGER) >= interval
    """)
    suspend fun countDueForReview(deckId: Long): Int
    
    // FLOW (reactive)
    @Query("SELECT * FROM vocabulary WHERE deck_id = :deckId")
    fun watchVocabularyByDeck(deckId: Long): Flow<List<VocabularyEntity>>
}
```

### KanjiDao, DeckDao, SRSHistoryDao

```kotlin
// SIMILAR PATTERN:

@Dao
interface KanjiDao {
    @Insert
    suspend fun insert(kanji: KanjiEntity)
    
    @Query("SELECT * FROM kanji WHERE kanji = :kanjiChar")
    suspend fun getByKanji(kanjiChar: String): KanjiEntity?
    
    @Query("""
        SELECT * FROM kanji 
        WHERE kanji LIKE '%' || :query || '%' 
           OR spanish_meaning LIKE '%' || :query || '%'
        LIMIT 50
    """)
    suspend fun search(query: String): List<KanjiEntity>
    
    @Update
    suspend fun update(kanji: KanjiEntity)
}

@Dao
interface DeckDao {
    @Insert
    suspend fun insert(deck: DeckEntity): Long
    
    @Query("SELECT * FROM deck WHERE id = :id")
    suspend fun getById(id: Long): DeckEntity?
    
    @Query("SELECT * FROM deck ORDER BY created_at DESC")
    fun getAllDecks(): Flow<List<DeckEntity>>
    
    @Update
    suspend fun update(deck: DeckEntity)
    
    @Delete
    suspend fun delete(deck: DeckEntity)
}

@Dao
interface SRSHistoryDao {
    @Insert
    suspend fun insert(history: SRSHistoryEntity)
    
    @Query("""
        SELECT * FROM srs_history 
        WHERE vocabulary_id = :vocabularyId
        ORDER BY review_date DESC
        LIMIT :limit
    """)
    suspend fun getHistoryByVocabulary(vocabularyId: String, limit: Int = 10): List<SRSHistoryEntity>
    
    @Query("""
        SELECT COUNT(*) FROM srs_history 
        WHERE isCorrect = 1 AND review_date > :sinceDate
    """)
    suspend fun countCorrectReviews(sinceDate: Long): Int
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM global_streak WHERE id = 1")
    suspend fun getGlobalStreak(): GlobalStreakEntity?
    
    @Update
    suspend fun updateGlobalStreak(streak: GlobalStreakEntity)
    
    @Query("SELECT * FROM deck_streak WHERE deckId = :deckId")
    suspend fun getDeckStreak(deckId: Long): DeckStreakEntity?
    
    @Update
    suspend fun updateDeckStreak(streak: DeckStreakEntity)
}
```

---

## 🗄️ ROOM DATABASE

```kotlin
@Database(
    entities = [
        VocabularyEntity::class,
        KanjiEntity::class,
        DeckEntity::class,
        SRSHistoryEntity::class,
        GlobalStreakEntity::class,
        DeckStreakEntity::class
    ],
    version = 2,
    exportSchema = false  // Phase 0: Simple
)
@TypeConverters(ListStringConverter::class)
abstract class JPMigakuDatabase : RoomDatabase() {
    
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun kanjiDao(): KanjiDao
    abstract fun deckDao(): DeckDao
    abstract fun srsHistoryDao(): SRSHistoryDao
    abstract fun streakDao(): StreakDao
    
    companion object {
        @Volatile
        private var INSTANCE: JPMigakuDatabase? = null
        
        fun getInstance(context: Context): JPMigakuDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JPMigakuDatabase::class.java,
                    "jpmigaku.db"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WAL)  // Write-Ahead Logging
                    .addMigrations(MIGRATION_1_2)
                    .createFromAsset("jpmigaku_initial.db")        // Pre-populated
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No-op for Phase 0; kept to make future changes safe.
            }
        }
    }
}

// HILT Module
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Singleton
    @Provides
    fun provideDatabase(context: Context): JPMigakuDatabase {
        return JPMigakuDatabase.getInstance(context)
    }
    
    @Provides
    fun provideVocabularyDao(db: JPMigakuDatabase) = db.vocabularyDao()
    
    @Provides
    fun provideKanjiDao(db: JPMigakuDatabase) = db.kanjiDao()
    
    @Provides
    fun provideDeckDao(db: JPMigakuDatabase) = db.deckDao()
    
    @Provides
    fun provideSRSHistoryDao(db: JPMigakuDatabase) = db.srsHistoryDao()
    
    @Provides
    fun provideStreakDao(db: JPMigakuDatabase) = db.streakDao()
}
```

---

## 📊 REPOSITORIES (Data Abstraction)

### VocabularyRepository

```kotlin
// INTERFACE
interface IVocabularyRepository {
    suspend fun addVocabulary(vocabulary: VocabularyEntity)
    suspend fun searchVocabulary(query: String, limit: Int = 50, offset: Int = 0): List<VocabularyEntity>
    suspend fun getVocabularyForReview(deckId: Long, limit: Int = 20): List<VocabularyEntity>
    suspend fun updateAfterReview(vocabularyId: String, srsUpdate: SRSUpdate)
    fun watchVocabularyByDeck(deckId: Long): Flow<List<VocabularyEntity>>
}

// IMPLEMENTATION
@Singleton
class VocabularyRepository @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    private val srsHistoryDao: SRSHistoryDao,
    private val database: JPMigakuDatabase
) : IVocabularyRepository {
    
    override suspend fun addVocabulary(vocabulary: VocabularyEntity) = 
        withContext(Dispatchers.IO) {
            require(vocabulary.japanese.isNotBlank()) { "Japanese text is required" }
            require(vocabulary.spanishMeaning.isNotBlank()) { "Spanish meaning is required" }
            require(vocabulary.japanese.length <= 200) { "Japanese text is too long" }
            require(vocabulary.spanishMeaning.length <= 200) { "Spanish meaning is too long" }
            vocabularyDao.insert(vocabulary)
        }
    
    override suspend fun searchVocabulary(
        query: String,
        limit: Int,
        offset: Int
    ): List<VocabularyEntity> = withContext(Dispatchers.IO) {
        // Índices hacen que esto sea rápido (<50ms)
        vocabularyDao.searchPaginated(query, limit, offset)
    }
    
    override suspend fun getVocabularyForReview(
        deckId: Long,
        limit: Int
    ): List<VocabularyEntity> = withContext(Dispatchers.IO) {
        vocabularyDao.getVocabularyDueForReview(deckId, limit)
    }
    
    override suspend fun updateAfterReview(
        vocabularyId: String,
        srsUpdate: SRSUpdate
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            vocabularyDao.updateAfterReview(
                id = vocabularyId,
                newInterval = srsUpdate.newInterval,
                newEaseFactor = srsUpdate.newEaseFactor,
                newRepetitions = srsUpdate.newRepetitions,
                lastReviewed = System.currentTimeMillis()
            )
            
            srsHistoryDao.insert(
                SRSHistoryEntity(
                    vocabularyId = vocabularyId,
                    reviewDate = System.currentTimeMillis(),
                    isCorrect = srsUpdate.isCorrect,
                    oldInterval = srsUpdate.previousInterval,
                    newInterval = srsUpdate.newInterval,
                    oldEaseFactor = srsUpdate.previousEaseFactor,
                    newEaseFactor = srsUpdate.newEaseFactor
                )
            )
        }
    }
    
    override fun watchVocabularyByDeck(deckId: Long): Flow<List<VocabularyEntity>> {
        return vocabularyDao.watchVocabularyByDeck(deckId)
    }
}

// HILT Injection
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Singleton
    @Provides
    fun provideVocabularyRepository(
        vocabularyDao: VocabularyDao,
        srsHistoryDao: SRSHistoryDao,
        database: JPMigakuDatabase
    ): IVocabularyRepository {
        return VocabularyRepository(vocabularyDao, srsHistoryDao, database)
    }
}
```

### Similar para KanjiRepository, DeckRepository, SRSRepository

```kotlin
// PATRÓN SIMILAR:
// - Interface definida
// - Implementation con @Singleton + @Inject
// - Hilt Module con @Provides
// - withContext(Dispatchers.IO) para DB queries
```

---

## 🔄 TRANSACCIONES Y CONSISTENCY

```kotlin
// Ejemplo: Actualizar vocabulary + registrar histórico (atómico)
suspend fun recordReviewAndUpdate(
    vocabularyId: String,
    srsUpdate: SRSUpdate
) = withContext(Dispatchers.IO) {
    database.withTransaction {
        // Si alguna falla, ROLLBACK ambas
        vocabularyDao.updateAfterReview(...)
        srsHistoryDao.insert(...)
    }
}

// WAL mode (Write-Ahead Logging) en Room garantiza:
// - Lecturas no bloquean escrituras
// - Escrituras no bloquean lecturas
// - Atomicidad de transacciones
```

---

## 📈 ÍNDICES PARA PERFORMANCE

```sql
-- Autocreados por Room @Entity:

CREATE INDEX idx_vocab_japanese ON vocabulary(japanese);
-- Optimiza: WHERE japanese LIKE 'query%'

CREATE INDEX idx_vocab_spanish ON vocabulary(spanish_meaning);
-- Optimiza: WHERE spanish_meaning LIKE 'query%'

CREATE INDEX idx_vocab_srs ON vocabulary(deck_id, last_reviewed);
-- Optimiza: getVocabularyDueForReview() query

CREATE INDEX idx_kanji_char ON kanji(kanji);
CREATE INDEX idx_kanji_meaning ON kanji(spanish_meaning);

-- INDICES = Performance garantizado para búsqueda
```

---

## 🧪 DATA LAYER TESTING

```kotlin
// Pseudocódigo para tests

class VocabularyRepositoryTest {
    
    @Test
    fun testSearchVocabulary_ReturnsFiltered() {
        // Dado: 100 vocabularios en DB
        // Cuando: search("agua")
        // Entonces: retorna solo los con "agua" en spanish_meaning
    }
    
    @Test
    fun testUpdateAfterReview_UpdatesAndRecordsHistory() {
        // Dado: vocabulary con interval=1
        // Cuando: updateAfterReview(isCorrect=true) calcula nuevo interval
        // Entonces: vocabulary.interval actualizado + SRSHistory insertado
    }
    
    @Test
    fun testSearchPerformance_LessThan50ms() {
        // Dado: 50k vocabularios en DB con índices
        // Cuando: search("水")
        // Entonces: <50ms (índice LIKE funciona)
    }
}
```

---

## 🎯 PRÓXIMOS PASOS

1. Implementar entities exactas (copiar pseudocódigo)
2. Generar DAOs (Copilot puede ayudar)
3. Crear Database singleton
4. Implementar repositories
5. Setup Hilt injection

**Próximo documento**: [`02-DOMAIN-LAYER.md`](02-DOMAIN-LAYER.md) - Use cases con SM-2 algorithm
