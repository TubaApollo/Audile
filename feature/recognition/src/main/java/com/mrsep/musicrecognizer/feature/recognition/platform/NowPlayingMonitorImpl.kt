package com.mrsep.musicrecognizer.feature.recognition.platform

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import com.mrsep.musicrecognizer.core.domain.track.NowPlayingMonitor
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.metadata.lyrics.LyricsFetcher
import com.mrsep.musicrecognizer.feature.recognition.service.NowPlayingListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transform
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

@OptIn(ExperimentalCoroutinesApi::class)
internal class NowPlayingMonitorImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val lyricsFetcher: LyricsFetcher,
) : NowPlayingMonitor {

    override val trackFlow: Flow<Track?> = NowPlayingListenerService.isConnected
        .flatMapLatest { isConnected ->
            if (isConnected) activeSessionTrackFlow() else flowOf(null)
        }
        // Players report their state periodically, skip the updates that don't change the position
        .distinctUntilChanged { old, new ->
            old == new || (old != null && new != null && old.isInSyncWith(new))
        }

    private val lyricsFlow = trackFlow
        .distinctUntilChangedBy { track -> track?.songKey() }
        .mapLatest { track ->
            val lyrics = track?.let { (lyricsFetcher.fetch(track) as? NetworkResult.Success)?.data }
            track?.songKey() to lyrics
        }

    override val trackWithLyricsFlow: Flow<Track?> = combine(trackFlow, lyricsFlow, ::Pair)
        .transform { (track, fetched) ->
            val (songKey, lyrics) = fetched
            when {
                track == null -> emit(null)
                // Otherwise the lyrics of the new track are still being fetched
                track.songKey() == songKey -> emit(track.copy(lyrics = lyrics))
            }
        }

    private fun activeSessionTrackFlow(): Flow<Track?> = callbackFlow {
        val sessionManager = checkNotNull(appContext.getSystemService<MediaSessionManager>())
        val listenerComponent = ComponentName(appContext, NowPlayingListenerService::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        var controllers = emptyList<MediaController>()

        fun sendTrack() {
            val track = controllers.firstNotNullOfOrNull { it.takeIf { it.isPlaying() }?.toTrack() }
                ?: controllers.firstNotNullOfOrNull { it.toTrack() }
            trySend(track)
        }

        val controllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = sendTrack()
            override fun onMetadataChanged(metadata: MediaMetadata?) = sendTrack()
        }
        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { activeControllers ->
            controllers.forEach { it.unregisterCallback(controllerCallback) }
            controllers = activeControllers.orEmpty()
            controllers.forEach { it.registerCallback(controllerCallback, mainHandler) }
            sendTrack()
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsListener,
                listenerComponent,
                mainHandler
            )
            sessionsListener.onActiveSessionsChanged(
                sessionManager.getActiveSessions(listenerComponent)
            )
        } catch (_: SecurityException) {
            // The access has just been revoked
            trySend(null)
        }
        awaitClose {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            controllers.forEach { it.unregisterCallback(controllerCallback) }
        }
    }

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    private fun MediaController.toTrack(): Track? {
        val metadata = metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) return null
        val position = playbackState?.takeIf { isPlaying() }?.run {
            val sinceUpdate = SystemClock.elapsedRealtime() - lastPositionUpdateTime
            (position + (sinceUpdate * playbackSpeed).toLong()).milliseconds
        }
        return Track(
            id = NowPlayingMonitor.TRACK_ID,
            title = title,
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "",
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            releaseDate = null,
            duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                .takeIf { it > 0 }?.milliseconds,
            recognizedAt = position,
            // The track is not recognized, any value will do
            recognizedBy = RecognitionProvider.entries.first(),
            recognitionDate = Instant.now(),
            lyrics = null,
            isrc = null,
            artworkThumbUrl = null,
            artworkUrl = null,
            trackLinks = emptyMap(),
            properties = Track.Properties(
                isFavorite = false,
                isViewed = true,
                themeSeedColor = null,
            ),
        )
    }

    private fun Track.songKey() = Triple(title, artist, duration)

    private fun Track.isInSyncWith(other: Track): Boolean {
        if (songKey() != other.songKey()) return false
        val position = recognizedAt ?: return other.recognizedAt == null
        val otherPosition = other.recognizedAt ?: return false
        val sinceSync = java.time.Duration.between(recognitionDate, other.recognitionDate)
        return (position + sinceSync.toKotlinDuration() - otherPosition).absoluteValue < SYNC_TOLERANCE
    }

    private companion object {
        val SYNC_TOLERANCE = 1.seconds
    }
}
