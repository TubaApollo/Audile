package com.mrsep.musicrecognizer.core.metadata.lyrics

/**
 * Converts Hangul to Latin script following the Revised Romanization of Korean.
 * Only the linking of a final consonant to the next vowel is applied from the pronunciation rules.
 */
@Suppress("SpellCheckingInspection")
internal object HangulRomanizer {

    private const val FIRST_SYLLABLE = '가'
    private const val LAST_SYLLABLE = '힣'
    private const val VOWEL_COUNT = 21
    private const val TAIL_COUNT = 28
    private const val LEAD_RIEUL = 5
    private const val LEAD_IEUNG = 11
    private const val TAIL_RIEUL = 8

    private val LEADS = "g kk n d tt r m b pp s ss  j jj ch k t p h".split(' ')
    private val VOWELS = "a ae ya yae eo e yeo ye o wa wae oe yo u wo we wi yu eu ui i".split(' ')
    private val TAILS = " k k k n n n t l k m l l l p l m p p t t ng t t k t p t".split(' ')

    // Final consonants as they sound when carried over to the next syllable starting with a vowel
    private val LINKED_TAILS = " g kk ks n nj n d r lg lm lb ls lt lp r m b ps s ss ng j ch k t p ".split(' ')

    fun Char.isHangulSyllable() = this in FIRST_SYLLABLE..LAST_SYLLABLE

    fun romanize(text: String) = buildString {
        var previousTail = 0
        text.forEachIndexed { index, char ->
            if (!char.isHangulSyllable()) {
                append(char)
                previousTail = 0
                return@forEachIndexed
            }
            val tail = (char - FIRST_SYLLABLE) % TAIL_COUNT
            val lead = char.leadIndex()
            append(if (lead == LEAD_RIEUL && previousTail == TAIL_RIEUL) "l" else LEADS[lead])
            append(VOWELS[(char - FIRST_SYLLABLE) / TAIL_COUNT % VOWEL_COUNT])
            val next = text.getOrNull(index + 1)
            val isLinked = next != null && next.isHangulSyllable() && next.leadIndex() == LEAD_IEUNG
            append(if (isLinked) LINKED_TAILS[tail] else TAILS[tail])
            previousTail = tail
        }
    }

    private fun Char.leadIndex() = (this - FIRST_SYLLABLE) / (VOWEL_COUNT * TAIL_COUNT)
}
