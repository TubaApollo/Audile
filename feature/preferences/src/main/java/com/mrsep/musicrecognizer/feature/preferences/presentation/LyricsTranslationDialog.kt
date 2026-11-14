package com.mrsep.musicrecognizer.feature.preferences.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsTranslationConfig
import com.mrsep.musicrecognizer.core.ui.components.PasswordInputField
import java.util.Locale
import com.mrsep.musicrecognizer.core.strings.R as StringsR

private val Models = mapOf(
    "claude-opus-5-5" to "Claude Opus 5.5",
    "claude-sonnet-5-5" to "Claude Sonnet 5.5",
    "claude-haiku-5-5" to "Claude Haiku 5.5",
    "claude-fable-5-2" to "Claude Fable 5.2",
)

// BCP 47 language tags
private val Languages = listOf(
    "ar", "cs", "de", "en", "es", "fr", "hi", "hu", "id", "it", "ja", "ko", "nl",
    "pl", "pt", "ru", "tr", "uk", "vi", "zh-Hans", "zh-Hant",
)

@Composable
internal fun LyricsTranslationDialog(
    config: LyricsTranslationConfig,
    onSaveClick: (LyricsTranslationConfig) -> Unit,
    onDismissClick: () -> Unit,
) {
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey) }
    var model by rememberSaveable { mutableStateOf(config.model) }
    var language by rememberSaveable { mutableStateOf(config.language) }
    val systemLanguageTitle = stringResource(StringsR.string.lyrics_translation_system_language)
    val languageTitles = remember(systemLanguageTitle) {
        mapOf("" to systemLanguageTitle) +
                Languages.associateWith { tag -> Locale.forLanguageTag(tag).displayName }
    }
    AlertDialog(
        title = {
            Text(text = stringResource(StringsR.string.pref_title_lyrics_translation))
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveClick(LyricsTranslationConfig(apiKey.trim(), model, language)) }
            ) {
                Text(text = stringResource(StringsR.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissClick) {
                Text(text = stringResource(StringsR.string.close))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(StringsR.string.lyrics_translation_dialog_message))
                PasswordInputField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = stringResource(StringsR.string.claude_api_key),
                    modifier = Modifier.fillMaxWidth()
                )
                OptionDropdownMenu(
                    label = stringResource(StringsR.string.lyrics_translation_model),
                    options = Models,
                    selectedOption = model,
                    onSelectOption = { model = it },
                )
                OptionDropdownMenu(
                    label = stringResource(StringsR.string.lyrics_translation_language),
                    options = languageTitles,
                    selectedOption = language,
                    onSelectOption = { language = it },
                )
            }
        },
        onDismissRequest = onDismissClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdownMenu(
    label: String,
    options: Map<String, String>,
    selectedOption: String,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = options[selectedOption] ?: selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            options.forEach { (option, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelectOption(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
