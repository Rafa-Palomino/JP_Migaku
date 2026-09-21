package com.jpmigaku.app.presentation.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import com.jpmigaku.app.domain.model.DictionaryVocabulary
import com.jpmigaku.app.domain.model.DictionaryKanji
import com.jpmigaku.app.domain.model.VocabularyEntry
import androidx.hilt.navigation.compose.hiltViewModel
import com.jpmigaku.app.R
import com.jpmigaku.app.presentation.viewmodel.HomeScreen
import com.jpmigaku.app.presentation.viewmodel.HomeViewModel
import com.jpmigaku.app.presentation.viewmodel.QuizMode
import com.jpmigaku.app.presentation.viewmodel.StudyArea
import com.jpmigaku.app.domain.util.toRomaji

private val JPMigakuColorScheme = lightColorScheme(
    primary = Color.Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDD2),
    onPrimaryContainer = Color.Black,
    inversePrimary = Color.Red,
    secondary = Color.Red,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFCDD2),
    onSecondaryContainer = Color.Black,
    tertiary = Color.Red,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFCDD2),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color.DarkGray,
    surfaceTint = Color.Red,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    error = Color.Red,
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color.Black,
    outline = Color.Gray,
    outlineVariant = Color.LightGray,
    scrim = Color.Black
)

@Composable
fun JPMigakuApp() {
    MaterialTheme(colorScheme = JPMigakuColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            JPMigakuHomeScreen()
        }
    }
}

@Composable
fun JPMigakuHomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        when (uiState.screen) {
            HomeScreen.Home -> HomeContent(uiState, viewModel)
            HomeScreen.AddVocabulary -> AddVocabularyContent(uiState, viewModel)
            HomeScreen.PersonalVocabulary -> PersonalVocabularyContent(uiState, viewModel)
            HomeScreen.AddKanji -> AddKanjiContent(uiState, viewModel)
            HomeScreen.QuizMode -> QuizModeContent(uiState, viewModel)
            HomeScreen.List -> VocabularyListContent(uiState, viewModel)
            HomeScreen.Quiz -> QuizContent(uiState, viewModel)
            HomeScreen.Search -> SearchContent(uiState, viewModel)
            HomeScreen.Statistics -> StatisticsContent(uiState, viewModel)
            HomeScreen.Decks -> DecksContent(uiState, viewModel)
            HomeScreen.DeckDetail -> DeckDetailContent(uiState, viewModel)
            HomeScreen.Settings -> SettingsContent(uiState, viewModel)
        }
    }
}

@Composable
private fun HomeContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_brand_kanji),
                color = Color.Red,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        RedButton(onClick = viewModel::onSettingsClicked) {
            Text("⚙")
        }
    }
    Spacer(modifier = Modifier.height(24.dp))

    Spacer(modifier = Modifier.height(16.dp))
    StudyAreaHeader()
    Spacer(modifier = Modifier.height(4.dp))
    StudyAreaRow(
        label = stringResource(R.string.kanji_area),
        onPrevious = viewModel::onAddKanjiClicked,
        onNext = { viewModel.onQuizClicked(StudyArea.KANJI) }
    )
    Spacer(modifier = Modifier.height(8.dp))
    StudyAreaRow(
        label = stringResource(R.string.vocabulary_area),
        onPrevious = viewModel::onAddVocabularyClicked,
        onNext = { viewModel.onQuizClicked(StudyArea.VOCABULARY) }
    )

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = viewModel::onStatisticsClicked,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Red,
            contentColor = Color.White
        )
    ) {
        Text(stringResource(R.string.statistics_button))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = viewModel::onManageDecksClicked,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
    ) {
        Text(stringResource(R.string.manage_decks_button))
    }

    Spacer(modifier = Modifier.height(24.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(stringResource(R.string.home_version_credit), fontSize = 14.sp)
        Text(stringResource(R.string.home_copyright_credit), fontSize = 14.sp)
        Text(stringResource(R.string.home_jmdict_credit), fontSize = 14.sp)
        Text(stringResource(R.string.home_kanjidic_credit), fontSize = 14.sp)
        Text(stringResource(R.string.home_jlpt_credit), fontSize = 14.sp)
        Text(stringResource(R.string.home_software_credit), fontSize = 14.sp)
        uiState.feedback?.let { Text(it, fontSize = 14.sp) }
    }
}

@Composable
private fun StudyAreaHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HubLabel(
            text = stringResource(R.string.selection_navigation_label),
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
        HubLabel(
            text = stringResource(R.string.quiz_navigation_label),
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StudyAreaRow(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.width(80.dp),
            contentAlignment = Alignment.Center
        ) {
            ArrowButton(symbol = "‹", onClick = onPrevious)
        }
        HubLabel(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier.width(80.dp),
            contentAlignment = Alignment.Center
        ) {
            ArrowButton(symbol = "›", onClick = onNext)
        }
    }
}

@Composable
private fun HubLabel(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = textAlign,
        color = Color.Black,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    )
}

@Composable
private fun ArrowButton(symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Red,
            contentColor = Color.White
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun AddKanjiContent(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(0.22f)) {
            Text(text = stringResource(R.string.add_kanji_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            JlptLevelSelector(
                selectedLevel = uiState.addJlptLevel,
                onLevelSelected = viewModel::onJlptLevelChanged
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dictionary_search_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uiState.kanjiQuery,
                onValueChange = viewModel::onKanjiQueryChanged,
                label = { Text(stringResource(R.string.kanji_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                singleLine = true
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
        ) {
            if (uiState.kanjiResults.isNotEmpty()) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawVerticalScrollbar(listState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.kanjiResults, key = { it.character }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onDictionaryKanjiSelected(entry) }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(entry.character, style = MaterialTheme.typography.titleMedium)
                            Text(entry.meaning)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.20f)
        ) {
            uiState.feedback?.let { Text(it) }
            RedButton(onClick = viewModel::onBackClicked) {
                Text(stringResource(R.string.back_button))
            }
        }
    }
    uiState.selectedDictionaryKanji?.let { entry ->
        DictionaryKanjiDialog(uiState, entry, viewModel)
    }
}

@Composable
private fun DictionaryKanjiDialog(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    entry: DictionaryKanji,
    viewModel: HomeViewModel
) {
    AlertDialog(
        onDismissRequest = viewModel::onSelectedDictionaryKanjiDismissed,
        title = { Text(stringResource(R.string.kanji_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.character, style = MaterialTheme.typography.headlineSmall)
                Text("${stringResource(R.string.kanji_onyomi_label)}: ${entry.onyomi.ifBlank { "-" }}")
                Text("${stringResource(R.string.kanji_kunyomi_label)}: ${entry.kunyomi.ifBlank { "-" }}")
                Text(
                    "${stringResource(R.string.dictionary_meaning_label)}: ${entry.meaning}" +
                        if (entry.isEnglishFallback) {
                            " (${stringResource(R.string.dictionary_english_fallback)})"
                        } else {
                            ""
                        }
                )
                DeckManagementContent(uiState, viewModel)
            }
        },
        confirmButton = {
            RedButton(onClick = viewModel::onAddSelectedDictionaryKanji) {
                Text(stringResource(R.string.add_button))
            }
        },
        dismissButton = {
            RedButton(onClick = viewModel::onSelectedDictionaryKanjiDismissed) {
                Text(stringResource(R.string.back_button))
            }
        }
    )
}

@Composable
private fun AddVocabularyContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(0.22f)) {
            Text(text = stringResource(R.string.add_vocab_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            JlptLevelSelector(
                selectedLevel = uiState.addJlptLevel,
                onLevelSelected = viewModel::onJlptLevelChanged
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dictionary_search_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uiState.dictionaryQuery,
                onValueChange = viewModel::onDictionaryQueryChanged,
                label = { Text(stringResource(R.string.dictionary_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            RedButton(onClick = {
                val clipboardText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
                viewModel.onClipboardSearchLoaded(clipboardText)
            }) {
                Text(stringResource(R.string.clipboard_button))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
        ) {
            if (uiState.dictionaryResults.isNotEmpty()) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawVerticalScrollbar(listState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.dictionaryResults, key = { it.sequenceId }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onDictionaryVocabularySelected(entry) }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(entry.japanese, style = MaterialTheme.typography.titleMedium)
                            Text("${entry.reading} · ${entry.meaning}")
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.20f)
        ) {
            RedButton(onClick = viewModel::onAddManualVocabularyClicked) {
                Text(stringResource(R.string.manual_vocab_title))
            }
            uiState.feedback?.let { Text(it) }
            RedButton(onClick = viewModel::onBackClicked) {
                Text(stringResource(R.string.back_button))
            }
        }
    }
    uiState.selectedDictionaryVocabulary?.let { entry ->
        DictionaryVocabularyDialog(uiState, entry, viewModel)
    }
}

private fun Modifier.drawVerticalScrollbar(state: LazyListState): Modifier =
    drawBehind {
        val totalItems = state.layoutInfo.totalItemsCount
        if (totalItems <= 0) return@drawBehind

        val viewportHeight = size.height
        val contentHeight = viewportHeight * totalItems
        val thumbHeight = (viewportHeight * viewportHeight / contentHeight)
            .coerceAtLeast(32.dp.toPx())
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1f)
        val scrollOffset = state.firstVisibleItemIndex * viewportHeight +
            state.firstVisibleItemScrollOffset.toFloat()
        val thumbOffset = (scrollOffset / maxScroll) * (viewportHeight - thumbHeight)

        drawRoundRect(
            color = Color.Red,
            topLeft = Offset(size.width - 6.dp.toPx(), thumbOffset),
            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), thumbHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }

@Composable
private fun DictionaryVocabularyDialog(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    entry: DictionaryVocabulary,
    viewModel: HomeViewModel
) {
    AlertDialog(
        onDismissRequest = viewModel::onSelectedDictionaryVocabularyDismissed,
        title = { Text(stringResource(R.string.dictionary_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.japanese, style = MaterialTheme.typography.headlineSmall)
                Text("${stringResource(R.string.dictionary_reading_label)}: ${entry.reading}")
                Text("${stringResource(R.string.dictionary_romaji_label)}: ${entry.romaji}")
                Text(
                    "${stringResource(R.string.dictionary_meaning_label)}: ${entry.meaning}" +
                        if (entry.isEnglishFallback) {
                            " (${stringResource(R.string.dictionary_english_fallback)})"
                        } else {
                            ""
                        }
                )
                DeckManagementContent(uiState, viewModel)
            }
        },
        confirmButton = {
            RedButton(onClick = viewModel::onAddSelectedDictionaryVocabulary) {
                Text(stringResource(R.string.add_button))
            }
        },
        dismissButton = {
            RedButton(onClick = viewModel::onSelectedDictionaryVocabularyDismissed) {
                Text(stringResource(R.string.back_button))
            }
        }
    )
}

@Composable
private fun PersonalVocabularyContent(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    Text(text = stringResource(R.string.personal_vocab_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = uiState.addJapanese,
        onValueChange = viewModel::onJapaneseChanged,
        label = { Text(stringResource(R.string.field_japanese)) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = uiState.addReading,
        onValueChange = viewModel::onReadingChanged,
        label = { Text(stringResource(R.string.field_reading)) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = uiState.addMeaning,
        onValueChange = viewModel::onMeaningChanged,
        label = { Text(stringResource(R.string.field_meaning)) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    JlptLevelSelector(
        selectedLevel = uiState.addJlptLevel,
        onLevelSelected = viewModel::onJlptLevelChanged
    )
    Spacer(modifier = Modifier.height(12.dp))
    DeckManagementContent(uiState, viewModel)
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RedButton(onClick = viewModel::onSaveVocabulary) {
            Text(stringResource(R.string.save_button))
        }
        RedButton(onClick = viewModel::onBackClicked) {
            Text(stringResource(R.string.back_button))
        }
    }
    uiState.feedback?.let { Text(it) }
}

private val JLPT_LEVELS = listOf("", "N5", "N4", "N3", "N2", "N1")

@Composable
private fun JlptLevelSelector(
    selectedLevel: String,
    onLevelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedLevel.ifBlank {
        stringResource(R.string.jlpt_any_level)
    }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${stringResource(R.string.field_jlpt_level)}: $selectedLabel")
                Text("⌄")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            JLPT_LEVELS.forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            level.ifBlank {
                                stringResource(R.string.jlpt_any_level)
                            }
                        )
                    },
                    onClick = {
                        onLevelSelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DeckManagementContent(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    Text(text = stringResource(R.string.deck_selector_title))
    val automaticDeckName = when (uiState.screen) {
        HomeScreen.AddKanji -> "Kanji"
        HomeScreen.AddVocabulary, HomeScreen.PersonalVocabulary -> "Vocabulario"
        else -> null
    }
    val allowedKind = when (uiState.screen) {
        HomeScreen.AddKanji -> "KANJI"
        HomeScreen.AddVocabulary, HomeScreen.PersonalVocabulary -> "VOCABULARY"
        else -> null
    }
    uiState.decks
        .filter { deck -> deck.name != automaticDeckName && (allowedKind == null || deck.kind == allowedKind) }
        .forEach { deck ->
        val selected = deck.id in uiState.selectedDeckIds
        Spacer(modifier = Modifier.height(4.dp))
        RedButton(onClick = { viewModel.onDeckSelected(deck.id) }) {
            Text(if (selected) "${deck.name} ✓" else deck.name)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = uiState.newDeckName,
        onValueChange = viewModel::onDeckNameChanged,
        label = { Text(stringResource(R.string.new_deck_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    RedButton(onClick = viewModel::onCreateDeckClicked) {
        Text(stringResource(R.string.create_deck_button))
    }
}

@Composable
private fun DecksContent(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    Text(stringResource(R.string.manage_decks_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(12.dp))
    uiState.decks.forEach { deck ->
        RedButton(onClick = { viewModel.onDeckOpened(deck.id) }) {
            Text(deck.name)
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
    RedButton(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
}

@Composable
private fun DeckDetailContent(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    val deck = uiState.decks.firstOrNull { it.id == uiState.managedDeckId }
    Text(deck?.name ?: stringResource(R.string.manage_decks_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(12.dp))
    RedButton(onClick = viewModel::onAddVocabularyClicked) {
        Text(stringResource(R.string.add_vocab_button))
    }
    if (uiState.managedDeckEntries.isEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        RedButton(onClick = viewModel::onDeleteDeckClicked) {
            Text(stringResource(R.string.delete_deck_button))
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uiState.managedDeckEntries, key = { it.id }) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.japanese, style = MaterialTheme.typography.titleMedium)
                    Text("${entry.reading} · ${entry.meaningEs}")
                }
                RedButton(onClick = { viewModel.onRemoveEntryFromDeck(entry) }) {
                    Text(stringResource(R.string.remove_button))
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    RedButton(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
}

@Composable
private fun SettingsContent(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.settings_quiz_title), style = MaterialTheme.typography.titleMedium)
    SettingSwitchRow(
        label = stringResource(R.string.settings_show_romaji),
        checked = uiState.showRomaji,
        onCheckedChange = viewModel::onShowRomajiChanged
    )
    SettingSwitchRow(
        label = stringResource(R.string.settings_show_kana),
        checked = uiState.showKanaInVocabulary,
        onCheckedChange = viewModel::onShowKanaChanged
    )
    SettingSwitchRow(
        label = stringResource(R.string.settings_show_kanji_meaning),
        checked = uiState.showKanjiMeaning,
        onCheckedChange = viewModel::onShowKanjiMeaningChanged
    )

    var expanded by remember { mutableStateOf(false) }
    Box {
        RedButton(onClick = { expanded = true }) {
            Text("${stringResource(R.string.settings_quiz_questions)}: ${uiState.quizQuestionCount}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (5..100 step 5).forEach { count ->
                DropdownMenuItem(
                    text = { Text(count.toString()) },
                    onClick = {
                        viewModel.onQuizQuestionCountChanged(count)
                        expanded = false
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    uiState.feedback?.let { Text(it) }
    RedButton(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Red,
                checkedTrackColor = Color.Red.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun RedButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Red,
            contentColor = Color.White,
            disabledContainerColor = Color.LightGray,
            disabledContentColor = Color.DarkGray
        ),
        content = content
    )
}

@Composable
private fun VocabularyListContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    Text(text = stringResource(R.string.entries_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uiState.vocabularies) { entry ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = entry.japanese, style = MaterialTheme.typography.titleMedium)
                Text(text = "${entry.reading} · ${entry.meaningEs}")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
}

@Composable
private fun QuizModeContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.quiz_mode_title),
            color = Color.Black,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        QuizDeckSelector(uiState, viewModel)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Preguntas por sesión: ${uiState.quizQuestionCount}",
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.studyArea == StudyArea.VOCABULARY) {
            Text(
                text = stringResource(R.string.quiz_vocabulary_title),
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.STUDY, stringResource(R.string.quiz_study_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.JAPANESE_TO_SPANISH_SELECTION, stringResource(R.string.quiz_jp_to_es_selection_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.JAPANESE_TO_SPANISH_WRITTEN, stringResource(R.string.quiz_jp_to_es_written_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.SPANISH_TO_JAPANESE_SELECTION, stringResource(R.string.quiz_es_to_jp_selection_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.SPANISH_TO_JAPANESE_WRITTEN, stringResource(R.string.quiz_es_to_jp_written_button))
        } else {
            Text(
                text = stringResource(R.string.quiz_kanji_title),
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.KANJI_STUDY, stringResource(R.string.quiz_study_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.KANJI_TO_SPANISH_SELECTION, stringResource(R.string.quiz_kanji_to_es_selection_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.SPANISH_TO_KANJI_SELECTION, stringResource(R.string.quiz_es_to_kanji_selection_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.KANJI_TO_READINGS_SELECTION, stringResource(R.string.quiz_kanji_to_readings_selection_button))
            QuizModeButton(viewModel, com.jpmigaku.app.presentation.viewmodel.QuizMode.READINGS_TO_KANJI_SELECTION, stringResource(R.string.quiz_readings_to_kanji_selection_button))
        }
        Spacer(modifier = Modifier.height(16.dp))
        uiState.feedback?.let { Text(it, color = Color.Black) }
        RedButton(onClick = viewModel::onBackClicked) {
            Text(stringResource(R.string.back_button))
        }
    }

}

@Composable
private fun QuizDeckSelector(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    val allowedKind = when (uiState.studyArea) {
        StudyArea.KANJI -> "KANJI"
        StudyArea.VOCABULARY -> "VOCABULARY"
    }
    val decks = uiState.decks.filter { it.kind == allowedKind }
    val selectedDeck = decks.firstOrNull { it.id == uiState.selectedQuizDeckId }
    var expanded by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.quiz_deck_selector_title),
        color = Color.Black,
        style = MaterialTheme.typography.labelLarge
    )
    Box {
        RedButton(onClick = { expanded = true }, enabled = decks.isNotEmpty()) {
            Text(selectedDeck?.name ?: stringResource(R.string.quiz_deck_select_hint))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            decks.forEach { deck ->
                DropdownMenuItem(
                    text = { Text(deck.name) },
                    onClick = {
                        viewModel.onQuizDeckSelected(deck.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun QuizModeButton(
    viewModel: HomeViewModel,
    mode: com.jpmigaku.app.presentation.viewmodel.QuizMode,
    label: String
) {
    RedButton(onClick = { viewModel.onStartQuizClicked(mode) }) {
        Text(label)
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SearchContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    Text(text = stringResource(R.string.search_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = viewModel::onSearchQueryChanged,
        label = { Text(stringResource(R.string.search_hint)) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = viewModel::onPerformSearch) {
        Text(stringResource(R.string.search_button))
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (uiState.searchResults.isNotEmpty()) {
        Text(text = "${uiState.searchResults.size} resultados", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.searchResults) { entry ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = entry.japanese, style = MaterialTheme.typography.titleMedium)
                    Text(text = "${entry.reading} · ${entry.meaningEs}")
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
}

@Composable
private fun StatisticsContent(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState,
    viewModel: HomeViewModel
) {
    Text(text = stringResource(R.string.statistics_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = stringResource(R.string.statistics_vocab_count, uiState.vocabularies.size))
    Text(text = stringResource(R.string.statistics_deck_count, uiState.decks.size))
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
}

@Composable
private fun QuizContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    if (uiState.quizCompleted) {
        Text(
            text = stringResource(R.string.quiz_completed_title),
            color = Color.Black,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.quiz_correct_answers, uiState.quizCorrectAnswers))
        Text(stringResource(R.string.quiz_incorrect_answers, uiState.quizIncorrectAnswers))
        Spacer(modifier = Modifier.height(16.dp))
        RedButton(onClick = viewModel::onBackClicked) {
            Text(stringResource(R.string.back_button))
        }
        return
    }
    if (uiState.quizEntry == null) {
        Text(
            text = stringResource(R.string.quiz_empty_message),
            color = Color.Black,
            style = MaterialTheme.typography.bodyLarge
        )
        return
    }

    Text(
        text = stringResource(R.string.quiz_title),
        color = Color.Black,
        style = MaterialTheme.typography.headlineSmall
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Preguntas restantes: ${uiState.quizQuestionsRemaining}",
        color = Color.Black,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = buildQuizCardText(uiState),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp)
    )
    Spacer(modifier = Modifier.height(12.dp))

    when {
        uiState.quizMode.usesSelectionOptions() -> {
            uiState.quizOptions.forEach { option ->
                RedButton(onClick = { viewModel.onQuizOptionSelected(option) }) {
                    Text(option.quizOptionDisplay(uiState))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        uiState.quizMode.isStudyMode() -> {
            RedButton(onClick = viewModel::onSubmitQuizAnswer) {
                Text("Siguiente")
            }
        }

        else -> {
            OutlinedTextField(
                value = uiState.quizAnswer,
                onValueChange = viewModel::onQuizAnswerChanged,
                label = { Text(stringResource(R.string.quiz_answer_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            RedButton(onClick = viewModel::onSubmitQuizAnswer) {
                Text(stringResource(R.string.submit_button))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    uiState.quizFeedback?.let { Text(it) }
}

private fun VocabularyEntry.japaneseQuizDisplay(
    showKana: Boolean,
    showRomaji: Boolean
): String = buildString {
    append(japanese)
    if (kind == "KANJI" && reading.isNotBlank()) {
        append("\n")
        append(reading)
    } else if (showKana && reading.isNotBlank()) {
        append("\nFurigana: ")
        append(reading)
    }

    val displayedRomaji = romaji.ifBlank { reading.toRomaji() }
    if (showRomaji && displayedRomaji.isNotBlank()) {
        append("\nRomaji: ")
        append(displayedRomaji)
    }
}

private fun buildQuizCardText(
    uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState
): AnnotatedString {
    val entry = requireNotNull(uiState.quizEntry)
    return buildAnnotatedString {
        when (uiState.quizMode) {
            QuizMode.JAPANESE_TO_SPANISH -> {
                append("Traduce al espanol:")
                appendQuizEntry(entry)
                if (uiState.showKanjiMeaning && entry.kind == "KANJI") {
                    appendQuizMeaning(entry)
                }
            }

            QuizMode.JAPANESE_TO_SPANISH_SELECTION,
            QuizMode.JAPANESE_TO_SPANISH_WRITTEN -> {
                append("Traduce al espanol:")
                appendQuizEntry(entry)
            }

            QuizMode.SPANISH_TO_JAPANESE,
            QuizMode.SPANISH_TO_JAPANESE_SELECTION,
            QuizMode.SPANISH_TO_JAPANESE_WRITTEN -> {
                append("Traduce al japones: ${entry.meaningEs}")
            }

            QuizMode.STUDY -> {
                append("Estudia:")
                appendQuizEntry(entry, includeMeaning = true)
            }

            QuizMode.KANJI_STUDY -> {
                append("Estudia kanji:")
                appendQuizEntry(entry)
                if (uiState.showKanjiMeaning) appendQuizMeaning(entry)
            }

            QuizMode.KANJI_TO_SPANISH_SELECTION -> {
                append("Selecciona el significado:")
                appendQuizEntry(entry)
                if (uiState.showKanjiMeaning) appendQuizMeaning(entry)
            }

            QuizMode.SPANISH_TO_KANJI_SELECTION -> {
                append("Selecciona el kanji: ${entry.meaningEs}")
            }

            QuizMode.KANJI_TO_READINGS_SELECTION -> {
                append("Selecciona la lectura:")
                appendQuizKanji(entry)
                if (uiState.showKanjiMeaning) appendQuizMeaning(entry)
            }

            QuizMode.READINGS_TO_KANJI_SELECTION -> {
                append("Selecciona el kanji:")
                appendQuizReadings(entry)
                if (uiState.showKanjiMeaning) appendQuizMeaning(entry)
            }
        }
    }
}

private fun AnnotatedString.Builder.appendQuizEntry(
    entry: VocabularyEntry,
    includeMeaning: Boolean = false
) {
    appendQuizKanji(entry)
    if (entry.reading.isNotBlank()) {
        withStyle(SpanStyle(fontSize = 24.sp)) {
            append("\n")
            append(entry.reading)
        }
    }
    if (includeMeaning) appendQuizMeaning(entry)
}

private fun AnnotatedString.Builder.appendQuizKanji(entry: VocabularyEntry) {
    withStyle(
        SpanStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    ) {
        append("\n")
        append(entry.japanese)
    }
}

private fun AnnotatedString.Builder.appendQuizReadings(entry: VocabularyEntry) {
    withStyle(SpanStyle(fontSize = 24.sp)) {
        append("\n")
        append(entry.reading.ifBlank { "Sin lectura registrada" })
    }
}

private fun AnnotatedString.Builder.appendQuizMeaning(entry: VocabularyEntry) {
    withStyle(SpanStyle(fontSize = 24.sp)) {
        append("\n")
        append(entry.meaningEs)
    }
}

private fun String.quizOptionDisplay(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState): String {
    if (uiState.quizMode !in setOf(
            QuizMode.SPANISH_TO_JAPANESE_SELECTION,
            QuizMode.SPANISH_TO_KANJI_SELECTION,
            QuizMode.READINGS_TO_KANJI_SELECTION
        )
    ) {
        return this
    }

    val entry = uiState.vocabularies.firstOrNull { it.japanese == this } ?: return this
    return entry.japaneseQuizDisplay(
        showKana = uiState.showKanaInVocabulary,
        showRomaji = uiState.showRomaji
    )
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

private fun QuizMode.isStudyMode(): Boolean = this == QuizMode.STUDY || this == QuizMode.KANJI_STUDY
