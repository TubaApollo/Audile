package com.mrsep.musicrecognizer.core.metadata.lyrics

import android.content.Context
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsTranslationConfig
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkError
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.LyricsTranslator
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

internal class LyricsTranslatorImpl @Inject constructor(
    private val httpClientLazy: dagger.Lazy<HttpClient>,
    private val json: Json,
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LyricsTranslator {

    override fun translate(
        lines: List<String>,
        config: LyricsTranslationConfig,
    ): Flow<NetworkResult<Map<String, String>>> = flow {
        val sourceLines = lines.filter { it.isNotBlank() }.distinct()
        if (sourceLines.isEmpty()) return@flow
        val language = if (config.language.isEmpty()) {
            Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
        } else {
            Locale.forLanguageTag(config.language).getDisplayName(Locale.ENGLISH)
        }
        // Each translation is billed, so reuse the previous result for the same lyrics
        val cacheFile = getCacheFile(sourceLines, config.model, language)
        val cached = readCache(cacheFile)
        if (cached != null) {
            emit(NetworkResult.Success(cached))
            return@flow
        }
        try {
            requestTranslation(sourceLines, config, language, cacheFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            emit(NetworkError.BadConnection(e.message))
        } catch (e: Exception) {
            emit(NetworkError.UnhandledError(message = e.message ?: "", cause = e))
        }
    }.flowOn(ioDispatcher)

    private suspend fun FlowCollector<NetworkResult<Map<String, String>>>.requestTranslation(
        lines: List<String>,
        config: LyricsTranslationConfig,
        language: String,
        cacheFile: File,
    ) {
        val request = httpClientLazy.get().preparePost("https://api.anthropic.com/v1/messages") {
            timeout {
                requestTimeoutMillis = 300_000
                socketTimeoutMillis = 120_000
            }
            header("x-api-key", config.apiKey)
            header("anthropic-version", "2026-10-21")
            contentType(ContentType.Application.Json)
            setBody(
                ClaudeMessagesRequestJson(
                    model = config.model,
                    maxTokens = 64_000,
                    stream = true,
                    // Keep the thinking short so that the first lines arrive sooner
                    outputConfig = ClaudeMessagesRequestJson.OutputConfigJson(effort = "low"),
                    system = "You translate song lyrics into $language. " +
                            "The user message lists the lyric lines as \"<number>|<line>\". " +
                            "Translate every line in the context of the whole song, keeping " +
                            "its meaning and tone rather than translating word for word. " +
                            "Reply with exactly one line per input line, in the same order " +
                            "and in the same format \"<number>|<translation>\", and nothing else. " +
                            "Repeat a line unchanged if it is already in $language " +
                            "or has nothing to translate.",
                    messages = listOf(
                        ClaudeMessagesRequestJson.MessageJson(
                            role = "user",
                            content = lines.withIndex().joinToString("\n") { (index, line) ->
                                "${index + 1}|$line"
                            },
                        )
                    ),
                )
            )
        }
        request.execute { response ->
            if (!response.status.isSuccess()) {
                emit(NetworkError.HttpError(response.status.value, response.status.description))
                return@execute
            }
            // Original lines are kept even if unchanged to track the completeness
            val translations = mutableMapOf<String, String>()
            val pendingText = StringBuilder()
            var stopReason: String? = null
            val channel = response.bodyAsChannel()
            while (true) {
                val sseLine = channel.readUTF8Line() ?: break
                if (!sseLine.startsWith(SSE_DATA_PREFIX)) continue
                val event = json.decodeFromString<ClaudeStreamEventJson>(
                    sseLine.removePrefix(SSE_DATA_PREFIX)
                )
                if (event.type == "error") break
                event.delta?.stopReason?.let { stopReason = it }
                val text = event.delta?.text ?: continue
                pendingText.append(text)
                if ('\n' !in text) continue
                val completeLines = pendingText.substring(0, pendingText.lastIndexOf('\n'))
                pendingText.delete(0, completeLines.length + 1)
                translations.putTranslatedLines(completeLines, lines)
                emit(NetworkResult.Success(translations.withoutUnchangedLines()))
            }
            translations.putTranslatedLines(pendingText.toString(), lines)
            // A refused or truncated response has another stop reason
            if (stopReason != "end_turn" || translations.isEmpty()) {
                emit(NetworkError.UnhandledError("Unexpected translation response: $stopReason"))
                return@execute
            }
            emit(NetworkResult.Success(translations.withoutUnchangedLines()))
            if (translations.size == lines.size) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(json.encodeToString(translations.withoutUnchangedLines()))
            }
        }
    }

    private fun MutableMap<String, String>.putTranslatedLines(text: String, lines: List<String>) {
        for (translatedLine in text.lineSequence()) {
            val number = translatedLine.substringBefore('|').trim().toIntOrNull() ?: continue
            val line = lines.getOrNull(number - 1) ?: continue
            put(line, translatedLine.substringAfter('|').trim())
        }
    }

    private fun Map<String, String>.withoutUnchangedLines() = filter { (line, translation) ->
        translation.isNotBlank() && translation != line
    }

    private fun getCacheFile(lines: List<String>, model: String, language: String): File {
        val key = (listOf(model, language) + lines).joinToString("\n")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(appContext.cacheDir, "$CACHE_DIR_NAME/$hash.json")
    }

    private fun readCache(file: File): Map<String, String>? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Map<String, String>>(file.readText()) }.getOrNull()
    }

    private companion object {
        const val CACHE_DIR_NAME = "lyrics_translations"
        const val SSE_DATA_PREFIX = "data:"
    }
}
