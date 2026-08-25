package com.jpmigaku.app.presentation.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jpmigaku.app.R
import com.jpmigaku.app.presentation.viewmodel.HomeScreen
import com.jpmigaku.app.presentation.viewmodel.HomeViewModel
import com.jpmigaku.app.presentation.viewmodel.StudyArea

@Composable
fun JPMigakuApp() {
    MaterialTheme {
        JPMigakuHomeScreen()
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
            HomeScreen.AddKanji -> AddKanjiContent(viewModel)
            HomeScreen.QuizMode -> QuizModeContent(uiState, viewModel)
            HomeScreen.List -> VocabularyListContent(uiState, viewModel)
            HomeScreen.Quiz -> QuizContent(uiState, viewModel)
            HomeScreen.Search -> SearchContent(uiState, viewModel)
            HomeScreen.Statistics -> StatisticsContent(uiState, viewModel)
        }
    }
}

@Composable
private fun HomeContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    Text(
        text = stringResource(R.string.home_title),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
    )
    Spacer(modifier = Modifier.height(24.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HubLabel(
            text = stringResource(R.string.search_button),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.weight(1f))
        HubLabel(
            text = stringResource(R.string.quiz_button),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    StudyAreaRow(
        label = stringResource(R.string.kanji_area),
        onPrevious = { viewModel.onStudyAreaChanged(StudyArea.VOCABULARY) },
        onNext = { viewModel.onStudyAreaChanged(StudyArea.VOCABULARY) }
    )
    Spacer(modifier = Modifier.height(8.dp))
    StudyAreaRow(
        label = stringResource(R.string.vocabulary_area),
        onPrevious = { viewModel.onStudyAreaChanged(StudyArea.KANJI) },
        onNext = { viewModel.onStudyAreaChanged(StudyArea.KANJI) }
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

    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = uiState.feedback ?: stringResource(R.string.status_placeholder),
    )
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
        ArrowButton(symbol = "‹", onClick = onPrevious)
        HubLabel(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        ArrowButton(symbol = "›", onClick = onNext)
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
private fun AddKanjiContent(viewModel: HomeViewModel) {
    Text(text = stringResource(R.string.add_kanji_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = stringResource(R.string.add_kanji_description))
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = "",
        onValueChange = {},
        label = { Text(stringResource(R.string.field_kanji)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = false
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = "",
        onValueChange = {},
        label = { Text(stringResource(R.string.field_kanji_meaning)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = false
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = stringResource(R.string.kanji_pending_message))
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
}

@Composable
private fun AddVocabularyContent(uiState: com.jpmigaku.app.presentation.viewmodel.HomeUiState, viewModel: HomeViewModel) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    Text(text = stringResource(R.string.add_vocab_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(12.dp))

    Button(onClick = {
        val clipboardText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
        viewModel.onClipboardTextLoaded(clipboardText)
    }) {
        Text(stringResource(R.string.clipboard_button))
    }
    Spacer(modifier = Modifier.height(8.dp))

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
    Spacer(modifier = Modifier.height(12.dp))

    Text(text = stringResource(R.string.deck_selector_title))
    uiState.decks.forEach { deck ->
        val selected = deck.id == uiState.selectedDeckId
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = { viewModel.onDeckSelected(deck.id) }, enabled = !selected) {
            Text(if (selected) "${deck.name} ✓" else deck.name)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = uiState.newDeckName,
        onValueChange = viewModel::onDeckNameChanged,
        label = { Text(stringResource(R.string.new_deck_hint)) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = viewModel::onCreateDeckClicked) {
        Text(stringResource(R.string.create_deck_button))
    }

    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::onSaveVocabulary) {
            Text(stringResource(R.string.save_button))
        }
        Button(onClick = viewModel::onBackClicked) {
            Text(stringResource(R.string.back_button))
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    uiState.feedback?.let { Text(it) }
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
    Text(text = stringResource(R.string.quiz_mode_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(16.dp))

    Button(onClick = { viewModel.onStartQuizClicked(com.jpmigaku.app.presentation.viewmodel.QuizMode.JAPANESE_TO_SPANISH) }) {
        Text(stringResource(R.string.quiz_jp_to_es_button))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = { viewModel.onStartQuizClicked(com.jpmigaku.app.presentation.viewmodel.QuizMode.SPANISH_TO_JAPANESE) }) {
        Text(stringResource(R.string.quiz_es_to_jp_button))
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = viewModel::onBackClicked) {
        Text(stringResource(R.string.back_button))
    }
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
    if (uiState.quizCompleted || uiState.quizEntry == null) {
        Text(text = stringResource(R.string.quiz_empty_message), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = viewModel::onBackClicked) {
            Text(stringResource(R.string.back_button))
        }
        return
    }

    Text(text = stringResource(R.string.quiz_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(12.dp))

    val questionDisplay = when (uiState.quizMode) {
        com.jpmigaku.app.presentation.viewmodel.QuizMode.JAPANESE_TO_SPANISH -> {
            "Traduce al español: ${uiState.quizEntry.japanese}"
        }
        com.jpmigaku.app.presentation.viewmodel.QuizMode.SPANISH_TO_JAPANESE -> {
            "Traduce al japonés: ${uiState.quizEntry.meaningEs}"
        }
    }
    Text(text = questionDisplay, style = MaterialTheme.typography.displaySmall)
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = uiState.quizAnswer,
        onValueChange = viewModel::onQuizAnswerChanged,
        label = { Text(stringResource(R.string.quiz_answer_hint)) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(onClick = viewModel::onSubmitQuizAnswer) {
        Text(stringResource(R.string.submit_button))
    }
    Spacer(modifier = Modifier.height(8.dp))
    uiState.quizFeedback?.let { Text(it) }
}
