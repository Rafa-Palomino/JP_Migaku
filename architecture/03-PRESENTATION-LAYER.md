# 03 - PRESENTATION LAYER: ViewModels, Screens, Navigation

**Fecha**: 2026-07-08  
**Descripción**: Jetpack Compose UI, ViewModels con StateFlow, navegación.

---

## 📦 COMPONENTES PRESENTATION LAYER

```
Presentation Layer (UI + State Management)
├── ViewModels
│   ├── HomeViewModel
│   ├── QuizViewModel
│   ├── SearchViewModel
│   ├── DeckViewModel
│   └── ClipboardViewModel
│
├── Screens (Compose)
│   ├── HomeScreen
│   ├── QuizScreen
│   ├── SearchScreen
│   ├── DeckManagementScreen
│   └── ClipboardDialogScreen
│
├── Components (Reusable Composables)
│   ├── QuizCard
│   ├── VocabularyListItem
│   ├── StreakDisplay
│   └── ...
│
└── Navigation
    └── NavGraph
```

---

## 🎮 VIEWMODELS CON STATEFLOW

### HomeViewModel

```kotlin
// Estado de la pantalla principal

data class HomeUiState(
    val isLoading: Boolean = false,
    val decks: List<DeckWithStats> = emptyList(),
    val globalStreak: Int = 0,
    val recentActivityCount: Int = 0,
    val error: String? = null
)

data class DeckWithStats(
    val deckId: Long,
    val deckName: String,
    val vocabularyCount: Int,
    val deckStreak: Int,
    val dueCount: Int,           // Vocabulario vencido
    val lastPracticedDate: Long? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deckRepository: IDeckRepository,
    private val srsRepository: ISRSRepository,
    private val streakRepository: IStreakRepository
) : ViewModel() {
    
    // Mutable state (privado)
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState())
    
    // Exposed state (readonly)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadHomeData()
    }
    
    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Cargar decks
                val decks = deckRepository.getAllDecks()
                
                // Cargar estadísticas globales
                val globalStreak = streakRepository.getGlobalStreak()?.currentStreak ?: 0
                
                // Mapear a UiState
                val deckStats = decks.map { deck ->
                    val vocabCount = srsRepository.getVocabularyCount(deck.id)
                    val dueCount = srsRepository.getVocabularyDueCount(deck.id)
                    val deckStreak = streakRepository.getDeckStreak(deck.id)?.currentStreak ?: 0
                    
                    DeckWithStats(
                        deckId = deck.id,
                        deckName = deck.name,
                        vocabularyCount = vocabCount,
                        deckStreak = deckStreak,
                        dueCount = dueCount,
                        lastPracticedDate = streakRepository.getDeckStreak(deck.id)?.lastPracticeDate
                    )
                }
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        decks = deckStats,
                        globalStreak = globalStreak
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    fun refreshHomeData() {
        loadHomeData()
    }
    
    fun navigateToDeck(deckId: Long) {
        // Signal navegación (puede usarse con callback)
    }
}
```

### QuizViewModel

```kotlin
data class QuizUiState(
    val sessionId: String = "",
    val currentQuestion: QuizQuestion? = null,
    val questionIndex: Int = 0,
    val totalQuestions: Int = 0,
    val progress: Float = 0f,                       // 0.0 - 1.0
    val selectedAnswer: String? = null,
    val showResult: Boolean = false,
    val isCorrect: Boolean? = null,
    val correctAnswer: String? = null,
    val isLoading: Boolean = false,
    val sessionComplete: Boolean = false,
    val sessionResult: QuizSessionResult? = null,
    val error: String? = null
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val performQuizUseCase: IPerformQuizUseCase,
    private val updateSRSUseCase: IUpdateSRSUseCase,
    private val updateStreakUseCase: IUpdateStreakUseCase
) : ViewModel() {
    
    private val deckId: Long = savedStateHandle["deckId"] ?: 0L
    
    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()
    
    private var session: QuizSession? = null
    private var sessionStartAtMs: Long = 0L
    private var currentQuestionStartedAtMs: Long = 0L
    private var completionInProgress = false
    
    init {
        initializeQuiz()
    }
    
    private fun initializeQuiz() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val result = performQuizUseCase.generateQuiz(
                    deckId = deckId,
                    questionCount = 10
                )
                
                result.onSuccess { quizSession ->
                    session = quizSession
                    sessionStartAtMs = System.currentTimeMillis()
                    currentQuestionStartedAtMs = sessionStartAtMs
                    
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessionId = quizSession.sessionId,
                            totalQuestions = quizSession.questions.size,
                            currentQuestion = quizSession.questions.firstOrNull(),
                            questionIndex = 0,
                            progress = 0f
                        )
                    }
                }
                
                result.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    fun selectAnswer(answer: String) {
        val currentState = _uiState.value
        val currentQuestion = currentState.currentQuestion ?: return
        if (currentState.showResult || completionInProgress) return
        
        val responseTimeMs = maxOf(1L, System.currentTimeMillis() - currentQuestionStartedAtMs)
        val isCorrect = answer == currentQuestion.correctSpanish
        
        val currentSession = session ?: return
        session = currentSession.copy(
            answers = currentSession.answers + QuizAnswer(
                questionId = currentQuestion.vocabularyId,
                selectedAnswer = answer,
                isCorrect = isCorrect,
                responseTimeMs = responseTimeMs
            )
        )
        
        _uiState.update {
            it.copy(
                selectedAnswer = answer,
                showResult = true,
                isCorrect = isCorrect,
                correctAnswer = currentQuestion.correctSpanish
            )
        }
    }
    
    fun nextQuestion() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val newIndex = currentState.questionIndex + 1
            
            if (newIndex >= currentState.totalQuestions) {
                finishQuiz()
            } else {
                currentQuestionStartedAtMs = System.currentTimeMillis()
                val nextQuestion = session?.questions?.getOrNull(newIndex)
                val progress = newIndex.toFloat() / currentState.totalQuestions
                
                _uiState.update {
                    it.copy(
                        questionIndex = newIndex,
                        currentQuestion = nextQuestion,
                        selectedAnswer = null,
                        showResult = false,
                        isCorrect = null,
                        progress = progress
                    )
                }
            }
        }
    }
    
    private fun finishQuiz() {
        if (completionInProgress) return
        completionInProgress = true
        
        viewModelScope.launch {
            try {
                val sessionToFinish = session ?: return@launch
                
                val resultSession = performQuizUseCase.finishQuiz(sessionToFinish)
                
                resultSession.onSuccess { sessionResult ->
                    sessionResult.srsUpdates.forEach { srsUpdate ->
                        updateSRSUseCase.calculateAndUpdate(srsUpdate)
                    }
                    
                    updateStreakUseCase.updateStreaks(deckId)
                    
                    _uiState.update {
                        it.copy(
                            sessionComplete = true,
                            sessionResult = sessionResult,
                            progress = 1f
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message)
                }
            } finally {
                completionInProgress = false
            }
        }
    }
}
```

### ClipboardViewModel (privacy-safe)

```kotlin
@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val detectClipboardUseCase: IDetectClipboardUseCase,
    private val validateVocabularyUseCase: IValidateVocabularyInputUseCase,
    private val vocabularyRepository: IVocabularyRepository,
    private val clipboardManager: ClipboardManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ClipboardUiState>(ClipboardUiState())
    val uiState: StateFlow<ClipboardUiState> = _uiState.asStateFlow()
    
    init {
        startMonitoring()
    }
    
    fun startMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                val clip = clipboardManager.primaryClip
                val text = clip?.getItemAt(0)?.text?.toString().orEmpty()
                if (text.isNotBlank() && !shouldIgnoreClipboardContent(text)) {
                    detectClipboardUseCase.detectAndExtractJapanese(text)
                        .onSuccess { event ->
                            _uiState.update { it.copy(lastDetectedEvent = event) }
                        }
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
            _uiState.update { it.copy(error = validation.errorMessage) }
            return
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
        _uiState.update { it.copy(lastSavedVocabulary = vocab) }
    }
    
    private fun shouldIgnoreClipboardContent(text: String): Boolean {
        val sensitiveRegex = Regex("(mailto:|https?://|\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b)")
        return text.length > 500 || sensitiveRegex.containsMatchIn(text)
    }
}

data class ClipboardUiState(
    val lastDetectedEvent: ClipboardEvent? = null,
    val lastSavedVocabulary: VocabularyEntity? = null,
    val error: String? = null
)
```

### SearchViewModel

```kotlin
data class SearchUiState(
    val query: String = "",
    val results: List<VocabularyEntity> = emptyList(),
    val isLoading: Boolean = false,
    val selectedVocabulary: VocabularyEntity? = null,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchVocabularyUseCase: ISearchVocabularyUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private val searchJob: Job? = null
    
    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        
        // Cancelar búsqueda anterior
        searchJob?.cancel()
        
        // Debounce 300ms
        viewModelScope.launch {
            delay(300)
            search()
        }
    }
    
    private fun search() {
        viewModelScope.launch {
            val query = _uiState.value.query
            if (query.isEmpty()) {
                _uiState.update { it.copy(results = emptyList()) }
                return@launch
            }
            
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val result = searchVocabularyUseCase.search(
                    query = query,
                    limit = 50
                )
                
                result.onSuccess { vocabularies ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = vocabularies
                        )
                    }
                }
                
                result.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    fun selectVocabulary(vocabulary: VocabularyEntity) {
        _uiState.update { it.copy(selectedVocabulary = vocabulary) }
    }
}
```

---

## 🎨 SCREENS (JETPACK COMPOSE)

### HomeScreen

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDeck: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Streak global
            item {
                StreakDisplay(
                    currentStreak = uiState.globalStreak,
                    title = "Racha Global"
                )
            }
            
            // Lista de decks
            items(uiState.decks) { deck ->
                DeckCard(
                    deck = deck,
                    onTap = { onNavigateToDeck(deck.deckId) }
                )
            }
        }
    }
}

@Composable
fun DeckCard(
    deck: DeckWithStats,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = deck.deckName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Vocabulario: ${deck.vocabularyCount}", fontSize = 12.sp)
                    Text("Vencido: ${deck.dueCount}", fontSize = 12.sp, color = Color.Red)
                }
                
                Text(
                    "🔥 ${deck.deckStreak}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
```

### QuizScreen

```kotlin
@Composable
fun QuizScreen(
    viewModel: QuizViewModel = hiltViewModel(),
    onFinish: (QuizSessionResult) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.sessionComplete -> {
            QuizResultsScreen(
                result = uiState.sessionResult!!,
                onFinish = onFinish
            )
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Progreso
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Pregunta
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.currentQuestion?.japanese ?: "",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = uiState.currentQuestion?.reading ?: "",
                        fontSize = 24.sp,
                        color = Color.Gray
                    )
                }
                
                // Opciones
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.currentQuestion?.options?.forEach { option ->
                        OptionButton(
                            text = option,
                            selected = option == uiState.selectedAnswer,
                            isCorrect = uiState.isCorrect,
                            showResult = uiState.showResult,
                            isCorrectAnswer = option == uiState.correctAnswer,
                            onTap = {
                                if (!uiState.showResult) {
                                    viewModel.selectAnswer(option)
                                }
                            }
                        )
                    }
                }
                
                // Botón siguiente
                Button(
                    onClick = { viewModel.nextQuestion() },
                    enabled = uiState.showResult,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Siguiente")
                }
            }
        }
    }
}
```

---

## 🧭 NAVEGACIÓN

```kotlin
@Composable
fun NavGraph() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToDeck = { deckId ->
                    navController.navigate("deck/$deckId")
                }
            )
        }
        
        composable("search") {
            SearchScreen()
        }
        
        composable(
            route = "deck/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.LongType })
        ) {
            DeckManagementScreen()
        }
        
        composable(
            route = "quiz/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.LongType })
        ) {
            QuizScreen(
                onFinish = { result ->
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun MainActivity() {
    JPMigakuTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavGraph()
        }
    }
}
```

---

## 🧪 TESTING VIEWMODELS

```kotlin
class HomeViewModelTest {
    
    @Test
    fun testHomeViewModel_LoadsDecks() = runTest {
        // Dado: repo mockeado con 3 decks
        val viewModel = HomeViewModel(mockDeckRepo, mockSrsRepo, mockStreakRepo)
        
        // Cuando: init
        advanceUntilIdle()
        
        // Entonces: uiState.decks contiene 3 elementos
        val state = viewModel.uiState.first()
        assertEquals(3, state.decks.size)
    }
}
```

---

## 🎯 PRÓXIMOS PASOS

1. Implementar Screens en Compose
2. Crear Tests para ViewModels
3. Configurar Navigation

**Próximo documento**: [`04-WORKFLOWS.md`](04-WORKFLOWS.md) - Flujos end-to-end
