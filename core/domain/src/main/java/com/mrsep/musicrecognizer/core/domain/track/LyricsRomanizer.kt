package com.mrsep.musicrecognizer.core.domain.track

interface LyricsRomanizer {

    /**
     * Returns the Latin transcription for each Japanese or Korean line of the lyrics,
     * or an empty map if the lyrics are in another language.
     */
    suspend fun romanize(lines: List<String>): Map<String, String>
}
