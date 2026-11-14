package com.mrsep.musicrecognizer.core.domain.track

import com.mrsep.musicrecognizer.core.domain.track.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Observes the track played by other apps. Such a track is not stored in the library,
 * its [Track.recognizedAt] and [Track.recognitionDate] reflect the playback position.
 */
interface NowPlayingMonitor {

    /** Emits null if nothing is played or the access to media sessions is not granted. */
    val trackFlow: Flow<Track?>

    /** Same as [trackFlow], but the track is emitted along with its lyrics once they are fetched. */
    val trackWithLyricsFlow: Flow<Track?>

    companion object {
        const val TRACK_ID = "now_playing"
    }
}
