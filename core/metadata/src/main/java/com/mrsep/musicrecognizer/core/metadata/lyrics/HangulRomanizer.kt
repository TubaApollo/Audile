package com.mrsep.musicrecognizer.core.metadata.lyrics

/**
 * Converts Hangul to Latin script following the Revised Romanization of Korean,
 * which reflects the standard pronunciation: linking, nasalization, liquid assimilation,
 * palatalization and aspiration between adjacent syllables.
 * Sound changes that depend on the word structure (e.g. inserted "n" in compounds) are not covered.
 */
@Suppress("SpellCheckingInspection")
internal object HangulRomanizer {

    private const val FIRST_SYLLABLE = '가'
    private const val LAST_SYLLABLE = '힣'
    private const val VOWEL_COUNT = 21
    private const val TAIL_COUNT = 28

    // Indices of initial consonants
    private const val LEAD_G = 0
    private const val LEAD_N = 2
    private const val LEAD_D = 3
    private const val LEAD_R = 5
    private const val LEAD_M = 6
    private const val LEAD_S = 9
    private const val LEAD_SILENT = 11
    private const val LEAD_J = 12
    private const val LEAD_H = 18

    // Indices of final consonants
    private const val TAIL_NJ = 5
    private const val TAIL_NH = 6
    private const val TAIL_D = 7
    private const val TAIL_LT = 13
    private const val TAIL_LH = 15
    private const val TAIL_J = 22
    private const val TAIL_T = 25
    private const val TAIL_H = 27

    private const val VOWEL_I = 20

    private val LEADS = "g kk n d tt r m b pp s ss  j jj ch k t p h".split(' ')
    private val VOWELS = "a ae ya yae eo e yeo ye o wa wae oe yo u wo we wi yu eu ui i".split(' ')
    private val TAILS = " k k k n n n t l k m l l l p l m p p t t ng t t k t p t".split(' ')

    // Final consonants as they sound when carried over to the next syllable starting with a vowel
    private val LINKED_TAILS = " g kk ks n nj n d r lg lm lb ls lt lp r m b ps s ss ng j ch k t p ".split(' ')

    private val ASPIRATED_LEADS = mapOf(LEAD_G to "k", LEAD_D to "t", LEAD_J to "ch")
    private val NASALIZED_TAILS = mapOf("k" to "ng", "t" to "n", "p" to "m")

    fun Char.isHangulSyllable() = this in FIRST_SYLLABLE..LAST_SYLLABLE

    fun romanize(text: String) = buildString {
        var assimilatedLead: String? = null
        text.forEachIndexed { index, char ->
            if (!char.isHangulSyllable()) {
                append(char)
                return@forEachIndexed
            }
            val tail = (char - FIRST_SYLLABLE) % TAIL_COUNT
            val next = text.getOrNull(index + 1)?.takeIf { it.isHangulSyllable() }
            val (tailSound, nextLeadSound) = if (next != null) {
                getJunctionSounds(tail, next.leadIndex(), next.vowelIndex())
            } else {
                TAILS[tail] to null
            }
            append(assimilatedLead ?: LEADS[char.leadIndex()])
            append(VOWELS[char.vowelIndex()])
            append(tailSound)
            assimilatedLead = nextLeadSound
        }
    }

    /**
     * Returns the sound of the final consonant followed by the next syllable
     * and the sound of the initial consonant of that syllable, if it changes.
     */
    private fun getJunctionSounds(tail: Int, nextLead: Int, nextVowel: Int): Pair<String, String?> {
        val tailSound = TAILS[tail]
        return when {
            // Palatalization: 같이 -> gachi, 굳이 -> guji
            nextLead == LEAD_SILENT && nextVowel == VOWEL_I && tail == TAIL_D -> "j" to null
            nextLead == LEAD_SILENT && nextVowel == VOWEL_I && tail == TAIL_T -> "ch" to null
            nextLead == LEAD_SILENT && nextVowel == VOWEL_I && tail == TAIL_LT -> "lch" to null
            // Linking: 음악이 -> eumagi
            nextLead == LEAD_SILENT -> LINKED_TAILS[tail] to null
            // Aspiration: 닫히다 -> dachida, 맞히다 -> machida, 앉히다 -> anchida
            nextLead == LEAD_H && nextVowel == VOWEL_I && tail == TAIL_D -> "" to "ch"
            nextLead == LEAD_H && tail == TAIL_J -> "" to "ch"
            nextLead == LEAD_H && tail == TAIL_NJ -> "n" to "ch"
            // Aspiration: 좋고 -> joko, 많다 -> manta, 싫지 -> silchi
            tail.hasFinalH() && nextLead in ASPIRATED_LEADS -> {
                tailSound.takeIf { tail != TAIL_H }.orEmpty() to ASPIRATED_LEADS[nextLead]
            }
            tail == TAIL_H && nextLead == LEAD_S -> "" to null
            // Liquid assimilation: 설날 -> seollal
            nextLead == LEAD_N && tailSound == "l" -> "l" to "l"
            // Nasalization: 국물 -> gungmul, 합니다 -> hamnida
            nextLead == LEAD_N || nextLead == LEAD_M -> (NASALIZED_TAILS[tailSound] ?: tailSound) to null
            nextLead == LEAD_R -> when (tailSound) {
                // Liquid assimilation: 몰라 -> molla, 신라 -> silla
                "l", "n" -> "l" to "l"
                // Nasalization: 종로 -> jongno, 독립 -> dongnip
                "m", "ng" -> tailSound to "n"
                "k", "t", "p" -> NASALIZED_TAILS.getValue(tailSound) to "n"
                else -> tailSound to null
            }
            else -> tailSound to null
        }
    }

    private fun Int.hasFinalH() = this == TAIL_H || this == TAIL_NH || this == TAIL_LH

    private fun Char.leadIndex() = (this - FIRST_SYLLABLE) / (VOWEL_COUNT * TAIL_COUNT)

    private fun Char.vowelIndex() = (this - FIRST_SYLLABLE) / TAIL_COUNT % VOWEL_COUNT
}
