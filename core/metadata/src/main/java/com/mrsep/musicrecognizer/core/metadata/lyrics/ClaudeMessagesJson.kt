package com.mrsep.musicrecognizer.core.metadata.lyrics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ClaudeMessagesRequestJson(
    @SerialName("model") val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    @SerialName("stream") val stream: Boolean,
    @SerialName("system") val system: String,
    @SerialName("messages") val messages: List<MessageJson>,
) {
    @Serializable
    data class MessageJson(
        @SerialName("role") val role: String,
        @SerialName("content") val content: String,
    )
}

@Serializable
internal data class ClaudeStreamEventJson(
    @SerialName("type") val type: String,
    @SerialName("delta") val delta: DeltaJson?,
) {
    @Serializable
    data class DeltaJson(
        @SerialName("text") val text: String?,
        @SerialName("stop_reason") val stopReason: String?,
    )
}
