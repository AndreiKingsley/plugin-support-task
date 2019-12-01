package com.h0tk3y.recommendation.plugin

import com.h0tk3y.player.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class RecommendationPlugin : MusicLibraryContributorPlugin {

    override val preferredOrder: Int
        get() = Int.MIN_VALUE


    private fun recommendatedPlaylist(currentLists : List<Playlist>) : Playlist = Playlist(
            "Auto-recommended",
            listOf(
                Track(
                    mapOf(
                        TrackMetadataKeys.ARTIST to "Queen",
                        TrackMetadataKeys.NAME to "Killer Queen",
                        TrackMetadataKeys.ALBUM to "Sheer Heart Attack"
                    ),
                    File("music/queen/sheer_heart_attack/killer_queen.mp3")
                )
            )
        )

    override fun contribute(current: MusicLibrary): MusicLibrary {
        current.playlists.add(recommendatedPlaylist(current.playlists))
        return current
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit

    override lateinit var musicAppInstance: MusicApp
}