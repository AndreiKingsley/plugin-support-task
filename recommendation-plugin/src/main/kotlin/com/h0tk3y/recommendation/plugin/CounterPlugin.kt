package com.h0tk3y.recommendation.plugin

import com.h0tk3y.player.*
import java.io.InputStream
import java.io.OutputStream

class CounterPlugin(override val musicAppInstance: MusicApp) :
    MusicLibraryContributorPlugin,
    PlaybackListenerPlugin {

    var runCount: Int = 0
        private set

    override val preferredOrder: Int
        get() = Int.MAX_VALUE

    companion object {
        const val TRACK_PLAYED_COUNT_KEY = "track-played-count"
    }

    override fun contribute(current: MusicLibrary): MusicLibrary = current.apply {
        current.playlists.forEach { playlist ->
            playlist.tracks.forEach { track ->
                updateMetadata(track)
            }
        }
    }

    private fun updateMetadata(track: Track) {
        val trackName = track.metadata[TrackMetadataKeys.NAME] ?: return
        val count = trackListenedToCount[trackName] ?: return
        track.metadata[TRACK_PLAYED_COUNT_KEY] = count.toString()
    }

    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
        if (oldPlaybackState.playlistPosition?.currentTrack != newPlaybackState.playlistPosition?.currentTrack) {
            val newTrack = newPlaybackState.playlistPosition?.currentTrack
            if (newTrack != null) {
                val trackName = newTrack.metadata[TrackMetadataKeys.NAME]
                if (trackName != null) {
                    trackListenedToCount.merge(trackName, 1, Int::plus)
                    contribute(musicAppInstance.musicLibrary) // TODO: optimize
                }
            }
        }
    }

    var trackListenedToCount: MutableMap<String, Int> = mutableMapOf()

    override fun init(persistedState: InputStream?) {
        if (persistedState != null) {
            val text = persistedState.reader().readText().lines()
            runCount = text[0].toIntOrNull() ?: 0
            for (l in text.drop(1)) {
                if (l.isEmpty()) continue
                val count = l.takeLastWhile { it.isDigit() }
                trackListenedToCount[l.removeSuffix(" -> $count")] = count.toInt()
            }
        }
        ++runCount
    }

    override fun persist(stateStream: OutputStream) {
        stateStream.write(buildString {
            appendln(runCount)
            trackListenedToCount.forEach { track, count ->
                appendln("$track -> $count")
            }
        }.toByteArray())
    }
}