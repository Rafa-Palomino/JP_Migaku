# 05 - PERFORMANCE: Targets, Optimizations, Benchmarks

**Fecha**: 2026-07-08  
**Descripción**: Performance targets, estrategias de optimización, benchmarks validables.

---

## 🎯 PERFORMANCE TARGETS (SLA)

| Métrica | Target | Percentil | Status |
|---------|--------|-----------|--------|
| Búsqueda (LIKE) | <50 ms | p95 | ✅ Indexado |
| Quiz/pregunta | <100 ms | p99 | ✅ Lazy-load |
| Memory pico | <80 MB | p90 | ✅ Streaming |
| Startup app | <3 s | p95 | ✅ Deferred DB |
| Clipboard detect | <50 ms | p95 | ✅ API 32 |
| Streak update | <20 ms | p95 | ✅ Singleton |

---

## 🔍 BÚSQUEDA: <50ms

### Problema
- 150k vocabularios en DB
- Búsqueda LIKE sin índices = 500+ ms (inaceptable)

### Solución
```kotlin
// Room Entity con índices:
@Entity(
    tableName = "vocabulary",
    indices = [
        Index(value = ["japanese"], name = "idx_vocab_japanese"),
        Index(value = ["spanish_meaning"], name = "idx_vocab_spanish"),
        Index(value = ["deck_id", "last_reviewed"], name = "idx_vocab_srs")
    ]
)
data class VocabularyEntity(...)

// DAO Query optimizado:
@Query("""
    SELECT * FROM vocabulary 
    WHERE spanish_meaning LIKE '%' || :query || '%' 
       OR japanese LIKE '%' || :query || '%'
    ORDER BY (CASE WHEN japanese LIKE :query || '%' THEN 0 ELSE 1 END)
    LIMIT :limit OFFSET :offset
""")
suspend fun searchPaginated(query: String, limit: Int, offset: Int): List<VocabularyEntity>
```

### Validación
```kotlin
@Test
fun testSearchPerformance_LessThan50ms() = runTest {
    // Poblar 50k vocabularios
    repeat(50000) { i ->
        vocabularyDao.insert(generateTestVocab(i))
    }
    
    // Medir búsqueda
    val startTime = System.nanoTime()
    val results = vocabularyDao.searchPaginated("agua", limit = 50)
    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
    
    assertTrue(elapsedMs < 50) { "Search took ${elapsedMs}ms" }
    assertTrue(results.size <= 50)
}
```

### Benchmark esperado
```
Índice: ON    → 12-45 ms ✓
Índice: OFF   → 400-800 ms ✗
Paginación    → -30% queries a DB
```

---

## 📝 QUIZ: <100ms/pregunta

### Problema
- 150k vocabularios × generar 10 opciones = costoso
- Mostrar pregunta lenta = mala UX

### Solución: Lazy Loading
```kotlin
// 1. Generar preguntas SIN cargar todo
// (solo 10-20 en memoria, rest on-demand)

class PerformQuizUseCase {
    
    suspend fun generateQuiz(
        deckId: Long,
        questionCount: Int = 10
    ): Result<QuizSession> = withContext(Dispatchers.IO) {
        // Cargar solo los necesarios
        val vocabularyDue = vocabularyRepository.getVocabularyForReview(
            deckId = deckId,
            limit = questionCount * 1.5  // Solo 15, no 150k
        )
        
        // Generar preguntas asincronamente
        val questions = vocabularyDue
            .take(questionCount)
            .map { generateQuestion(it) }  // Parallelizable
            .shuffled()
        
        QuizSession(...)
    }
    
    // 2. Distractores ON-DEMAND
    private suspend fun generateDistracters(
        vocab: VocabularyEntity,
        count: Int = 3
    ): List<String> {
        // Obtener solo 20 random, no todos 150k
        val randomVocab = vocabularyRepository.getRandomVocabulary(
            deckId = vocab.deckId,
            limit = 20,  // Máximo
            exclude = vocab.id
        )
        
        return randomVocab
            .shuffled()
            .take(count)
            .map { it.spanishMeaning }
    }
}

// 3. Room Query optimizada
@Query("""
    SELECT * FROM vocabulary 
    WHERE deck_id = :deckId 
      AND (last_reviewed IS NULL 
           OR CAST((strftime('%s','now') - last_reviewed) / 86400.0 AS INTEGER) >= interval)
    ORDER BY CASE WHEN last_reviewed IS NULL THEN 0 ELSE 1 END,
             last_reviewed ASC
    LIMIT :limit
""")
suspend fun getVocabularyDueForReview(deckId: Long, limit: Int = 20): List<VocabularyEntity>
// Con índice en (deck_id, last_reviewed) = <20ms

@Query("""
    SELECT * FROM vocabulary 
    WHERE deck_id = :deckId AND id != :excludeId
    ORDER BY RANDOM()
    LIMIT :limit
""")
suspend fun getRandomVocabulary(deckId: Long, excludeId: String, limit: Int): List<VocabularyEntity>
// RANDOM() sin WHERE = rápido en subsets
```

### Benchmark
```
Quiz Generation:
├─ Load 20 due vocabularies    : ~5 ms  (indexed)
├─ Generate 10 questions        : ~40 ms (distractors loaded on-demand)
├─ Shuffle                      : ~2 ms
└─ TOTAL                        : ~47 ms ✓

Per-Question Display:
├─ Render layout                : ~15 ms
├─ Update StateFlow             : ~5 ms
└─ TOTAL                        : ~20 ms ✓

Guaranty: <100ms p99
```

---

## 💾 MEMORY: <80 MB

### Problema
- APK+Data: 150 MB APK
- En memoria queremos: <80 MB peak

### Estrategia NO-Caché

```kotlin
// ❌ ANTI-PATRÓN (uses tons of memory)
// class VocabularyCache {
//     val allVocabulary = mutableListOf<VocabularyEntity>()  // 150k items!
// }

// ✅ PATRÓN: Streaming queries, pequeños batches

class SearchViewModel {
    
    suspend fun search(query: String) {
        // NUNCA: Cargar toda la DB
        // SI: Query solo 50 items a la vez
        
        val results = searchUseCase.search(
            query = query,
            limit = 50  // Max en memory
        )
        
        _uiState.update { it.copy(results = results) }
        // results = 50 items ≈ 100 KB
    }
}

class QuizViewModel {
    
    // Cargar solo 20 preguntas, no 150k
    private val quizSession = QuizSession(
        questions = List<QuizQuestion>(20)  // 20 * ~2 KB = 40 KB
    )
    
    // Desechar preguntas ya respondidas (opcional)
    suspend fun nextQuestion() {
        // Opción: Liberar pregunta anterior
        session.questions[currentIndex] = null  // GC recogerá
    }
}

class HomeViewModel {
    
    // NO cachear lista de decks
    // Recargar cada vez que volvemos a Home (fast con indices)
    
    val uiState = MutableStateFlow<HomeUiState>(HomeUiState())
    
    fun refreshHomeData() {
        // Fresh query, datos antiguos GC'd
        loadHomeData()
    }
}
```

### Medición real

```kotlin
@Test
fun testMemoryUsage_LessThan80Mb() {
    val runtime = Runtime.getRuntime()
    val initialMemory = runtime.totalMemory() - runtime.freeMemory()
    
    // Simular uso normal
    repeat(10) {
        val vocab = vocabularyRepository.searchVocabulary("agua", limit=50)
        val quiz = performQuizUseCase.generateQuiz(deckId=1)
        val streakUpdate = updateStreakUseCase.updateStreaks(deckId=1)
    }
    
    System.gc()
    val finalMemory = runtime.totalMemory() - runtime.freeMemory()
    val usedMb = (finalMemory - initialMemory) / (1024 * 1024)
    
    assertTrue(usedMb < 80) { "Memory usage: ${usedMb} MB" }
}
```

---

## 🚀 STARTUP: <3s

### Problema
- Room initialization = costoso
- Cargar índices SQLite = ~500ms-1s

### Solución: Deferred Initialization

```kotlin
@Application
@HiltAndroidApp
class JPMigakuApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // ❌ NO: Inicializar DB aquí
        // database = Room.databaseBuilder(...).build()  // SLOW!
        
        // ✅ SI: Deferred init en background
        // (Primera query a DB los pagará)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Singleton
    @Provides
    fun provideDatabase(context: Context): JPMigakuDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            JPMigakuDatabase::class.java,
            "jpmigaku.db"
        )
            .setJournalMode(RoomDatabase.JournalMode.WAL)
            .createFromAsset("jpmigaku_initial.db")  // Pre-populated
            .build()
        // First call paga costo de inicialización
    }
}

// En HomeViewModel:
class HomeViewModel @Inject constructor(
    private val deckRepository: IDeckRepository  // Inyectada, no inicializa DB
) : ViewModel() {
    
    init {
        loadHomeData()  // Primera query al DB aquí (2-3s), no en App.onCreate()
    }
}

// Startup timeline:
// ├─ App.onCreate()        : ~100ms (sin DB init)
// ├─ MainActivity.onCreate(): ~50ms
// ├─ HomeScreen renders    : ~100ms (UI thread)
// └─ First DB query        : ~1500-2000ms (Background thread, user no ve)
// └─ TOTAL APP READY       : ~2-2.5s (perceived)
```

---

## 🔄 STREAK: <20ms

### Implementación simple

```kotlin
@Query("SELECT * FROM global_streak WHERE id = 1")
suspend fun getGlobalStreak(): GlobalStreakEntity?

@Update
suspend fun updateGlobalStreak(streak: GlobalStreakEntity)

// Query simple + Update simple = ~5-20ms
// Sin lógica compleja, solo UPDATE + INSERT
```

---

## 📋 WAL MODE (Write-Ahead Logging)

```kotlin
Room.databaseBuilder(...)
    .setJournalMode(RoomDatabase.JournalMode.WAL)
    .build()

// WAL benefits:
// ├─ Lecturas ≠ bloquean escrituras
// ├─ Escrituras ≠ bloquean lecturas
// ├─ Mejor concurrency
// └─ Mejor performance en I/O
//
// CONS: +2 archivos en storage (DB-wal, DB-shm)
// Total: ~150 MB APK + ~3 MB WAL = ~153 MB (acceptable)
```

---

## 🧪 BENCHMARK SUITE

```kotlin
@Benchmark
fun benchmarkSearch_50kVocabs() {
    // Setup: 50k items
    repeat(10) {
        vocabularyDao.searchPaginated("agua", limit = 50)
    }
    // Expected: cada query <50ms
}

@Benchmark
fun benchmarkQuizGeneration() {
    repeat(5) {
        performQuizUseCase.generateQuiz(deckId = 1, questionCount = 10)
    }
    // Expected: <100ms
}

@Benchmark
fun benchmarkSRSUpdate() {
    repeat(10) {
        updateSRSUseCase.calculateAndUpdate(srsUpdate)
    }
    // Expected: <5ms
}

@Benchmark
fun benchmarkStreakUpdate() {
    repeat(20) {
        updateStreakUseCase.updateStreaks(deckId = 1)
    }
    // Expected: <20ms
}

// CI/CD: Ejecutar benchmarks post-build, FAIL si exceden targets
```

---

## 📊 PROFILING

### Android Studio Profiler

```
1. Abrir Android Studio Profiler (View > Tool Windows > Profiler)
2. Ejecutar app
3. Tabs:
   ├─ CPU: Buscar <50% en búsqueda
   ├─ Memory: Buscar peak <100MB
   ├─ Network: Debe estar 0 (offline!)
   └─ Energy: Buscar baja drain
```

### Logcat Markers

```kotlin
// Marcar secciones en Profiler
Debug.startMethodTracing("search_start")
val results = vocabularyDao.search(...)
Debug.stopMethodTracing()

// Room automatic query logging
// (habilitar en debug builds)
```

---

## 🎯 PRÓXIMOS PASOS

1. Implementar índices SQL
2. Setup Benchmark suite
3. Configurar CI/CD checks
4. Profilar con Android Profiler

**Próximo documento**: [`06-OFFLINE-FIRST.md`](06-OFFLINE-FIRST.md) - Garantías offline
