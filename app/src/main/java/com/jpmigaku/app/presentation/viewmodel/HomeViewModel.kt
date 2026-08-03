package com.jpmigaku.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpmigaku.app.data.repository.DeckRepository
import com.jpmigaku.app.data.repository.VocabularyRepository
import com.jpmigaku.app.domain.model.Deck
import com.jpmigaku.app.domain.model.VocabularyEntry
import com.jpmigaku.app.domain.usecase.CreateDeckUseCase
import com.jpmigaku.app.domain.usecase.CreateVocabularyUseCase
import com.jpmigaku.app.domain.usecase.ReviewVocabularyUseCase
import com.jpmigaku.app.domain.usecase.SearchVocabularyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QuizMode {
    JAPANESE_TO_SPANISH,
    SPANISH_TO_JAPANESE
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val deckRepository: DeckRepository,
    private val createVocabularyUseCase: CreateVocabularyUseCase,
    private val createDeckUseCase: CreateDeckUseCase,
    private val reviewVocabularyUseCase: ReviewVocabularyUseCase,
    private val searchVocabularyUseCase: SearchVocabularyUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refreshState()
        }
    }

    fun onAddVocabularyClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update { it.copy(screen = HomeScreen.AddVocabulary, feedback = null) }
        }
    }

    fun onPracticeQuizClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update { it.copy(screen = HomeScreen.QuizMode, feedback = null) }
        }
    }

    fun onStartQuizClicked(mode: QuizMode) {
        viewModelScope.launch {
            val dueEntries = vocabularyRepository.getDue(limit = 1)
            val nextEntry = dueEntries.firstOrNull()
            _uiState.update {
                it.copy(
                    screen = HomeScreen.Quiz,
                    quizMode = mode,
                    quizEntry = nextEntry,
                    quizAnswer = "",
                    quizFeedback = null,
                    quizCompleted = nextEntry == null
                )
            }
        }
    }

    fun onSearchClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update { it.copy(screen = HomeScreen.Search, searchQuery = "", searchResults = emptyList()) }
        }
    }

    fun onBrowseEntriesClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update { it.copy(screen = HomeScreen.List, feedback = null) }
        }
    }

    fun onBackClicked() {
        _uiState.update { it.copy(screen = HomeScreen.Home, feedback = null) }
    }

    fun onSearchQueryChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun onPerformSearch() {
        viewModelScope.launch {
            val query = _uiState.value.searchQuery.trim()
            if (query.isBlank()) {
                _uiState.update { it.copy(feedback = "Escribe algo para buscar") }
                return@launch
            }
            val results = searchVocabularyUseCase(query, limit = 20)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun onJapaneseChanged(value: String) {
        _uiState.update { it.copy(addJapanese = value) }
    }

    fun onReadingChanged(value: String) {
        _uiState.update { it.copy(addReading = value) }
    }

    fun onMeaningChanged(value: String) {
        _uiState.update { it.copy(addMeaning = value) }
    }

    fun onDeckSelected(deckId: String?) {
        _uiState.update { it.copy(selectedDeckId = deckId) }
    }

    fun onDeckNameChanged(value: String) {
        _uiState.update { it.copy(newDeckName = value) }
    }

    fun onCreateDeckClicked() {
        val name = _uiState.value.newDeckName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(feedback = "Introduce un nombre para el deck") }
            return
        }

        viewModelScope.launch {
            val created = createDeckUseCase(name)
            refreshState()
            _uiState.update {
                it.copy(
                    selectedDeckId = created.id,
                    newDeckName = "",
                    feedback = "Deck creado: ${created.name}"
                )
            }
        }
    }

    fun onClipboardTextLoaded(text: String?) {
        val normalized = text?.trim().orEmpty()
        if (normalized.isBlank()) {
            _uiState.update { it.copy(feedback = "El portapapeles está vacío") }
            return
        }

        _uiState.update {
            it.copy(
                addJapanese = normalized,
                feedback = "Texto del portapapeles cargado"
            )
        }
    }

    fun onSaveVocabulary() {
        val current = _uiState.value

        viewModelScope.launch {
            if (current.addJapanese.isBlank() || current.addMeaning.isBlank()) {
                _uiState.update { it.copy(feedback = "Completa los campos japones y significado") }
                return@launch
            }

            val saved = createVocabularyUseCase(
                japanese = current.addJapanese,
                reading = current.addReading,
                meaningEs = current.addMeaning,
                deckId = current.selectedDeckId
            )

            refreshState()
            _uiState.update {
                it.copy(
                    screen = HomeScreen.List,
                    feedback = "Guardado: ${saved.japanese}",
                    addJapanese = "",
                    addReading = "",
                    addMeaning = ""
                )
            }
        }
    }

    fun onQuizAnswerChanged(value: String) {
        _uiState.update { it.copy(quizAnswer = value) }
    }

    fun onSubmitQuizAnswer() {
        val current = _uiState.value.quizEntry ?: return
        val answer = _uiState.value.quizAnswer.trim()

        val isCorrect = when (_uiState.value.quizMode) {
            QuizMode.JAPANESE_TO_SPANISH -> answer.equals(current.meaningEs.trim(), ignoreCase = true)
            QuizMode.SPANISH_TO_JAPANESE -> answer.equals(current.japanese.trim(), ignoreCase = false)
        }

        viewModelScope.launch {
            reviewVocabularyUseCase(current, isCorrect)
            refreshState()
            val nextEntry = vocabularyRepository.getDue(limit = 1).firstOrNull()
            val expectedAnswer = when (_uiState.value.quizMode) {
                QuizMode.JAPANESE_TO_SPANISH -> current.meaningEs
                QuizMode.SPANISH_TO_JAPANESE -> current.japanese
            }
            _uiState.update {
                it.copy(
                    quizEntry = nextEntry,
                    quizAnswer = "",
                    quizFeedback = if (isCorrect) "Correcto" else "Respuesta esperada: $expectedAnswer",
                    quizCompleted = nextEntry == null
                )
            }
        }
    }

    private suspend fun refreshState() {
        val decks = deckRepository.listDecks().ifEmpty {
            listOf(createDeckUseCase("General"))
        }
        val vocabulary = vocabularyRepository.getAll()
        _uiState.update { state ->
            state.copy(
                decks = decks,
                vocabularies = vocabulary,
                selectedDeckId = state.selectedDeckId ?: decks.firstOrNull()?.id
            )
        }
    }
}

sealed interface HomeAction {
    data object AddVocabulary : HomeAction
    data object PracticeQuiz : HomeAction
    data object Search : HomeAction
    data object ViewStreak : HomeAction
}

sealed interface HomeScreen {
    data object Home : HomeScreen
    data object AddVocabulary : HomeScreen
    data object QuizMode : HomeScreen
    data object Quiz : HomeScreen
    data object List : HomeScreen
    data object Search : HomeScreen
}

data class HomeUiState(
    val screen: HomeScreen = HomeScreen.Home,
    val feedback: String? = null,
    val addJapanese: String = "",
    val addReading: String = "",
    val addMeaning: String = "",
    val newDeckName: String = "",
    val selectedDeckId: String? = null,
    val decks: List<Deck> = emptyList(),
    val vocabularies: List<VocabularyEntry> = emptyList(),
    val quizMode: QuizMode = QuizMode.JAPANESE_TO_SPANISH,
    val quizEntry: VocabularyEntry? = null,
    val quizAnswer: String = "",
    val quizFeedback: String? = null,
    val quizCompleted: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<VocabularyEntry> = emptyList()
)
