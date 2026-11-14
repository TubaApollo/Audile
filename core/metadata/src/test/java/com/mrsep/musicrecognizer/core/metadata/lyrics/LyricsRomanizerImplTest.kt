package com.mrsep.musicrecognizer.core.metadata.lyrics

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("SpellCheckingInspection")
internal class LyricsRomanizerImplTest {

    @Test
    fun romanizesJapaneseLines() = runTest {
        val romanizer = LyricsRomanizerImpl(StandardTestDispatcher(testScheduler))
        val lines = listOf(
            "ありがとう",
            "東京は雨",
            "ちょっと待ってください",
            "コーヒーを飲んだ",
            "Hello world",
            "",
        )
        romanizer.romanize(lines) shouldBe mapOf(
            "ありがとう" to "arigatou",
            "東京は雨" to "toukyou wa ame",
            "ちょっと待ってください" to "chotto matte kudasai",
            "コーヒーを飲んだ" to "koohii o nonda",
        )
    }

    @Test
    fun romanizesKoreanLines() = runTest {
        val romanizer = LyricsRomanizerImpl(StandardTestDispatcher(testScheduler))
        val romanized = mapOf(
            "사랑해요" to "saranghaeyo",
            "음악이 좋아" to "eumagi joa",
            "나는 몰라 Baby" to "naneun molla Baby",
            "같이 굳이" to "gachi guji",
            "좋고 많다 싫지" to "joko manta silchi",
            "국물 합니다 있는" to "gungmul hamnida inneun",
            "설날 신라 종로 독립" to "seollal silla jongno dongnip",
            "닫히다 축하 없어" to "dachida chukha eopseo",
        )
        romanizer.romanize(romanized.keys + "Hello world") shouldBe romanized
    }

    @Test
    fun ignoresNonJapaneseLyrics() = runTest {
        val romanizer = LyricsRomanizerImpl(StandardTestDispatcher(testScheduler))
        romanizer.romanize(listOf("月亮代表我的心", "Hello world")).shouldBeEmpty()
    }
}
