package com.mrsep.musicrecognizer.core.domain.track

import com.mrsep.musicrecognizer.core.domain.preferences.LyricsTranslationConfig
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import kotlinx.coroutines.flow.Flow

interface LyricsTranslator {

    /**
     * Emits the translations of the lyrics lines received so far while the translation
     * is in progress, except for the lines that are already in the target language.
     */
    fun translate(
        lines: List<String>,
        config: LyricsTranslationConfig,
    ): Flow<NetworkResult<Map<String, String>>>
}
