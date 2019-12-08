package com.h0tk3y.recommendation.plugin

import com.h0tk3y.player.*
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.lang.StringBuilder

class RecommendationPlugin : MusicLibraryContributorPlugin {

    override val preferredOrder: Int
        get() = Int.MIN_VALUE


    lateinit var oldResult: List<Pair<Int, String>>

    private fun analyze() = Unit

    var leastPlayedTracks: List<Pair<Int, String>> = listOf()

    var recommendedTracks: List<Track> = listOf()


    private fun update(trackListenedToCount: Map<String, Int>, musicLibrary: MusicLibrary) {
        oldResult = leastPlayedTracks
        val res = mutableListOf<Pair<Int, String>>()
        val tracks: List<Track> = musicLibrary.playlists.flatMap { it.tracks }
        tracks.forEach {
            val trackName = it.metadata[TrackMetadataKeys.NAME]
            val cnt: Int? = trackListenedToCount[trackName]
            if (cnt != null) {
                res.add(Pair(cnt, trackName!!))
            }
        }
        res.sortBy { it.first }
        res.take(10)
        leastPlayedTracks = res
        analyze()
        recommendTracks(tracks)
    }

    private fun recommendTracks(tracks : List<Track>){
        val res: MutableList<Track> = mutableListOf()
        leastPlayedTracks.forEach {
            (_, trackName) ->
            res.add(tracks.single{it.metadata[TrackMetadataKeys.NAME]!! == trackName})
        }
        recommendedTracks = res
    }

    override fun contribute(current: MusicLibrary): MusicLibrary {
        val counterPlugin =
            musicAppInstance.findSinglePlugin(CounterPlugin::class.java.canonicalName) as? CounterPlugin
                ?: throw Exception("CounterPlugin must be initialized.")
        update(counterPlugin.trackListenedToCount, current)
        current.playlists.add(Playlist("Recommended tracks", recommendedTracks))
        return current
    }

    override fun init(persistedState: InputStream?) {
        if (persistedState == null) {
            oldResult = listOf()
        } else {
            val res = mutableListOf<Pair<Int, String>>()
            val text = persistedState.reader().readText().lines()
            for (l in text) {
                if (l.isEmpty()) continue
                val count = l.takeLastWhile { it.isDigit() }
                res.add(Pair(count.toInt(), l.removeSuffix(" -> $count")))
            }
            oldResult = res
        }
    }

    override fun persist(stateStream: OutputStream) {
        stateStream.write(buildString {
            oldResult.forEach { (count, stringTrack) ->
                appendln("$stringTrack -> $count")
            }
        }.toByteArray())
    }

    override lateinit var musicAppInstance: MusicApp
}



