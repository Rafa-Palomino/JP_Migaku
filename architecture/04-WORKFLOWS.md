# 04 - WORKFLOWS: Flujos End-to-End

**Fecha**: 2026-07-08  
**Descripción**: Flujos críticos con pseudocódigo detallado.

---

## 1️⃣ WORKFLOW: CLIPBOARD → ADD VOCABULARY

```
USER COPIES: "飲む" (clipboard has "飲む (のむ) to drink")

START
  │
  └─▶ ClipboardViewModel.monitorClipboard()
       │
       ├─▶ ClipboardManager.getPrimaryClip()
       │   (API 32 optimizado: foreground access directo)
       │
       └─▶ DetectClipboardUseCase.detectAndExtractJapanese()
           ├─ Input: "飲む (のむ) to drink"
           ├─ Regex: [\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]+
           └─ Output: "飲む のむ"
           
       ├─▶ isJapanese == TRUE?
       │   ├─ YES: Show Snackbar("✓ Vocabulario detectado")
       │   └─ NO: Ignore
       │
       └─▶ USER TAPS "Agregar"
           │
           ├─▶ ClipboardDialog abre con:
           │   ├─ japanese: "飲む" (editable)
           │   ├─ reading: "のむ" (editable)
           │   ├─ spanish: "" (placeholder: "beber")
           │   └─ deckSelector: [Deck 1, Deck 2, ...] (dropdown)
           │
           └─▶ USER EDITS + TAP "GUARDAR"
               │
               ├─▶ ValidationUseCase.validateVocabulary()
               │   ├─ japanese != empty?
               │   ├─ reading != empty?
               │   ├─ spanish != empty?
               │   └─ deckId != 0?
               │
               ├─▶ VocabularyRepository.addVocabulary()
               │   │
               │   └─▶ Room DB Transaction:
               │       ├─ INSERT INTO vocabulary (...)
               │       │  VALUES (id, japanese, reading, spanish, deckId, ...)
               │       │
               │       └─ SRS fields AUTO:
               │          interval = 1
               │          easeFactor = 2.5
               │          repetitions = 0
               │
               └─▶ UI Toast: "✓ Guardado en [Deck]"
                   Clipboard cleared (optional)
                   
END

Pseudocódigo:
```

```kotlin
@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val detectClipboardUseCase: IDetectClipboardUseCase,
    private val validateVocabularyUseCase: IValidateVocabularyInputUseCase,
    private val vocabularyRepository: IVocabularyRepository,
    private val clipboardManager: ClipboardManager
) : ViewModel() {
    
    private val _clipboardEvent = MutableStateFlow<ClipboardEvent?>(null)
    
    fun startMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val clip = clipboardManager.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString().orEmpty()
                        if (text.length <= 500 && !text.contains("http")) {
                            detectClipboardUseCase.detectAndExtractJapanese(text)
                                .onSuccess { event -> _clipboardEvent.emit(event) }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore permission errors
                }
                delay(1000)
            }
        }
    }
    
    suspend fun addVocabularyFromClipboard(
        japanese: String,
        reading: String,
        spanish: String,
        deckId: Long
    ) {
        val validation = validateVocabularyUseCase.validate(japanese, reading, spanish)
        if (!validation.isValid) {
            throw IllegalArgumentException(validation.errorMessage)
        }
        
        val vocab = VocabularyEntity(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            japanese = japanese.trim(),
            reading = reading.trim().take(200),
            spanishMeaning = spanish.trim(),
            createdAt = System.currentTimeMillis(),
            interval = 1,
            easeFactor = 2.5,
            repetitions = 0
        )
        
        vocabularyRepository.addVocabulary(vocab)
    }
}
```

---

## 2️⃣ WORKFLOW: QUIZ SESSION

```
USER TAPS "Comenzar Quiz" en Deck

START
  │
  ├─▶ QuizViewModel.initializeQuiz(deckId=5)
  │   │
  │   └─▶ PerformQuizUseCase.generateQuiz(deckId=5, count=10)
  │       │
  │       └─▶ Room Query:
  │           SELECT * FROM vocabulary
  │           WHERE deck_id = 5
  │             AND (last_reviewed IS NULL
  │                  OR CAST((strftime('%s','now') - last_reviewed) 
  │                     / 86400.0 AS INTEGER) >= interval)
  │           LIMIT 20
  │           
  │           Resultado: [水, 飲む, 火, ...]
  │       │
  │       └─▶ Genera 10 QuizQuestion:
  │           ├─ Q1: japanese="水" → options=[みず, のむ, ひ, ...]
  │           ├─ Q2: spanish="飲む" → options=[beber, agua, ...]
  │           └─ ...
  │
  ├─▶ QuizScreen MOSTRARÁ:
  │   ├─ Pregunta 1/10: "水"
  │   ├─ Reading: "みず"
  │   ├─ 4 opciones (buttons)
  │   └─ Progress bar
  │
  └─▶ USER SELECCIONA OPCIÓN
      │
      ├─▶ QuizViewModel.selectAnswer("agua")
      │   ├─ isCorrect = ("agua" == "agua") = TRUE
      │   └─ Show result (color verde/rojo)
      │
      ├─▶ USER TAPS "SIGUIENTE"
      │   │
      │   └─▶ QuizViewModel.nextQuestion()
      │       ├─ questionIndex = 1
      │       └─ Display Q2 (repeat)
      │
      └─▶ REPEAT HASTA questionIndex == 10
          │
          └─▶ QuizViewModel.finishQuiz()
              │
              ├─▶ PerformQuizUseCase.finishQuiz(session)
              │   │
              │   └─▶ Calcula:
              │       ├─ accuracy = 7/10 = 70%
              │       ├─ totalTime = 125s
              │       └─ srsUpdates = [
              │           { id="Q1", isCorrect=true, ... },
              │           { id="Q2", isCorrect=false, ... },
              │           ...
              │         ]
              │
              ├─▶ UpdateSRSUseCase.calculateAndUpdate()
              │   (para cada pregunta)
              │   │
              │   └─▶ Room Transaction:
              │       ├─ UPDATE vocabulary SET interval, easeFactor, ...
              │       └─ INSERT INTO srs_history (...)
              │
              ├─▶ UpdateStreakUseCase.updateStreaks(deckId=5)
              │   │
              │   └─▶ Update GlobalStreak + DeckStreak
              │
              └─▶ QuizResultsScreen(accuracy=70%, ...)
                  └─ USER VUELVE A HOME
                  
END

Pseudocódigo:
```

```kotlin
@HiltViewModel
class QuizViewModel @Inject constructor(...) : ViewModel() {
    
    private var session: QuizSession? = null
    private val questionStartTime = mutableMapOf<Int, Long>()
    
    suspend fun selectAnswer(answer: String) {
        val current = _uiState.value
        val question = current.currentQuestion ?: return
        val isCorrect = answer == question.correctSpanish
        
        val responseTime = System.currentTimeMillis() - questionStartTime[current.questionIndex]!!
        
        val quizAnswer = QuizAnswer(
            questionId = question.vocabularyId,
            selectedAnswer = answer,
            isCorrect = isCorrect,
            responseTimeMs = responseTime
        )
        
        session = session?.copy(
            answers = session!!.answers + quizAnswer
        )
        
        _uiState.update {
            it.copy(
                selectedAnswer = answer,
                isCorrect = isCorrect,
                showResult = true,
                correctAnswer = question.correctSpanish
            )
        }
    }
    
    suspend fun nextQuestion() {
        val current = _uiState.value
        val nextIdx = current.questionIndex + 1
        
        if (nextIdx >= current.totalQuestions) {
            finishQuiz()
        } else {
            questionStartTime[nextIdx] = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    questionIndex = nextIdx,
                    currentQuestion = session?.questions?.get(nextIdx),
                    selectedAnswer = null,
                    showResult = false,
                    progress = nextIdx.toFloat() / current.totalQuestions
                )
            }
        }
    }
    
    private suspend fun finishQuiz() {
        // Calcular SRS updates
        val updates = session!!.answers.map { answer ->
            val question = session!!.questions.find { it.vocabularyId == answer.questionId }!!
            
            // Aquí entraría UpdateSRSUseCase
            SRSUpdate(
                vocabularyId = answer.questionId,
                isCorrect = answer.isCorrect,
                previousInterval = ...,
                previousEaseFactor = ...,
                previousRepetitions = ...,
                newInterval = ...,
                newEaseFactor = ...,
                newRepetitions = ...
            )
        }
        
        // Persistir todo en una transacción
        // (updateSRSUseCase internamente llama a repository.updateAfterReview)
    }
}
```

---

## 3️⃣ WORKFLOW: BÚSQUEDA LOCAL

```
USER ABRE PANTALLA SEARCH

START
  │
  └─▶ SearchScreen MOSTRADA
      ├─ TextField vacío
      └─ results = []
      │
  ├─▶ USER ESCRIBE "agua"
  │   │
  │   └─▶ SearchViewModel.onQueryChange("agua")
  │       ├─ Debounce 300ms
  │       └─ search()
  │           │
  │           └─▶ SearchVocabularyUseCase.search("agua", limit=50)
  │               │
  │               └─▶ Room DAO Query (INDEXED):
  │                   SELECT * FROM vocabulary
  │                   WHERE spanish_meaning LIKE '%agua%'
  │                      OR japanese LIKE '%agua%'
  │                   ORDER BY (CASE WHEN japanese LIKE 'agua%' ...)
  │                   LIMIT 50
  │                   
  │                   └─ Índices garantizan <50ms
  │                   └─ Results: [agua(みず), agua(H2O), ...]
  │
  ├─▶ SearchScreen ACTUALIZA:
  │   ├─ results = [VocabularyEntity, ...]
  │   └─ Lista mostrada (lazy)
  │
  └─▶ USER TAPS RESULTADO
      │
      └─▶ SearchViewModel.selectVocabulary(vocab)
          │
          └─▶ Navega a DETAIL o AGREGAR A DECK
          
END

Performance Garantizado:
├─ Búsqueda: <50ms (sqlite index)
├─ Debounce: 300ms (evita queries spam)
├─ Paginación: LIMIT 50 (no carga todo)
└─ Lazy loading UI: Solo 10-15 items en pantalla
```

---

## 4️⃣ WORKFLOW: CÁLCULO DE STREAK

```
TRAS COMPLETAR QUIZ

START
  │
  └─▶ UpdateStreakUseCase.updateStreaks(deckId=5)
      │
      ├─▶ today = System.currentTimeMillis() / 86400000  (days since epoch)
      │
      ├─▶ GET global_streak (singleton):
      │   lastPracticeDate_days = globalStreak.lastPracticeDate / 86400000
      │   currentStreak = globalStreak.currentStreak
      │
      ├─▶ SI lastPracticeDate_days < today:
      │   ├─ Nueva práctica hoy
      │   │
      │   └─▶ SI lastPracticeDate_days == today - 1:
      │       ├─ ✓ Practicó ayer
      │       ├─ newStreak = currentStreak + 1
      │       └─ UPDATE global_streak SET currentStreak = newStreak
      │   
      │   └─▶ ELSE:
      │       ├─ ✗ NO practicó ayer (streakRoto)
      │       ├─ newStreak = 1
      │       └─ UPDATE global_streak SET currentStreak = 1
      │
      ├─▶ IDEM para deck_streak (por deckId)
      │
      └─▶ RETORNA StreakUpdate(globalStreakUpdated, deckStreakUpdated, ...)

GARANTÍAS:
├─ 1 streak global (singleton)
├─ N streaks por deck
├─ Actualización atómica (transacción)
└─ No hay timezones (UTC en epochs)
```

---

## 🎯 TRANSACCIONES CRÍTICAS

### Atomic Quiz + SRS + Streak Update

```kotlin
database.withTransaction {
    // 1. Actualizar vocabulario (SRS)
    for (update in srsUpdates) {
        vocabularyDao.updateAfterReview(
            id = update.vocabularyId,
            newInterval = update.newInterval,
            newEaseFactor = update.newEaseFactor,
            newRepetitions = update.newRepetitions,
            lastReviewed = System.currentTimeMillis()
        )
        
        // 2. Registrar histórico
        srsHistoryDao.insert(SRSHistoryEntity(
            vocabularyId = update.vocabularyId,
            reviewDate = System.currentTimeMillis(),
            isCorrect = update.isCorrect,
            oldInterval = update.previousInterval,
            newInterval = update.newInterval,
            oldEaseFactor = update.previousEaseFactor,
            newEaseFactor = update.newEaseFactor
        ))
    }
    
    // 3. Actualizar streak
    streakDao.updateGlobalStreak(newGlobalStreak)
    streakDao.updateDeckStreak(deckId, newDeckStreak)
}
// Si FALLA algo: TODO ROLLBACK
```

---

## 🧪 VALIDACIÓN DE WORKFLOWS

```kotlin
class ClipboardToVocabularyWorkflowTest {
    
    @Test
    fun testClipboardWorkflow_AddsVocabularyToDatabase() = runTest {
        // Setup
        val clipboard = "水 (みず) agua"
        
        // Act
        val event = detectClipboardUseCase.detectAndExtractJapanese(clipboard)
        val vocab = VocabularyEntity(...)
        vocabularyRepository.addVocabulary(vocab)
        
        // Assert
        val stored = vocabularyRepository.getById(vocab.id)
        assertEquals(vocab, stored)
    }
}

class QuizWorkflowTest {
    
    @Test
    fun testQuizWorkflow_CompletesAndUpdatesSRS() = runTest {
        // Generate → Answer → Finish → Verify SRS updated
    }
}
```

---

**Próximo documento**: [`05-PERFORMANCE.md`](05-PERFORMANCE.md) - Performance targets y optimizaciones
