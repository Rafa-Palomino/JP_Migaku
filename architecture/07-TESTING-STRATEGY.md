# 07 - TESTING STRATEGY: Pyramid, Test Cases, Validation

**Fecha**: 2026-07-08  
**Descripción**: Testing pyramid (unit/integration/E2E), test data factories, cobertura crítica.

---

## 🏰 TESTING PYRAMID

```
                     ▲
                    ╱ ╲
                   ╱   ╲  E2E (5%)
                  ╱─────╲ 
                 ╱       ╲
                ╱         ╲
               ╱───────────╲ Integration (25%)
              ╱             ╲
             ╱               ╲
            ╱─────────────────╲
           ╱                   ╲
          ╱                     ╲
         ╱───────────────────────╲  Unit (70%)
        ╱                         ╲

DISTRIBUCIÓN:
├─ Unit Tests (70%): Lógica pura, <1s cada una
├─ Integration (25%): DB + UseCase, 1-5s cada
└─ E2E (5%): Flujos completos, 10-60s cada

TOTAL: ~200 tests, ~10-15 minutos en CI/CD
```

---

## 🧪 UNIT TESTS (70%)

### 1. Domain Layer (Crítico)

```kotlin
// test/java/com/migaku/jpmigaku/domain/usecase/

class UpdateSRSUseCaseTest {
    
    private lateinit var updateSRSUseCase: UpdateSRSUseCase
    private lateinit var mockSrsRepo: ISRSRepository
    
    @Before
    fun setup() {
        mockSrsRepo = mock()
        updateSRSUseCase = UpdateSRSUseCase(mockSrsRepo)
    }
    
    @Test
    fun testSM2_FirstReview_IntervalIs1() {
        // Dado: vocab nuevo
        val srsUpdate = SRSUpdate(
            vocabularyId = "vocab-1",
            isCorrect = true,
            previousInterval = 0,
            previousEaseFactor = 2.5,
            previousRepetitions = 0,
            newInterval = 0,  // Sin calcular
            newEaseFactor = 2.5,
            newRepetitions = 0
        )
        
        // Cuando: primer review correctamente
        val result = runBlocking {
            updateSRSUseCase.calculateAndUpdate(srsUpdate)
        }
        
        // Entonces: interval = 1
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.newInterval)
    }
    
    @Test
    fun testSM2_SecondReview_IntervalIs3() {
        // Dado: vocab con repetitions=1, interval=1
        val srsUpdate = SRSUpdate(
            vocabularyId = "vocab-1",
            isCorrect = true,
            previousInterval = 1,
            previousEaseFactor = 2.5,
            previousRepetitions = 1,
            newInterval = 0,
            newEaseFactor = 2.5,
            newRepetitions = 1
        )
        
        // Cuando: segundo review
        val result = runBlocking {
            updateSRSUseCase.calculateAndUpdate(srsUpdate)
        }
        
        // Entonces: interval = 3
        assertEquals(3, result.getOrNull()?.newInterval)
    }
    
    @Test
    fun testSM2_ThirdReview_IntervalMultiplied() {
        // Dado: vocab con repetitions=2, interval=3, ease=2.5
        val srsUpdate = SRSUpdate(
            vocabularyId = "vocab-1",
            isCorrect = true,
            previousInterval = 3,
            previousEaseFactor = 2.5,
            previousRepetitions = 2,
            newInterval = 0,
            newEaseFactor = 2.5,
            newRepetitions = 2
        )
        
        // Cuando: tercer review
        val result = runBlocking {
            updateSRSUseCase.calculateAndUpdate(srsUpdate)
        }
        
        // Entonces: interval ≈ 7 (3 * 2.5)
        val newInterval = result.getOrNull()?.newInterval ?: 0
        assertTrue(newInterval >= 7 && newInterval <= 8)
    }
    
    @Test
    fun testSM2_WrongAnswer_ResetInterval() {
        // Dado: vocab con interval=14
        val srsUpdate = SRSUpdate(
            vocabularyId = "vocab-1",
            isCorrect = false,  // INCORRECTO
            previousInterval = 14,
            previousEaseFactor = 2.5,
            previousRepetitions = 5,
            newInterval = 0,
            newEaseFactor = 2.5,
            newRepetitions = 5
        )
        
        // Cuando: revisar incorrectamente
        val result = runBlocking {
            updateSRSUseCase.calculateAndUpdate(srsUpdate)
        }
        
        // Entonces: interval = 1, ease disminuye
        assertEquals(1, result.getOrNull()?.newInterval)
        assertTrue((result.getOrNull()?.newEaseFactor ?: 2.5) < 2.5)
    }
}

class PerformQuizUseCaseTest {
    
    @Test
    fun testGenerateQuiz_ReturnsCorrectCount() {
        // Dado: mock repo con 50 vocabularios
        val mockVocabRepo = mock<IVocabularyRepository>()
        every { 
            runBlocking { mockVocabRepo.getVocabularyForReview(any(), any()) }
        } returns listOf(
            generateMockVocabulary(id = "1"),
            generateMockVocabulary(id = "2"),
            // ...
        )
        
        val useCase = PerformQuizUseCase(mockVocabRepo, mockKanjiRepo, mockSrsRepo)
        
        // Cuando: generate 10 preguntas
        val result = runBlocking {
            useCase.generateQuiz(deckId = 1, questionCount = 10)
        }
        
        // Entonces: retorna exactamente 10
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.questions?.size)
    }
    
    @Test
    fun testGenerateQuestion_HasCorrectAnswer() {
        // Cada pregunta debe tener exactamente 1 opción correcta
        val useCase = PerformQuizUseCase(...)
        val vocab = generateMockVocabulary()
        
        val question = useCase.generateQuestion(vocab, index = 0)
        
        // Contar cuántas opciones = correctSpanish
        val correctCount = question.options.count { 
            it == question.correctSpanish 
        }
        assertEquals(1, correctCount)
    }
}

class DetectClipboardUseCaseTest {
    
    @Test
    fun testDetectJapanese_ExtractsHiragana() {
        val useCase = DetectClipboardUseCase()
        
        val result = runBlocking {
            useCase.detectAndExtractJapanese("水 (みず) agua")
        }
        
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.japaneseText?.contains("水") ?: false)
        assertTrue(result.getOrNull()?.japaneseText?.contains("みず") ?: false)
    }
    
    @Test
    fun testDetectJapanese_IgnoresEnglish() {
        val useCase = DetectClipboardUseCase()
        
        val result = runBlocking {
            useCase.detectAndExtractJapanese("Hello world this is English")
        }
        
        assertTrue(result.isFailure)  // No Japanese detected
    }
}

class UpdateStreakUseCaseTest {
    
    @Test
    fun testUpdateStreak_ContinueStreak() {
        // Ayer practicó
        val mockStreakRepo = mock<IStreakRepository>()
        val yesterday = (System.currentTimeMillis() - 86400000) / 86400000
        
        every {
            runBlocking { mockStreakRepo.getGlobalStreak() }
        } returns GlobalStreakEntity(
            id = 1,
            currentStreak = 5,
            lastPracticeDate = yesterday * 86400000
        )
        
        val useCase = UpdateStreakUseCase(mockStreakRepo, mockHistoryRepo)
        
        val result = runBlocking {
            useCase.updateStreaks(deckId = 1)
        }
        
        // Streak debe aumentar
        assertTrue(result.isSuccess)
        assertEquals(6, result.getOrNull()?.newGlobalStreak)
    }
    
    @Test
    fun testUpdateStreak_BreakStreak() {
        // Hace 3 días que no practica
        val mockStreakRepo = mock<IStreakRepository>()
        val threeDaysAgo = (System.currentTimeMillis() - 86400000 * 3) / 86400000
        
        every {
            runBlocking { mockStreakRepo.getGlobalStreak() }
        } returns GlobalStreakEntity(
            id = 1,
            currentStreak = 10,
            lastPracticeDate = threeDaysAgo * 86400000
        )
        
        val useCase = UpdateStreakUseCase(mockStreakRepo, mockHistoryRepo)
        
        val result = runBlocking {
            useCase.updateStreaks(deckId = 1)
        }
        
        // Streak debe resetear
        assertEquals(1, result.getOrNull()?.newGlobalStreak)
    }
}
```

### 2. ViewModel Tests

```kotlin
class HomeViewModelTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Test
    fun testHomeViewModel_LoadsDecksOnInit() = runTest {
        // Setup
        val mockDeckRepo = mock<IDeckRepository>()
        val testDecks = listOf(
            DeckEntity(id = 1, name = "JLPT N5"),
            DeckEntity(id = 2, name = "Kanji")
        )
        
        coEvery {
            mockDeckRepo.getAllDecks()
        } returns testDecks
        
        // Act
        val viewModel = HomeViewModel(mockDeckRepo, mockSrsRepo, mockStreakRepo)
        advanceUntilIdle()
        
        // Assert
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(2, state.decks.size)
    }
}
```

---

## 📦 INTEGRATION TESTS (25%)

```kotlin
// androidTest/java/com/migaku/jpmigaku/integration/

@RunWith(AndroidJUnit4::class)
class VocabularyRepositoryIntegrationTest {
    
    @get:Rule
    val databaseRule = RoomDatabaseRule()
    
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var repository: VocabularyRepository
    
    @Before
    fun setup() {
        val db = databaseRule.database
        vocabularyDao = db.vocabularyDao()
        repository = VocabularyRepository(vocabularyDao, db.srsHistoryDao(), db)
    }
    
    @Test
    fun testSearchVocabulary_ReturnsFilteredResults() = runTest {
        // Setup: Insertar 10 vocabularios
        val vocabs = listOf(
            VocabularyEntity(
                id = "1",
                deckId = 1,
                japanese = "水",
                spanishMeaning = "agua"
            ),
            VocabularyEntity(
                id = "2",
                deckId = 1,
                japanese = "火",
                spanishMeaning = "fuego"
            ),
            // ...
        )
        vocabs.forEach { vocabularyDao.insert(it) }
        
        // Act: Buscar "agua"
        val results = runBlocking {
            repository.searchVocabulary("agua", limit = 50)
        }
        
        // Assert
        assertEquals(1, results.size)
        assertEquals("agua", results.first().spanishMeaning)
    }
    
    @Test
    fun testUpdateAfterReview_UpdatesAndRecordsHistory() = runTest {
        // Setup
        val vocab = VocabularyEntity(
            id = "test-vocab",
            deckId = 1,
            japanese = "水",
            spanishMeaning = "agua",
            interval = 1,
            easeFactor = 2.5,
            repetitions = 0
        )
        vocabularyDao.insert(vocab)
        
        // Act: Update después de review
        val srsUpdate = SRSUpdate(
            vocabularyId = "test-vocab",
            isCorrect = true,
            previousInterval = 1,
            previousEaseFactor = 2.5,
            previousRepetitions = 0,
            newInterval = 3,
            newEaseFactor = 2.6,
            newRepetitions = 1
        )
        runBlocking {
            repository.updateAfterReview("test-vocab", srsUpdate)
        }
        
        // Assert: Vocabulary actualizado
        val updated = runBlocking {
            vocabularyDao.getById("test-vocab")
        }
        assertEquals(3, updated?.interval)
        assertEquals(1, updated?.repetitions)
    }
    
    @Test
    fun testSearchPerformance_LessThan50ms() = runTest {
        // Setup: Poblar 50k vocabs (usando bulk insert)
        val vocabs = (1..50000).map { i ->
            VocabularyEntity(
                id = "vocab-$i",
                deckId = 1,
                japanese = if (i % 3 == 0) "水" else if (i % 3 == 1) "火" else "木",
                spanishMeaning = if (i % 10 == 0) "agua" else "palabra-$i"
            )
        }
        vocabularyDao.insertAll(vocabs)
        
        // Act: Measure search time
        val startTime = System.nanoTime()
        val results = runBlocking {
            repository.searchVocabulary("agua", limit = 50)
        }
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        
        // Assert
        assertTrue(elapsedMs < 50) { "Search took ${elapsedMs}ms" }
        assertEquals(true, results.isNotEmpty())
    }
}

class QuizWorkflowIntegrationTest {
    
    @get:Rule
    val databaseRule = RoomDatabaseRule()
    
    @Test
    fun testQuizWorkflow_CompletesSuccessfully() = runTest {
        // Setup: Insertar datos de prueba
        val db = databaseRule.database
        // ... setup vocabs
        
        // Act: Ejecutar flujo completo
        val performQuizUseCase = PerformQuizUseCase(...)
        val updateSRSUseCase = UpdateSRSUseCase(...)
        
        val quiz = performQuizUseCase.generateQuiz(deckId = 1, questionCount = 5)
        assertTrue(quiz.isSuccess)
        
        // Answer 3 correctamente, 2 incorrectamente
        // ... simular respuestas
        
        val result = performQuizUseCase.finishQuiz(quiz.getOrNull()!!)
        assertTrue(result.isSuccess)
        
        // Assert
        val resultData = result.getOrNull()!!
        assertEquals(5, resultData.totalQuestions)
        assertEquals(3, resultData.correctCount)
        assertEquals(0.6f, resultData.accuracy)
    }
}
```

---

## 🚀 E2E TESTS (5%)

```kotlin
// androidTest/java/com/migaku/jpmigaku/e2e/

@RunWith(AndroidJUnit4::class)
class ClipboardToQuizE2ETest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Test
    fun testCompleteWorkflow_ClipboardAddToQuiz() {
        // 1. Simular clipboard
        val clipboardManager = composeTestRule.activity
            .getSystemService(ClipboardManager::class.java)
        
        val clip = ClipData.newPlainText("test", "水 (みず) agua")
        clipboardManager.setPrimaryClip(clip)
        
        // 2. Navegar a Clipboard dialog
        composeTestRule.onNodeWithText("Agregar vocabulario").performClick()
        
        // 3. Rellenar y guardar
        composeTestRule.onNodeWithTag("input_japanese").performTextInput("水")
        composeTestRule.onNodeWithTag("input_reading").performTextInput("みず")
        composeTestRule.onNodeWithTag("input_spanish").performTextInput("agua")
        composeTestRule.onNodeWithTag("btn_save").performClick()
        
        // 4. Verificar que apareció en Home
        composeTestRule.onNodeWithText("✓ Guardado").assertIsDisplayed()
        
        // 5. Iniciar quiz
        composeTestRule.onNodeWithText("Comenzar Quiz").performClick()
        
        // 6. Resolver la pregunta
        composeTestRule.onNodeWithText("agua").performClick()
        composeTestRule.onNodeWithText("Siguiente").performClick()
        
        // 7. Verificar resultado
        composeTestRule.onNodeWithText("70%").assertIsDisplayed()
    }
}
```

---

## 🏭 TEST DATA FACTORY

```kotlin
// test/java/com/migaku/jpmigaku/test/

object TestDataFactory {
    
    fun generateTestVocabulary(
        id: String = UUID.randomUUID().toString(),
        japanese: String = "水",
        reading: String = "みず",
        spanish: String = "agua"
    ) = VocabularyEntity(
        id = id,
        deckId = 1,
        japanese = japanese,
        reading = reading,
        spanishMeaning = spanish,
        createdAt = System.currentTimeMillis(),
        interval = 1,
        easeFactor = 2.5,
        repetitions = 0
    )
    
    fun generateTestDeck(
        id: Long = 1,
        name: String = "JLPT N5"
    ) = DeckEntity(
        id = id,
        name = name,
        createdAt = System.currentTimeMillis()
    )
    
    fun generateTestSRSUpdate(
        vocabularyId: String = "vocab-1",
        isCorrect: Boolean = true
    ) = SRSUpdate(
        vocabularyId = vocabularyId,
        isCorrect = isCorrect,
        previousInterval = 1,
        previousEaseFactor = 2.5,
        previousRepetitions = 0,
        newInterval = 3,
        newEaseFactor = 2.6,
        newRepetitions = 1
    )
}
```

---

## 🧪 COBERTURA CRÍTICA

```
ARCHIVOS CRÍTICOS (100% cobertura):
├─ UpdateSRSUseCase.kt           (lógica SM-2)
├─ UpdateStreakUseCase.kt        (lógica streak)
├─ DetectClipboardUseCase.kt     (detección Japanese)
└─ PerformQuizUseCase.kt         (generación quiz)

ARCHIVOS IMPORTANTES (>80% cobertura):
├─ VocabularyRepository.kt
├─ HomeViewModel.kt
├─ QuizViewModel.kt
└─ DAOs (Room generated)

ARCHIVOS UI (<60% cobertura acceptable):
├─ Screens (Compose)
├─ Navigation
└─ Components (manual testing prioritized)
```

---

## 🔄 CI/CD PIPELINE

```bash
#!/bin/bash
# .github/workflows/test.yml (pseudo)

name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: 17
      
      - name: Unit Tests
        run: ./gradlew test
        
      - name: Integration Tests
        run: ./gradlew connectedAndroidTest
        
      - name: Coverage Report
        run: ./gradlew jacocoTestReport
        
      - name: Offline Validation
        run: ./ci-offline-check.sh
        
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
        with:
          files: ./coverage/cobertura.xml
          
    # FAIL si:
    # - Coverage < 70% (domain layer)
    # - Tests fail
    # - Offline validation fails
```

---

## 🎯 PRÓXIMOS PASOS

1. Crear test fixtures/factories
2. Implementar unit tests (Domain)
3. Implementar integration tests (DB)
4. Implementar E2E tests (UI)
5. Setup CI/CD pipeline con coverage gates

---

**STATUS**: Arquitectura completa documentada ✅  
**PRÓXIMO**: Iniciar Sprint 1 - Implementación con Copilot CLI
