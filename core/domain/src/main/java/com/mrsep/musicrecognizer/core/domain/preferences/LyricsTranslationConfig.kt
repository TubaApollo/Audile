package com.mrsep.musicrecognizer.core.domain.preferences

data class LyricsTranslationConfig(
    val apiKey: String,
    val model: String,
    /** BCP 47 language tag, empty to follow the system language */
    val language: String,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
    }
}
