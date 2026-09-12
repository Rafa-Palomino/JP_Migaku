package com.jpmigaku.app.presentation.viewmodel

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * Defines the prompt and answer direction for a quiz session.
 */
enum class QuizMode {
    STUDY,
    KANJI_STUDY,
    JAPANESE_TO_SPANISH,
    JAPANESE_TO_SPANISH_SELECTION,
    JAPANESE_TO_SPANISH_WRITTEN,
    SPANISH_TO_JAPANESE,
    SPANISH_TO_JAPANESE_SELECTION,
    SPANISH_TO_JAPANESE_WRITTEN,
    KANJI_TO_SPANISH_SELECTION,
    SPANISH_TO_KANJI_SELECTION,
    KANJI_TO_READINGS_SELECTION,
    READINGS_TO_KANJI_SELECTION
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
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
    private val preferences = context.getSharedPreferences("jpmigaku_settings", Context.MODE_PRIVATE)
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
                selectedDictionaryVocabulary = null,
                selectedDeckIds = it.decks.firstOrNull { deck -> deck.name == DEFAULT_VOCABULARY_DECK }
                    ?.let { deck -> setOf(deck.id) }
                    ?: emptySet()
            )
        }
        viewModelScope.launch {
            refreshState()
            _uiState.update { state ->
                state.copy(
                    selectedDeckIds = state.decks.firstOrNull { it.name == DEFAULT_VOCABULARY_DECK }
                        ?.let { setOf(it.id) }
                        ?: emptySet()
                )
            }
        }
    }

    fun onAddManualVocabularyClicked() {
        viewModelScope.launch {
            refreshState()
            _uiState.update {
                it.copy(
                    screen = HomeScreen.PersonalVocabulary,
                    feedback = null,
                    selectedDeckIds = it.decks.firstOrNull { deck -> deck.name == DEFAULT_VOCABULARY_DECK }
                        ?.let { deck -> setOf(deck.id) }
                        ?: emptySet()
                )
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
                selectedDictionaryKanji = null,
                selectedDeckIds = it.decks.firstOrNull { deck -> deck.name == DEFAULT_KANJI_DECK }
                    ?.let { deck -> setOf(deck.id) }
                    ?: emptySet()
            )
        }
        viewModelScope.launch {
            refreshState()
            _uiState.update { state ->
                    state.copy(
                        selectedDeckIds = state.decks.firstOrNull { it.name == DEFAULT_KANJI_DECK }
                            ?.let { setOf(it.id) }
                            ?: emptySet()
                    )
            }
        }
    }

    fun onQuizClicked(area: StudyArea) {
        _uiState.update { it.copy(screen = HomeScreen.QuizMode, studyArea = area, feedback = null) }
        viewModelScope.launch {
            refreshState()
        }
    }

    fun onStartQuizClicked(mode: QuizMode) {
        _uiState.update {
            it.copy(
                screen = HomeScreen.Quiz,
                quizMode = mode,
                quizEntry = null,
                quizAnswer = "",
                quizFeedback = null,
                quizCompleted = false,
                quizCorrectAnswers = 0,
                quizIncorrectAnswers = 0,
                quizOptions = emptyList()
            )
        }
        viewModelScope.launch {
            val currentState = _uiState.value
            val dueEntries = vocabularyRepository.getDue(
                limit = currentState.vocabularies.size.coerceAtLeast(currentState.quizQuestionCount),
                kind = mode.kindFilter()
            )
            val candidateEntries = dueEntries + currentState.vocabularies
                .filter { entry ->
                    entry.kind == mode.kindFilter() && dueEntries.none { dueEntry -> dueEntry.id == entry.id }
                }
            val quizQueue = buildQuizQueue(candidateEntries, currentState.quizQuestionCount)
            val nextEntry = quizQueue.firstOrNull()
            val options = nextEntry?.let {
                buildQuizOptions(
                    mode = mode,
                    currentEntry = it,
                    queue = quizQueue.drop(1),
                    allEntries = currentState.vocabularies
                )
            } ?: emptyList()

            _uiState.update {
                it.copy(
                    screen = HomeScreen.Quiz,
                    quizMode = mode,
                    quizEntry = nextEntry,
                    quizAnswer = "",
                    quizFeedback = null,
                    quizCompleted = nextEntry == null,
                    quizCorrectAnswers = 0,
                    quizIncorrectAnswers = 0,
                    quizEntries = quizQueue.drop(1),
                    quizQuestionsRemaining = quizQueue.size,
                    quizOptions = options
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

    fun onSettingsClicked() {
        _uiState.update { it.copy(screen = HomeScreen.Settings, feedback = null) }
    }

    fun onShowRomajiChanged(value: Boolean) {
        preferences.edit().putBoolean(SETTING_SHOW_ROMAJI, value).apply()
        _uiState.update { it.copy(showRomaji = value) }
    }

    fun onShowKanaChanged(value: Boolean) {
        preferences.edit().putBoolean(SETTING_SHOW_KANA, value).apply()
        _uiState.update { it.copy(showKanaInVocabulary = value) }
    }

    fun onShowKanjiMeaningChanged(value: Boolean) {
        preferences.edit().putBoolean(SETTING_SHOW_KANJI_MEANING, value).apply()
        _uiState.update { it.copy(showKanjiMeaning = value) }
    }

    fun onQuizQuestionCountChanged(value: Int) {
        require(value in QUIZ_QUESTION_COUNTS)
        preferences.edit().putInt(SETTING_QUIZ_COUNT, value).apply()
        _uiState.update { it.copy(quizQuestionCount = value) }
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

    fun onDeleteDeckClicked() {
        val deckId = _uiState.value.managedDeckId ?: return
        viewModelScope.launch {
            if (deckRepository.deleteDeckIfEmpty(deckId)) {
                refreshState()
                _uiState.update {
                    it.copy(
                        screen = HomeScreen.Decks,
                        managedDeckId = null,
                        managedDeckEntries = emptyList(),
                        feedback = "Deck eliminado"
                    )
                }
            } else {
                _uiState.update { it.copy(feedback = "Solo se pueden eliminar decks vacíos") }
            }
        }
    }

    private fun buildQuizQueue(
        candidates: List<VocabularyEntry>,
        questionCount: Int
    ): List<VocabularyEntry> {
        if (candidates.isEmpty() || questionCount <= 0) return emptyList()

        // Repeat shuffled rounds so a short collection can fill the configured session.
        val queue = ArrayList<VocabularyEntry>(questionCount)
        var previousId: String? = null
        while (queue.size < questionCount) {
            val round = candidates.shuffled()
            var addedInRound = false
            round.forEach { entry ->
                if (queue.size < questionCount && entry.id != previousId) {
                    queue += entry
                    previousId = entry.id
                    addedInRound = true
                }
            }
            if (!addedInRound) {
                queue += candidates.first()
                previousId = candidates.first().id
            }
        }
        return queue
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
            val results = dictionaryVocabularyRepository.search(value)
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
            val results = dictionaryKanjiRepository.search(value)
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
                reading = entry.kanjiReadings(),
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

    fun onQuizOptionSelected(option: String) {
        _uiState.update { it.copy(quizAnswer = option) }
        onSubmitQuizAnswer()
    }

    fun onSubmitQuizAnswer() {
        val state = _uiState.value
        val currentEntry = state.quizEntry ?: return
        val answer = state.quizAnswer.trim()

        if (!state.quizMode.isStudyMode() && answer.isBlank()) {
            _uiState.update { it.copy(quizFeedback = "Selecciona o escribe una respuesta") }
            return
        }

        val expectedAnswer = state.quizMode.expectedAnswer(currentEntry)
        val isCorrect = if (state.quizMode.isStudyMode()) {
            true
        } else {
            answer.equals(expectedAnswer.trim(), ignoreCase = state.quizMode.usesCaseInsensitiveComparison())
        }

        viewModelScope.launch {
            reviewVocabularyUseCase(currentEntry, isCorrect)
            refreshState()

            val remainingEntries = state.quizEntries
            val nextEntry = remainingEntries.firstOrNull()
            val nextQueue = remainingEntries.drop(1)
            val nextOptions = nextEntry?.let {
                buildQuizOptions(
                    mode = state.quizMode,
                    currentEntry = it,
                    queue = nextQueue,
                    allEntries = _uiState.value.vocabularies
                )
            } ?: emptyList()

            _uiState.update {
                it.copy(
                    quizEntry = nextEntry,
                    quizEntries = nextQueue,
                    quizAnswer = "",
                    quizFeedback = if (isCorrect) "Correcto" else "Respuesta esperada: $expectedAnswer",
                    quizCompleted = nextEntry == null,
                    quizCorrectAnswers = state.quizCorrectAnswers + if (isCorrect) 1 else 0,
                    quizIncorrectAnswers = state.quizIncorrectAnswers + if (isCorrect) 0 else 1,
                    quizQuestionsRemaining = if (nextEntry == null) 0 else nextQueue.size + 1,
                    quizOptions = nextOptions
                )
            }
        }
    }

    private fun buildQuizOptions(
        mode: QuizMode,
        currentEntry: VocabularyEntry,
        queue: List<VocabularyEntry>,
        allEntries: List<VocabularyEntry>
    ): List<String> {
        if (!mode.usesSelectionOptions()) return emptyList()

        val correctAnswer = mode.expectedAnswer(currentEntry).trim()
        if (correctAnswer.isBlank()) return emptyList()

        val normalizedCorrect = correctAnswer.normalizeQuizText()
        val currentConflictKey = mode.answerConflictKey(currentEntry)

        /*
         * Keep the source entry paired with each option. For Spanish-to-Japanese
         * quizzes, two different Japanese forms can share one valid meaning.
         */
        val distractors = (queue + allEntries.filter { it.kind == mode.kindFilter() })
            .asSequence()
            .filter { it.id != currentEntry.id }
            .map { entry -> entry to mode.expectedAnswer(entry).trim() }
            .filter { (_, answer) -> answer.isNotBlank() }
            .distinctBy {
                it.second.normalizeQuizText()
            }
            .filter { (entry, candidate) ->
                candidate.normalizeQuizText() != normalizedCorrect &&
                    mode.answerConflictKey(entry) != currentConflictKey
            }
            .map { (_, answer) -> answer }
            .shuffled()
            .take(3)
            .toList()

        return (listOf(correctAnswer) + distractors).shuffled()
    }

    private fun QuizMode.kindFilter(): String = when (this) {
        QuizMode.KANJI_STUDY,
        QuizMode.KANJI_TO_SPANISH_SELECTION,
        QuizMode.SPANISH_TO_KANJI_SELECTION,
        QuizMode.KANJI_TO_READINGS_SELECTION,
        QuizMode.READINGS_TO_KANJI_SELECTION -> "KANJI"
        else -> "VOCABULARY"
    }

    private fun QuizMode.expectedAnswer(entry: VocabularyEntry): String = when (this) {
        QuizMode.JAPANESE_TO_SPANISH,
        QuizMode.JAPANESE_TO_SPANISH_SELECTION,
        QuizMode.JAPANESE_TO_SPANISH_WRITTEN,
        QuizMode.KANJI_TO_SPANISH_SELECTION -> entry.meaningEs

        QuizMode.KANJI_TO_READINGS_SELECTION -> entry.reading

        QuizMode.SPANISH_TO_JAPANESE,
        QuizMode.SPANISH_TO_JAPANESE_SELECTION,
        QuizMode.SPANISH_TO_JAPANESE_WRITTEN,
        QuizMode.SPANISH_TO_KANJI_SELECTION,
        QuizMode.READINGS_TO_KANJI_SELECTION -> entry.japanese

        QuizMode.STUDY,
        QuizMode.KANJI_STUDY -> entry.meaningEs
    }

    private fun QuizMode.usesSelectionOptions(): Boolean = when (this) {
        QuizMode.JAPANESE_TO_SPANISH_SELECTION,
        QuizMode.SPANISH_TO_JAPANESE_SELECTION,
        QuizMode.KANJI_TO_SPANISH_SELECTION,
        QuizMode.SPANISH_TO_KANJI_SELECTION,
        QuizMode.KANJI_TO_READINGS_SELECTION,
        QuizMode.READINGS_TO_KANJI_SELECTION -> true

        else -> false
    }

    private fun QuizMode.usesCaseInsensitiveComparison(): Boolean = when (this) {
        QuizMode.JAPANESE_TO_SPANISH,
        QuizMode.JAPANESE_TO_SPANISH_SELECTION,
        QuizMode.JAPANESE_TO_SPANISH_WRITTEN,
        QuizMode.KANJI_TO_SPANISH_SELECTION,
        QuizMode.KANJI_TO_READINGS_SELECTION -> true

        else -> false
    }

    private fun QuizMode.answerConflictKey(entry: VocabularyEntry): String = when (this) {
        QuizMode.SPANISH_TO_JAPANESE,
        QuizMode.SPANISH_TO_JAPANESE_SELECTION,
        QuizMode.SPANISH_TO_JAPANESE_WRITTEN,
        QuizMode.SPANISH_TO_KANJI_SELECTION -> entry.meaningEs.normalizeQuizText()

        QuizMode.READINGS_TO_KANJI_SELECTION -> entry.reading.normalizeQuizText()
        else -> expectedAnswer(entry).normalizeQuizText()
    }

    private fun String.normalizeQuizText(): String =
        trim().lowercase().replace(Regex("[\\p{Punct}\\s]+"), " ")

    private fun QuizMode.isStudyMode(): Boolean = this == QuizMode.STUDY || this == QuizMode.KANJI_STUDY

    private fun DictionaryKanji.kanjiReadings(): String = buildString {
        if (onyomi.isNotBlank()) append("On: $onyomi")
        if (onyomi.isNotBlank() && kunyomi.isNotBlank()) append("\n")
        if (kunyomi.isNotBlank()) append("Kun: $kunyomi")
    }

    private suspend fun refreshState() {
        val storedDecks = deckRepository.listDecks()
        val decksWithVocabulary = if (storedDecks.none { it.name == DEFAULT_VOCABULARY_DECK }) {
            storedDecks + createDeckUseCase(DEFAULT_VOCABULARY_DECK)
        } else {
            storedDecks
        }
        val decks = if (decksWithVocabulary.none { it.name == DEFAULT_KANJI_DECK }) {
            decksWithVocabulary + createDeckUseCase(DEFAULT_KANJI_DECK)
        } else {
            decksWithVocabulary
        }
        val defaultDeck = decks.firstOrNull { it.name == DEFAULT_VOCABULARY_DECK }
        val vocabulary = vocabularyRepository.getAll()
        _uiState.update { state ->
            state.copy(
                decks = decks,
                vocabularies = vocabulary,
                showRomaji = preferences.getBoolean(SETTING_SHOW_ROMAJI, false),
                showKanaInVocabulary = preferences.getBoolean(SETTING_SHOW_KANA, true),
                showKanjiMeaning = preferences.getBoolean(SETTING_SHOW_KANJI_MEANING, true),
                quizQuestionCount = preferences.getInt(SETTING_QUIZ_COUNT, 10)
                    .coerceIn(QUIZ_QUESTION_COUNTS.first(), QUIZ_QUESTION_COUNTS.last()),
                selectedDeckIds = if (state.selectedDeckIds.isEmpty()) {
                    setOfNotNull(defaultDeck?.id)
                } else {
                    state.selectedDeckIds.intersect(decks.map { it.id }.toSet())
                }
            )
        }
    }

    private companion object {
        const val DEFAULT_VOCABULARY_DECK = "Vocabulario"
        const val DEFAULT_KANJI_DECK = "Kanji"
        const val SETTING_SHOW_ROMAJI = "show_romaji"
        const val SETTING_SHOW_KANA = "show_kana"
        const val SETTING_SHOW_KANJI_MEANING = "show_kanji_meaning"
        const val SETTING_QUIZ_COUNT = "quiz_question_count"
        val QUIZ_QUESTION_COUNTS = (5..100 step 5).toList()
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
    data object Settings : HomeScreen
}

/**
 * Immutable state rendered by the home flow and its child screens.
 */
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
    val quizCorrectAnswers: Int = 0,
    val quizIncorrectAnswers: Int = 0,
    val quizEntries: List<VocabularyEntry> = emptyList(),
    val quizQuestionsRemaining: Int = 0,
    val quizOptions: List<String> = emptyList(),
    val showRomaji: Boolean = false,
    val showKanaInVocabulary: Boolean = true,
    val showKanjiMeaning: Boolean = true,
    val quizQuestionCount: Int = 10,
    val searchQuery: String = "",
    val searchResults: List<VocabularyEntry> = emptyList(),
    val dictionaryQuery: String = "",
    val dictionaryResults: List<DictionaryVocabulary> = emptyList(),
    val selectedDictionaryVocabulary: DictionaryVocabulary? = null,
    val kanjiQuery: String = "",
    val kanjiResults: List<DictionaryKanji> = emptyList(),
    val selectedDictionaryKanji: DictionaryKanji? = null
)
