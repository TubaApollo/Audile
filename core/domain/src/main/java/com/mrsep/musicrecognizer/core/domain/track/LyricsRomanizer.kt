package com.mrsep.musicrecognizer.core.domain.track

interface LyricsRomanizer {

    /**
     * Returns the romaji transcription for each Japanese line of the lyrics,
     * or an empty map if the lyrics are not in Japanese.
     */
    suspend fun romanize(lines: List<String>): Map<String, String>
}
