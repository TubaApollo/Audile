package com.mrsep.musicrecognizer.core.metadata.lyrics

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.mrsep.musicrecognizer.core.common.di.DefaultDispatcher
import com.mrsep.musicrecognizer.core.domain.track.LyricsRomanizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.crizin.KoreanRomanizer
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

internal class LyricsRomanizerImpl @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : LyricsRomanizer {

    // Loading the dictionary is slow and memory-consuming, so defer it until first use
    private val tokenizer by lazy { Tokenizer() }

    override suspend fun romanize(lines: List<String>): Map<String, String> {
        // Kana distinguishes Japanese from Chinese, which also uses kanji
        val isJapanese = lines.any { line -> line.any { it.isKana() } }
        return withContext(defaultDispatcher) {
            lines.mapNotNull { line ->
                when {
                    line.contains(HANGUL_WORD) -> line to romanizeKoreanLine(line)
                    isJapanese && line.any { it.isKana() || it.isKanji() } -> line to romanizeLine(line)
                    else -> null
                }
            }.toMap()
        }
    }

    private fun romanizeLine(line: String): String {
        val reading = buildString {
            for (token in tokenizer.tokenize(line)) {
                val startsNewWord = token.partOfSpeechLevel1 != POS_SYMBOL &&
                        !token.attachesToPreviousWord() &&
                        isNotEmpty() && !last().isWhitespace() && last() !in OPENING_BRACKETS
                if (startsNewWord) append(' ')
                append(
                    PARTICLE_READINGS[token.surface]?.takeIf { token.partOfSpeechLevel1 == POS_PARTICLE }
                        // Words missing from the dictionary have no reading
                        ?: token.reading.takeUnless { it == "*" }
                        ?: token.surface
                )
            }
        }
        return Normalizer.normalize(kanaToRomaji(reading), Normalizer.Form.NFKC)
    }

    // Only Korean words are passed to the romanizer, because it capitalizes every word
    private fun romanizeKoreanLine(line: String) = line.replace(HANGUL_WORD) { word ->
        KoreanRomanizer.romanize(word.value).lowercase(Locale.ROOT)
    }

    private fun Token.attachesToPreviousWord() = when (partOfSpeechLevel1) {
        POS_AUXILIARY_VERB -> conjugationType !in COPULA_CONJUGATIONS
        POS_PARTICLE -> partOfSpeechLevel2 == POS_CONJUNCTIVE_PARTICLE && surface in ATTACHED_PARTICLES
        else -> partOfSpeechLevel2 == POS_SUFFIX
    }

    private fun kanaToRomaji(text: String) = buildString {
        var index = 0
        var isGeminated = false
        var isAfterMoraicN = false
        while (index < text.length) {
            val kana = text[index].toKatakana()
            val digraph = text.getOrNull(index + 1)?.let { next -> DIGRAPHS["$kana${next.toKatakana()}"] }
            val romaji = digraph ?: MONOGRAPHS[kana]
            when {
                kana == 'ッ' -> Unit
                kana == 'ー' -> lastOrNull()?.takeIf { it in VOWELS }?.let(::append)
                romaji == null -> append(PUNCTUATION[kana] ?: kana)
                else -> {
                    if (isGeminated) append(if (romaji.startsWith("ch")) 't' else romaji.first())
                    if (isAfterMoraicN && (romaji.first() in VOWELS || romaji.first() == 'y')) append('\'')
                    append(romaji)
                }
            }
            isGeminated = kana == 'ッ'
            isAfterMoraicN = kana == 'ン'
            index += if (digraph != null) 2 else 1
        }
    }

    private fun Char.toKatakana() = if (this in 'ぁ'..'ゖ') this + 0x60 else this

    private fun Char.isKana() = this in 'ぁ'..'ゖ' || this in 'ァ'..'ヺ'

    private fun Char.isKanji() = this in '一'..'鿿' || this == '々'

    @Suppress("SpellCheckingInspection")
    private companion object {
        const val POS_SYMBOL = "記号"
        const val POS_PARTICLE = "助詞"
        const val POS_CONJUNCTIVE_PARTICLE = "接続助詞"
        const val POS_AUXILIARY_VERB = "助動詞"
        const val POS_SUFFIX = "接尾"
        const val VOWELS = "aiueo"
        const val OPENING_BRACKETS = "「『（(［["
        val HANGUL_WORD = Regex("[가-힣]+")

        val COPULA_CONJUGATIONS = setOf("特殊・ダ", "特殊・デス")
        val ATTACHED_PARTICLES = setOf("て", "で", "ば")
        val PARTICLE_READINGS = mapOf("は" to "ワ", "へ" to "エ", "を" to "オ")
        val PUNCTUATION = mapOf('、' to ',', '。' to '.')

        val MONOGRAPHS = (
            "アa イi ウu エe オo カka キki クku ケke コko サsa シshi スsu セse ソso " +
                "タta チchi ツtsu テte トto ナna ニni ヌnu ネne ノno ハha ヒhi フfu ヘhe ホho " +
                "マma ミmi ムmu メme モmo ヤya ユyu ヨyo ラra リri ルru レre ロro ワwa ヰi ヱe ヲo ンn " +
                "ガga ギgi グgu ゲge ゴgo ザza ジji ズzu ゼze ゾzo ダda ヂji ヅzu デde ドdo " +
                "バba ビbi ブbu ベbe ボbo パpa ピpi プpu ペpe ポpo ヴvu " +
                "ァa ィi ゥu ェe ォo ャya ュyu ョyo ヮwa"
            ).split(' ').associate { it.first() to it.drop(1) }

        val DIGRAPHS = (
            "キャkya キュkyu キョkyo ギャgya ギュgyu ギョgyo シャsha シュshu ショsho シェshe " +
                "ジャja ジュju ジョjo ジェje チャcha チュchu チョcho チェche ヂャja ヂュju ヂョjo " +
                "ニャnya ニュnyu ニョnyo ヒャhya ヒュhyu ヒョhyo ビャbya ビュbyu ビョbyo " +
                "ピャpya ピュpyu ピョpyo ミャmya ミュmyu ミョmyo リャrya リュryu リョryo " +
                "ファfa フィfi フェfe フォfo フュfyu ティti ディdi テュtyu デュdyu トゥtu ドゥdu " +
                "ウィwi ウェwe ウォwo ヴァva ヴィvi ヴェve ヴォvo"
            ).split(' ').associate { it.take(2) to it.drop(2) }
    }
}
