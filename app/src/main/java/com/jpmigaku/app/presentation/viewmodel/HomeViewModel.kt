package com.jpmigaku.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpmigaku.app.data.local.DictionaryAssetImporter
import com.jpmigaku.app.data.repository.DeckRepository
import com.jpmigaku.app.data.repository.DictionaryVocabularyRepository
import com.jpmigaku.app.data.repository.DictionaryKanjiRepository
import com.jpmigaku.app.data.repository.VocabularyRepository
import com.jpmigaku.app.domain.model.Deck
import com.jpmigaku.app.domain.model.DictionaryVocabulary
import com.jpmigaku.app.domain.model.DictionaryKanji
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

enum class StudyArea {
    KANJI,
    VOCABULARY
}

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
    private val searchVocabularyUseCase: SearchVocabularyUseCase,
    private val dictionaryVocabularyRepository: DictionaryVocabularyRepository,
    private val dictionaryKanjiRepository: DictionaryKanjiRepository,
    private val dictionaryAssetImporter: DictionaryAssetImporter
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private var dictionarySearchJob: Job? = null
    private var kanjiSearchJob: Job? = null
    private var dictionaryImportJob: Job? = null

    init {
        viewModelScope.launch { refreshState() }
    }

    fun onAddVocabularyClicked() {
        _uiState.update {
            it.copy(
                screen = HomeScreen.AddVocabulary,
                feedback = null,
                dictionaryQuery = "",
                dictionaryResults = emptyList(),
                selectedDictionaryVocabulary = null
            )
        }
        viewModelScope.launch {
            refreshState()
        }
    }

    fun onAddManualVocabularyClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update {
                it.copy(screen = HomeScreen.PersonalVocabulary, feedback = null)
            }
        }
    }

    fun onAddKanjiClicked() {
        _uiState.update {
            it.copy(
                screen = HomeScreen.AddKanji,
                feedback = null,
                kanjiQuery = "",
                kanjiResults = emptyList(),
                selectedDictionaryKanji = null
            )
        }
    }

    fun onStudyAreaChanged(area: StudyArea) {
        _uiState.update { it.copy(studyArea = area) }
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

    fun onStatisticsClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update { it.copy(screen = HomeScreen.Statistics, feedback = null) }
        }
    }

    fun onManageDecksClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update { it.copy(screen = HomeScreen.Decks, feedback = null) }
        }
    }

    fun onDeckOpened(deckId: String) {
        viewModelScope.launch {
            val deck = _uiState.value.decks.firstOrNull { it.id == deckId } ?: return@launch
            val entries = vocabularyRepository.getByDeck(deck.id)
            _uiState.update {
                it.copy(
                    screen = HomeScreen.DeckDetail,
                    managedDeckId = deck.id,
                    selectedDeckIds = setOf(deck.id),
                    managedDeckEntries = entries
                )
            }
        }
    }

    fun onRemoveEntryFromDeck(entry: VocabularyEntry) {
        val deckId = _uiState.value.managedDeckId ?: return
        viewModelScope.launch {
            vocabularyRepository.removeFromDeck(entry.id, deckId)
            _uiState.update {
                it.copy(managedDeckEntries = vocabularyRepository.getByDeck(deckId))
            }
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

    fun onDictionaryQueryChanged(value: String) {
        _uiState.update {
            it.copy(
                dictionaryQuery = value,
                selectedDictionaryVocabulary = null,
                dictionaryResults = if (value.isBlank()) emptyList() else it.dictionaryResults
            )
        }
        dictionarySearchJob?.cancel()
        if (value.isBlank()) return

        dictionarySearchJob = viewModelScope.launch {
            ensureDictionaryImport().join()
            val results = dictionaryVocabularyRepository.search(value, limit = 20)
            if (_uiState.value.dictionaryQuery == value) {
                _uiState.update { it.copy(dictionaryResults = results) }
            }

        }
    }

    fun onClipboardSearchLoaded(text: String?) {
        val normalized = text?.trim().orEmpty()
        if (normalized.isBlank()) {
            _uiState.update { it.copy(feedback = "El portapapeles está vacío") }
            return
        }
        onDictionaryQueryChanged(normalized)
    }

    fun onDictionaryVocabularySelected(entry: DictionaryVocabulary) {
        _uiState.update { it.copy(selectedDictionaryVocabulary = entry) }
    }

    fun onKanjiQueryChanged(value: String) {
        _uiState.update {
            it.copy(
                kanjiQuery = value,
                selectedDictionaryKanji = null,
                kanjiResults = if (value.isBlank()) emptyList() else it.kanjiResults
            )
        }
        kanjiSearchJob?.cancel()
        if (value.isBlank()) return

        kanjiSearchJob = viewModelScope.launch {
            ensureDictionaryImport().join()
            val results = dictionaryKanjiRepository.search(value, limit = 20)
            if (_uiState.value.kanjiQuery == value) {
                _uiState.update { it.copy(kanjiResults = results) }
            }
        }
    }

    fun onDictionaryKanjiSelected(entry: DictionaryKanji) {
        _uiState.update { it.copy(selectedDictionaryKanji = entry) }
    }

    fun onSelectedDictionaryKanjiDismissed() {
        _uiState.update { it.copy(selectedDictionaryKanji = null) }
    }

    fun onAddSelectedDictionaryKanji() {
        val current = _uiState.value
        val entry = current.selectedDictionaryKanji ?: return
        viewModelScope.launch {
            createVocabularyUseCase(
                japanese = entry.character,
                reading = entry.kunyomi.ifBlank { entry.onyomi },
                meaningEs = entry.meaning,
                deckIds = current.selectedDeckIds.toList(),
                kind = "KANJI",
                sourceProvider = "kanjidic2",
                sourceKey = entry.character,
                sourceVersion = "3.6.2",
                sourceSnapshot = "${entry.character}|${entry.onyomi}|${entry.kunyomi}|${entry.meaning}"
            )
            refreshState()
            _uiState.update {
                it.copy(
                    selectedDictionaryKanji = null,
                    kanjiQuery = "",
                    kanjiResults = emptyList(),
                    feedback = "Kanji añadido: ${entry.character}"
                )
            }
        }
    }

    fun onSelectedDictionaryVocabularyDismissed() {
        _uiState.update { it.copy(selectedDictionaryVocabulary = null) }
    }

    fun onAddSelectedDictionaryVocabulary() {
        val current = _uiState.value
        val entry = current.selectedDictionaryVocabulary ?: return
        viewModelScope.launch {
            createVocabularyUseCase(
                japanese = entry.japanese,
                reading = entry.reading,
                meaningEs = entry.meaning,
                deckIds = current.selectedDeckIds.toList(),
                kind = "VOCABULARY",
                sourceProvider = "jmdict",
                sourceKey = entry.sequenceId,
                sourceVersion = "3.6.2",
                romaji = entry.romaji,
                sourceSnapshot = "${entry.japanese}|${entry.reading}|${entry.romaji}|${entry.meaning}"
            )
            refreshState()
            _uiState.update {
                it.copy(
                    selectedDictionaryVocabulary = null,
                    dictionaryQuery = "",
                    dictionaryResults = emptyList(),
                    feedback = "Añadido: ${entry.japanese}"
                )
            }
        }
    }

    fun onReadingChanged(value: String) {
        _uiState.update { it.copy(addReading = value) }
    }

    fun onMeaningChanged(value: String) {
        _uiState.update { it.copy(addMeaning = value) }
    }

    fun onDeckSelected(deckId: String?) {
        if (deckId == null) return
        _uiState.update { state ->
            val selected = state.selectedDeckIds.toMutableSet()
            if (!selected.add(deckId)) selected.remove(deckId)
            state.copy(selectedDeckIds = selected)
        }
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
                    selectedDeckIds = it.selectedDeckIds + created.id,
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
                deckIds = current.selectedDeckIds.toList()
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
        val storedDecks = deckRepository.listDecks()
        val decks = if (storedDecks.none { it.name == "Vocabulario" }) {
            storedDecks + createDeckUseCase("Vocabulario")
        } else {
            storedDecks
        }
        val defaultDeck = decks.firstOrNull { it.name == "Vocabulario" }
        val vocabulary = vocabularyRepository.getAll()
        _uiState.update { state ->
            state.copy(
                decks = decks,
                vocabularies = vocabulary,
                selectedDeckIds = if (state.selectedDeckIds.isEmpty()) {
                    setOfNotNull(defaultDeck?.id)
                } else {
                    state.selectedDeckIds.intersect(decks.map { it.id }.toSet())
                }
            )
        }
    }

    private fun ensureDictionaryImport(): Job {
        dictionaryImportJob?.let { return it }

        return viewModelScope.launch {
            _uiState.update { it.copy(feedback = "Cargando diccionario local...") }
            try {
                dictionaryAssetImporter.importIfNeeded()
                _uiState.update { state ->
                    if (state.feedback == "Cargando diccionario local...") {
                        state.copy(feedback = null)
                    } else {
                        state
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(feedback = "No se pudo cargar el diccionario: ${error.message}")
                }
            }
        }.also { dictionaryImportJob = it }
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
    data object PersonalVocabulary : HomeScreen
    data object AddKanji : HomeScreen
    data object QuizMode : HomeScreen
    data object Quiz : HomeScreen
    data object List : HomeScreen
    data object Search : HomeScreen
    data object Statistics : HomeScreen
    data object Decks : HomeScreen
    data object DeckDetail : HomeScreen
}

data class HomeUiState(
    val screen: HomeScreen = HomeScreen.Home,
    val studyArea: StudyArea = StudyArea.KANJI,
    val feedback: String? = null,
    val addJapanese: String = "",
    val addReading: String = "",
    val addMeaning: String = "",
    val newDeckName: String = "",
    val selectedDeckIds: Set<String> = emptySet(),
    val managedDeckId: String? = null,
    val managedDeckEntries: List<VocabularyEntry> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val vocabularies: List<VocabularyEntry> = emptyList(),
    val quizMode: QuizMode = QuizMode.JAPANESE_TO_SPANISH,
    val quizEntry: VocabularyEntry? = null,
    val quizAnswer: String = "",
    val quizFeedback: String? = null,
    val quizCompleted: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<VocabularyEntry> = emptyList(),
    val dictionaryQuery: String = "",
    val dictionaryResults: List<DictionaryVocabulary> = emptyList(),
    val selectedDictionaryVocabulary: DictionaryVocabulary? = null
    ,
    val kanjiQuery: String = "",
    val kanjiResults: List<DictionaryKanji> = emptyList(),
    val selectedDictionaryKanji: DictionaryKanji? = null
)
