package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsStyle
import com.mrsep.musicrecognizer.core.domain.preferences.ThemeMode
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.ResultNotificationManager
import com.mrsep.musicrecognizer.core.domain.recognition.TrackMetadataFetchManager
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.LyricsRomanizer
import com.mrsep.musicrecognizer.core.domain.track.LyricsTranslator
import com.mrsep.musicrecognizer.core.domain.track.NowPlayingMonitor
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.Lyrics
import com.mrsep.musicrecognizer.core.domain.track.model.PlainLyrics
import com.mrsep.musicrecognizer.core.domain.track.model.SyncedLyrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
internal class LyricsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackRepository: TrackRepository,
    private val preferencesRepository: PreferencesRepository,
    trackMetadataFetchManager: TrackMetadataFetchManager,
    private val resultNotificationManager: ResultNotificationManager,
    private val lyricsRomanizer: LyricsRomanizer,
    private val lyricsTranslator: LyricsTranslator,
    private val nowPlayingMonitor: NowPlayingMonitor,
) : ViewModel() {

    private val args = LyricsScreen.Args(savedStateHandle)

    private val trackFlow = if (args.trackId == NowPlayingMonitor.TRACK_ID) {
        nowPlayingMonitor.trackWithLyricsFlow
    } else {
        trackRepository.getTrackFlow(args.trackId)
    }

    private val lyricsLinesFlow = trackFlow
        .map { track -> track?.lyrics?.contentLines().orEmpty() }
        .distinctUntilChanged()

    // Lyrics for which the translation is requested by the user
    private val translationRequestedLines = MutableStateFlow<List<String>?>(null)

    // Emitted separately so that the lyrics are not held back by the romanization
    @OptIn(ExperimentalCoroutinesApi::class)
    private val romajiFlow: Flow<Map<String, String>> = combine(
        flow = lyricsLinesFlow,
        flow2 = preferencesRepository.userPreferencesFlow.map { it.lyricsStyle.showRomaji },
    ) { lines, showRomaji -> lines.takeIf { showRomaji } }
        .distinctUntilChanged()
        .transformLatest { lines ->
            emit(emptyMap())
            lines?.let { emit(lyricsRomanizer.romanize(lines)) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val translationFlow: Flow<TranslationState> = combine(
        flow = lyricsLinesFlow,
        flow2 = preferencesRepository.userPreferencesFlow,
        flow3 = translationRequestedLines,
    ) { lines, preferences, requestedLines ->
        val style = preferences.lyricsStyle
        val config = preferences.lyricsTranslationConfig
        val isActive = style.showTranslation && config.apiKey.isNotBlank() &&
                (style.alwaysTranslate || requestedLines == lines)
        if (isActive) lines to config else null
    }
        .distinctUntilChanged()
        .transformLatest { request ->
            emit(TranslationState(isActive = request != null, lines = emptyMap()))
            request?.let { (lines, config) ->
                emitAll(
                    lyricsTranslator.translate(lines, config).map { result ->
                        TranslationState(isActive = true, lines = (result as? NetworkResult.Success)?.data)
                    }
                )
            }
        }

    val uiStateStream = combine(
        flow = trackFlow,
        flow2 = preferencesRepository.userPreferencesFlow,
        flow3 = trackMetadataFetchManager.isLyricsFetcherRunning(args.trackId),
        flow4 = romajiFlow,
        flow5 = translationFlow,
    ) { track, preferences, isLyricsFetcherRunning, romaji, translation ->
        if (track == null) return@combine LyricsUiState.TrackNotFound
        if (isLyricsFetcherRunning) return@combine LyricsUiState.Loading
        track.lyrics?.let { lyrics ->
            LyricsUiState.Success(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                artworkThumbUrl = track.artworkThumbUrl,
                artworkUrl = track.artworkUrl,
                lyrics = lyrics,
                romaji = romaji,
                translation = translation.lines.orEmpty(),
                translationFailed = translation.lines == null,
                translationAvailable = preferences.lyricsTranslationConfig.apiKey.isNotBlank(),
                translationButtonVisible = preferences.lyricsStyle.showTranslation &&
                        preferences.lyricsTranslationConfig.apiKey.isNotBlank() &&
                        !translation.isActive,
                trackDuration = track.duration,
                recognizedAt = track.recognizedAt,
                recognitionDate = track.recognitionDate,
                lyricsStyle = preferences.lyricsStyle,
                themeSeedColor = track.properties.themeSeedColor,
                artworkBasedThemeEnabled = preferences.artworkBasedThemeEnabled,
                themeMode = preferences.themeMode,
                usePureBlackForDarkTheme = preferences.usePureBlackForDarkTheme,
                isTrackViewed = track.properties.isViewed,
            )
        } ?: LyricsUiState.LyricsNotFound
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LyricsUiState.Loading
        )

    fun requestTranslation() {
        val lyrics = (uiStateStream.value as? LyricsUiState.Success)?.lyrics ?: return
        translationRequestedLines.value = lyrics.contentLines()
    }

    private fun Lyrics.contentLines() = when (this) {
        is PlainLyrics -> plain.lines()
        is SyncedLyrics -> lines.map { it.content }
    }

    // Lines are null if the translation has failed
    private data class TranslationState(val isActive: Boolean, val lines: Map<String, String>?)

    fun setLyricsStyle(newStyle: LyricsStyle) {
        viewModelScope.launch {
            preferencesRepository.setLyricsStyle(newStyle)
        }
    }

    fun setTrackAsViewed(trackId: String) {
        viewModelScope.launch {
            trackRepository.setViewed(trackId, true)
            resultNotificationManager.cancelBackgroundMatches(setOf(trackId))
        }
    }

    fun setThemeSeedColor(trackId: String, color: Int?) {
        viewModelScope.launch {
            trackRepository.setThemeSeedColor(trackId, color)
        }
    }
}

@Immutable
internal sealed class LyricsUiState {

    data object Loading : LyricsUiState()

    data object TrackNotFound : LyricsUiState()

    data object LyricsNotFound : LyricsUiState()

    data class Success(
        val trackId: String,
        val title: String,
        val artist: String,
        val artworkThumbUrl: String?,
        val artworkUrl: String?,
        val lyrics: Lyrics,
        val romaji: Map<String, String>,
        val translation: Map<String, String>,
        val translationFailed: Boolean,
        val translationAvailable: Boolean,
        val translationButtonVisible: Boolean,
        val trackDuration: Duration?,
        val recognizedAt: Duration?,
        val recognitionDate: Instant,
        val lyricsStyle: LyricsStyle,
        val themeSeedColor: Int?,
        val artworkBasedThemeEnabled: Boolean,
        val themeMode: ThemeMode,
        val usePureBlackForDarkTheme: Boolean,
        val isTrackViewed: Boolean,
    ) : LyricsUiState()
}
