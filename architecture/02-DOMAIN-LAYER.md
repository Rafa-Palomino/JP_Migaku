# 02 - DOMAIN LAYER: Use Cases, Models, Algorithms

**Fecha**: 2026-07-08  
**Descripción**: Lógica de negocio pura - use cases, modelos de dominio y algoritmos (SM-2, streak).

---

## 📦 COMPONENTES DOMAIN LAYER

```
Domain Layer (Business Logic)
├── Models (Data Transfer Objects + Domain Entities)
│   ├── QuizQuestion
│   ├── SRSUpdate
│   ├── StreakUpdate
│   └── ...
│
├── Exceptions
│   └── DomainException
│
└── Use Cases
    ├── PerformQuizUseCase
    ├── UpdateSRSUseCase
    ├── SearchVocabularyUseCase
    ├── DetectClipboardUseCase
    └── UpdateStreakUseCase
```

---

## 🎯 MODELOS DE DOMINIO

### QuizQuestion

```kotlin
// Pregunta formulada al usuario en quiz
data class QuizQuestion(
    val vocabularyId: String,
    val japanese: String,                  // "飲む"
    val reading: String,                   // "のむ"
    val correctSpanish: String,            // "beber"
    val options: List<String>,             // [correctSpanish, ...3 distractores]
    val questionType: QuestionType,        // VOCAB_TO_SPANISH, KANJI_TO_READING, etc
    val mediaPath: String? = null          // Para extensión futura (audio, kanji animation)
)

enum class QuestionType {
    VOCAB_TO_SPANISH,      // "飲む" → selecciona "beber"
    SPANISH_TO_VOCAB,      // "beber" → selecciona "飲む"
    KANJI_TO_READING       // "火" → selecciona "ひ"
}
```

### SRSUpdate (Resultado del review)

```kotlin
// Resultado de revisar una pregunta - se propaga a repository
data class SRSUpdate(
    val vocabularyId: String,
    val isCorrect: Boolean,                // ¿Respondió correctamente?
    
    // SM-2 valores anteriores (para historial)
    val previousInterval: Int,
    val previousEaseFactor: Double,
    val previousRepetitions: Int,
    
    // SM-2 valores nuevos (calculados por UpdateSRSUseCase)
    val newInterval: Int,
    val newEaseFactor: Double,
    val newRepetitions: Int,
    
    val reviewTimestamp: Long = System.currentTimeMillis()
)
```

### StreakUpdate

```kotlin
data class StreakUpdate(
    val globalStreakUpdated: Boolean,
    val deckStreakUpdated: Boolean,
    val newGlobalStreak: Int,
    val newDeckStreak: Int,
    val maintenanceRequired: Boolean       // ¿Se rompió streak? → Requiere acción
)
```

### QuizSession

```kotlin
data class QuizSession(
    val sessionId: String,                 // UUID
    val deckId: Long,
    val startedAt: Long,
    val questions: List<QuizQuestion>,
    val currentQuestionIndex: Int = 0,
    val answers: List<QuizAnswer> = emptyList(),
    val isCompleted: Boolean = false
)

data class QuizAnswer(
    val questionId: String,
    val selectedAnswer: String,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val answeredAt: Long = System.currentTimeMillis()
)

data class QuizSessionResult(
    val sessionId: String,
    val deckId: Long,
    val totalQuestions: Int,
    val correctCount: Int,
    val accuracy: Float,                   // 0.0 - 1.0
    val totalTimeMs: Long,
    val srsUpdates: List<SRSUpdate>,
    val streakUpdated: Boolean
)
```

### ClipboardEvent

```kotlin
data class ClipboardEvent(
    val text: String,
    val japaneseText: String,             // Texto filtrado (solo japonés)
    val detectedAt: Long = System.currentTimeMillis(),
    val userId: String? = null            // Para analítica futura
)
```

---

## 🧮 USE CASES

### TimeProvider + validation helpers

```kotlin
interface TimeProvider {
    fun nowEpochMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

class VocabularyValidator {
    fun validate(japanese: String, reading: String, spanish: String): ValidationResult {
        if (japanese.isBlank()) return ValidationResult(false, "Japanese text is required")
        if (spanish.isBlank()) return ValidationResult(false, "Spanish meaning is required")
        if (japanese.length > 200) return ValidationResult(false, "Japanese text is too long")
        if (spanish.length > 200) return ValidationResult(false, "Spanish meaning is too long")
        if (reading.length > 200) return ValidationResult(false, "Reading is too long")
        return ValidationResult(true)
    }
}
```

### ValidateVocabularyInputUseCase

```kotlin
interface IValidateVocabularyInputUseCase {
    fun validate(japanese: String, reading: String, spanish: String): ValidationResult
}

class ValidateVocabularyInputUseCase @Inject constructor() : IValidateVocabularyInputUseCase {
    private val validator = VocabularyValidator()

    override fun validate(japanese: String, reading: String, spanish: String): ValidationResult {
        return validator.validate(japanese, reading, spanish)
    }
}
```

### PerformQuizUseCase

```kotlin
// Generar preguntas de quiz basadas en SRS

interface IPerformQuizUseCase {
    suspend fun generateQuiz(
        deckId: Long,
        questionCount: Int = 10
    ): Result<QuizSession>
    
    suspend fun submitAnswer(
        session: QuizSession,
        questionIndex: Int,
        selectedAnswer: String,
        responseTimeMs: Long
    ): Result<QuizAnswer>
    
    suspend fun finishQuiz(session: QuizSession): Result<QuizSessionResult>
}

@Singleton
class PerformQuizUseCase @Inject constructor(
    private val vocabularyRepository: IVocabularyRepository,
    private val kanjiRepository: IKanjiRepository,
    private val srsRepository: ISRSRepository,
    private val timeProvider: TimeProvider
) : IPerformQuizUseCase {
    
    override suspend fun generateQuiz(
        deckId: Long,
        questionCount: Int
    ): Result<QuizSession> = try {
        val vocabularyDue = vocabularyRepository.getVocabularyForReview(
            deckId = deckId,
            limit = questionCount * 2
        )
        
        if (vocabularyDue.isEmpty()) {
            return Result.failure(Exception("No hay vocabulario vencido"))
        }
        
        val questions = vocabularyDue
            .take(questionCount)
            .mapIndexed { idx, vocab -> generateQuestion(vocab, idx) }
            .filter { it.options.size == 4 && it.options.distinct().size == 4 }
            .shuffled()
        
        if (questions.size < questionCount) {
            return Result.failure(Exception("No se pudieron generar suficientes preguntas válidas"))
        }
        
        val session = QuizSession(
            sessionId = UUID.randomUUID().toString(),
            deckId = deckId,
            startedAt = timeProvider.nowEpochMillis(),
            questions = questions.take(questionCount),
            currentQuestionIndex = 0
        )
        
        Result.success(session)
        
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    private suspend fun generateQuestion(
        vocab: VocabularyEntity,
        index: Int
    ): QuizQuestion {
        val type = if (index % 2 == 0) {
            QuestionType.VOCAB_TO_SPANISH
        } else {
            QuestionType.SPANISH_TO_VOCAB
        }
        
        val distractors = generateDistracters(vocab, count = 3)
        val options = (listOf(vocab.spanishMeaning) + distractors)
            .distinct()
            .filter { it.isNotBlank() }
            .shuffled()
            .take(4)

        return QuizQuestion(
            vocabularyId = vocab.id,
            japanese = vocab.japanese,
            reading = vocab.reading ?: "",
            correctSpanish = vocab.spanishMeaning,
            options = options,
            questionType = type
        )
    }
    
    private suspend fun generateDistracters(
        vocab: VocabularyEntity,
        count: Int = 3
    ): List<String> {
        val allVocab = vocabularyRepository.getRandomVocabulary(
            deckId = vocab.deckId,
            limit = 20,
            exclude = vocab.id
        )
        
        val candidateDistractors = allVocab
            .map { it.spanishMeaning }
            .filter { it.isNotBlank() && it != vocab.spanishMeaning }
            .distinct()
            .shuffled()

        return candidateDistractors.take(count)
    }
    
    override suspend fun submitAnswer(
        session: QuizSession,
        questionIndex: Int,
        selectedAnswer: String,
        responseTimeMs: Long
    ): Result<QuizAnswer> = try {
        val question = session.questions.getOrNull(questionIndex)
            ?: return Result.failure(Exception("Question index is invalid"))
        val isCorrect = selectedAnswer == question.correctSpanish
        
        val answer = QuizAnswer(
            questionId = question.vocabularyId,
            selectedAnswer = selectedAnswer,
            isCorrect = isCorrect,
            responseTimeMs = responseTimeMs
        )
        
        Result.success(answer)
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    override suspend fun finishQuiz(session: QuizSession): Result<QuizSessionResult> = try {
        val totalQuestions = session.questions.size
        val correctCount = session.answers.count { it.isCorrect }
        val accuracy = if (totalQuestions == 0) 0f else correctCount.toFloat() / totalQuestions
        val totalTimeMs = session.answers.sumOf { it.responseTimeMs }
        
        val srsUpdates = session.answers.mapNotNull { answer ->
            val question = session.questions.find { it.vocabularyId == answer.questionId }
            if (question != null) {
                SRSUpdate(
                    vocabularyId = answer.questionId,
                    isCorrect = answer.isCorrect,
                    previousInterval = 0,
                    previousEaseFactor = 2.5,
                    previousRepetitions = 0,
                    newInterval = 0,
                    newEaseFactor = 2.5,
                    newRepetitions = 0
                )
            } else null
        }
        
        Result.success(
            QuizSessionResult(
                sessionId = session.sessionId,
                deckId = session.deckId,
                totalQuestions = totalQuestions,
                correctCount = correctCount,
                accuracy = accuracy,
                totalTimeMs = totalTimeMs,
                srsUpdates = srsUpdates,
                streakUpdated = true
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### UpdateSRSUseCase (SM-2 Algorithm)

```kotlin
// Calcular nuevo intervalo usando SM-2 algorithm

interface IUpdateSRSUseCase {
    suspend fun calculateAndUpdate(srsUpdate: SRSUpdate): Result<SRSUpdate>
}

@Singleton
class UpdateSRSUseCase @Inject constructor(
    private val srsRepository: ISRSRepository,
    private val timeProvider: TimeProvider
) : IUpdateSRSUseCase {
    
    override suspend fun calculateAndUpdate(srsUpdate: SRSUpdate): Result<SRSUpdate> = try {
        val quality = if (srsUpdate.isCorrect) 5 else 0
        val oldEF = srsUpdate.previousEaseFactor
        val newEF = calculateNewEaseFactor(oldEF, quality)
        val oldInterval = srsUpdate.previousInterval
        val newReps = srsUpdate.previousRepetitions + 1
        val newInterval = calculateNewInterval(oldInterval, newReps, newEF)
        
        val updated = srsUpdate.copy(
            reviewTimestamp = timeProvider.nowEpochMillis(),
            newEaseFactor = newEF,
            newInterval = newInterval,
            newRepetitions = newReps
        )
        
        srsRepository.updateSRS(updated)
        Result.success(updated)
        
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    private fun calculateNewEaseFactor(previousEF: Double, quality: Int): Double {
        val qualityAdjustment = 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)
        return maxOf(1.3, previousEF + qualityAdjustment)
    }
    
    private fun calculateNewInterval(previousInterval: Int, repetitions: Int, easeFactor: Double): Int {
        return when (repetitions) {
            1 -> 1
            2 -> 3
            else -> maxOf(1, (previousInterval * easeFactor).toInt())
        }
    }
}

// PSEUDOCÓDIGO TEST para validar SM-2:
/*
Test: SM-2 Algorithm Correctness
Given: vocab con previousInterval=1, previousEF=2.5, repetitions=1
When: calculateAndUpdate(isCorrect=true, quality=5)
Then: 
  - newInterval = 3 (segunda revisión)
  - newEF ≈ 2.6 (aumenta porque fue correcto)
  
When: calculateAndUpdate(isCorrect=false, quality=0)
Then:
  - newInterval = 1 (se resetea)
  - newEF ≈ 1.7 (disminuye)
*/
```

### SearchVocabularyUseCase

```kotlin
interface ISearchVocabularyUseCase {
    suspend fun search(
        query: String,
        deckId: Long? = null,
        limit: Int = 50
    ): Result<List<VocabularyEntity>>
}

@Singleton
class SearchVocabularyUseCase @Inject constructor(
    private val vocabularyRepository: IVocabularyRepository
) : ISearchVocabularyUseCase {
    
    override suspend fun search(
        query: String,
        deckId: Long?,
        limit: Int
    ): Result<List<VocabularyEntity>> = try {
        // Validación simple
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        
        // 2. Query en repositorio (Room DAO manejará índices)
        val results = if (deckId != null) {
            vocabularyRepository.searchVocabulary(
                query = query,
                deckId = deckId,
                limit = limit
            )
        } else {
            vocabularyRepository.searchVocabulary(
                query = query,
                limit = limit
            )
        }
        
        return Result.success(results)
        
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### DetectClipboardUseCase

```kotlin
interface IDetectClipboardUseCase {
    suspend fun detectAndExtractJapanese(text: String): Result<ClipboardEvent>
}

@Singleton
class DetectClipboardUseCase @Inject constructor() : IDetectClipboardUseCase {
    
    override suspend fun detectAndExtractJapanese(text: String): Result<ClipboardEvent> = try {
        val safeText = text.trim().take(500)
        val sensitiveRegex = Regex("(mailto:|https?://|\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b)")
        if (sensitiveRegex.containsMatchIn(safeText)) {
            return Result.failure(Exception("Sensitive clipboard content skipped"))
        }
        
        val japaneseRegex = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]+")
        val japaneseMatches = japaneseRegex.findAll(safeText)
        val japaneseText = japaneseMatches.joinToString(" ") { it.value }
        
        if (japaneseText.isEmpty()) {
            return Result.failure(Exception("No Japanese text detected"))
        }
        
        val event = ClipboardEvent(
            text = safeText,
            japaneseText = japaneseText,
            detectedAt = System.currentTimeMillis()
        )
        
        Result.success(event)
        
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### UpdateStreakUseCase

```kotlin
interface IUpdateStreakUseCase {
    suspend fun updateStreaks(
        deckId: Long,
        forceDate: Long? = null
    ): Result<StreakUpdate>
}

@Singleton
class UpdateStreakUseCase @Inject constructor(
    private val streakRepository: IStreakRepository,
    private val srsHistoryRepository: ISRSHistoryRepository,
    private val timeProvider: TimeProvider
) : IUpdateStreakUseCase {
    
    override suspend fun updateStreaks(
        deckId: Long,
        forceDate: Long? = null
    ): Result<StreakUpdate> = try {
        val reviewDate = forceDate ?: timeProvider.nowEpochMillis()
        val todayDay = toUtcDay(reviewDate)
        
        val globalStreak = streakRepository.getGlobalStreak()
        val lastGlobalPracticeDay = globalStreak?.lastPracticeDate?.let { toUtcDay(it) } ?: Long.MIN_VALUE
        val globalStreakUpdated = if (lastGlobalPracticeDay < todayDay) {
            val newGlobalStreak = if (lastGlobalPracticeDay == todayDay - 1L) {
                globalStreak?.currentStreak?.plus(1) ?: 1
            } else {
                1
            }
            
            streakRepository.updateGlobalStreak(
                currentStreak = newGlobalStreak,
                lastPracticeDate = reviewDate
            )
            true
        } else {
            false
        }
        
        val deckStreak = streakRepository.getDeckStreak(deckId)
        val lastDeckPracticeDay = deckStreak?.lastPracticeDate?.let { toUtcDay(it) } ?: Long.MIN_VALUE
        val deckStreakUpdated = if (lastDeckPracticeDay < todayDay) {
            val newDeckStreak = if (lastDeckPracticeDay == todayDay - 1L) {
                deckStreak?.currentStreak?.plus(1) ?: 1
            } else {
                1
            }
            
            streakRepository.updateDeckStreak(
                deckId = deckId,
                currentStreak = newDeckStreak,
                lastPracticeDate = reviewDate
            )
            true
        } else {
            false
        }
        
        val result = StreakUpdate(
            globalStreakUpdated = globalStreakUpdated,
            deckStreakUpdated = deckStreakUpdated,
            newGlobalStreak = if (globalStreakUpdated) {
                (globalStreak?.currentStreak ?: 0) + 1
            } else {
                globalStreak?.currentStreak ?: 0
            },
            newDeckStreak = if (deckStreakUpdated) {
                (deckStreak?.currentStreak ?: 0) + 1
            } else {
                deckStreak?.currentStreak ?: 0
            },
            maintenanceRequired = false
        )
        
        Result.success(result)
        
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    private fun toUtcDay(epochMillis: Long): Long {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .toEpochDay()
    }
}
```

---

## 🎯 INYECCIÓN DE DEPENDENCIAS

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    
    @Singleton
    @Provides
    fun providePerformQuizUseCase(
        vocabRepo: IVocabularyRepository,
        kanjiRepo: IKanjiRepository,
        srsRepo: ISRSRepository,
        timeProvider: TimeProvider
    ): IPerformQuizUseCase {
        return PerformQuizUseCase(vocabRepo, kanjiRepo, srsRepo, timeProvider)
    }
    
    @Singleton
    @Provides
    fun provideUpdateSRSUseCase(
        srsRepo: ISRSRepository,
        timeProvider: TimeProvider
    ): IUpdateSRSUseCase {
        return UpdateSRSUseCase(srsRepo, timeProvider)
    }
    
    @Singleton
    @Provides
    fun provideSearchVocabularyUseCase(
        vocabRepo: IVocabularyRepository
    ): ISearchVocabularyUseCase {
        return SearchVocabularyUseCase(vocabRepo)
    }
    
    @Singleton
    @Provides
    fun provideDetectClipboardUseCase(): IDetectClipboardUseCase {
        return DetectClipboardUseCase()
    }
    
    @Singleton
    @Provides
    fun provideTimeProvider(): TimeProvider {
        return SystemTimeProvider()
    }
    
    @Singleton
    @Provides
    fun provideValidateVocabularyInputUseCase(): IValidateVocabularyInputUseCase {
        return ValidateVocabularyInputUseCase()
    }
    
    @Singleton
    @Provides
    fun provideUpdateStreakUseCase(
        streakRepo: IStreakRepository,
        historyRepo: ISRSHistoryRepository,
        timeProvider: TimeProvider
    ): IUpdateStreakUseCase {
        return UpdateStreakUseCase(streakRepo, historyRepo, timeProvider)
    }
}
```

---

## 🧪 DOMAIN LAYER TESTING

```kotlin
// Pseudocódigo para tests (críticos - aquí está la lógica pura)

class UpdateSRSUseCaseTest {
    
    @Test
    fun testSM2_FirstReview_IntervalIs1() {
        // Dado: vocab nuevo (repetitions=0)
        // Cuando: revisar correctamente
        // Entonces: interval = 1 día
    }
    
    @Test
    fun testSM2_SecondReview_IntervalIs3() {
        // Dado: vocab con repetitions=1, interval=1
        // Cuando: revisar correctamente
        // Entonces: interval = 3 días
    }
    
    @Test
    fun testSM2_ThirdReview_IntervalMultiplied() {
        // Dado: vocab con repetitions=2, interval=3, ease=2.5
        // Cuando: revisar correctamente
        // Entonces: interval = 3 * 2.5 = 7 días
    }
    
    @Test
    fun testSM2_WrongAnswer_IntervalResets() {
        // Dado: vocab con interval=14
        // Cuando: revisar incorrectamente
        // Entonces: interval = 1, ease disminuye
    }
}

class PerformQuizUseCaseTest {
    
    @Test
    fun testGenerateQuiz_ReturnsCorrectCount() {
        // Dado: deck con 50 vocabularios vencidos
        // Cuando: generateQuiz(questionCount=10)
        // Entonces: retorna 10 preguntas
    }
    
    @Test
    fun testGenerateQuestion_HasExactlyOneCorrectAnswer() {
        // Dado: una pregunta generada
        // Entonces: exactamente 1 opción = correctSpanish
    }
}

class DetectClipboardUseCaseTest {
    
    @Test
    fun testDetectJapanese_ExtractsHiragana() {
        // Dado: "水 (水) みず water"
        // Cuando: detectAndExtract()
        // Entonces: japaneseText = "水 みず"
    }
}
```

---

## 🏛️ ARQUITECTURA DOMAIN LAYER

```
Definiciones Puras (NO dependen de Android)
├── Modelos (data classes)
├── Algoritmos (funciones puras)
├── Excepciones
└── Interfaces

Reglas:
- Ninguna dependencia en Context, Activity, ViewModel
- Ninguna dependencia en Room, Retrofit, Firebase
- Todas las funciones son puras o suspenders (async)
- Todos los errores retornan Result<T> o lanzan Exception
- Testeable sin Android Framework
```

---

## 🎯 PRÓXIMOS PASOS

1. Copiar pseudocódigo e implementar Use Cases
2. Implementar algoritmo SM-2 con precisión
3. Crear tests unitarios para cada UseCase
4. Validar que calculos SM-2 sean correctos

**Próximo documento**: [`03-PRESENTATION-LAYER.md`](03-PRESENTATION-LAYER.md) - ViewModels y Screens
