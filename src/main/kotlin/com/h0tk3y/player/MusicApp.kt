package com.h0tk3y.player

import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty0
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    private val pluginsDirectory: File
        get() {
            val res = File("plugins")
            if (!res.exists()) {
                res.mkdirs()
            }
            return res
        }

    fun init() {
        try {
            plugins.forEach {
                val inputFile = File(pluginsDirectory, it.pluginId + ".mplug")
                if(inputFile.exists()){
                    inputFile.inputStream().use {inputStream ->
                        it.init(inputStream)
                    }
                } else {
                    it.init(null)
                }
            }
        } catch (e: PluginClassNotFoundException) {
            throw e
        }

        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        plugins.forEach {
            val outputFile = File(pluginsDirectory, it.pluginId + ".mplug")
            outputFile.createNewFile()
            outputFile.outputStream().use {
                outputStream -> it.persist(outputStream)
            }
        }
    }

    fun wipePersistedPluginData() {
        plugins.forEach {
            File(pluginsDirectory, it.pluginId + ".mplug").delete()
        }
    }

    private val pluginClassLoader: ClassLoader = URLClassLoader(
        pluginClasspath.map { it.toURI().toURL() }.toTypedArray()
    )

    private val plugins: List<MusicPlugin> by lazy {
        val loader = pluginClassLoader

        val ans = mutableListOf<MusicPlugin>()
        for (pluginName in enabledPluginClasses) {
            val inputClass = try {
                loader.loadClass(pluginName).kotlin
            } catch (e: ClassNotFoundException) {
                throw PluginClassNotFoundException(pluginName)
            }
        /*    if(inputClass is MusicPlugin){
                throw IllegalPluginException(inputClass.java)
            } */
           //todo check implepents music plugin
            val primaryConstructor = inputClass.primaryConstructor
            if (primaryConstructor == null || primaryConstructor.parameters.isEmpty()) {
                val noParamConstructor = inputClass.constructors.find { it.parameters.isEmpty() }
                    ?: throw IllegalPluginException(inputClass.java)
                val mAIproperty =
                    inputClass.memberProperties.find { it.name == "musicAppInstance" } ?: throw IllegalPluginException(
                        inputClass.java
                    )
                if (mAIproperty is KMutableProperty<*>) {
                    val newPlugin = noParamConstructor.call() as MusicPlugin
                    mAIproperty.setter.call(newPlugin, this)
                    ans.add(newPlugin)
                } else {
                    throw IllegalPluginException(inputClass.java)
                }
            } else {
                if (primaryConstructor.parameters.size != 1) {
                    throw IllegalPluginException(inputClass.java)
                }
           /*     if (primaryConstructor.parameters.single().type.jvmErasure.java is MusicApp){
                    throw IllegalPluginException(inputClass.java)
                } */
                ans.add(primaryConstructor.call(this) as MusicPlugin) //todo check parametr class + test
            }
        }

        return@lazy ans
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? {
        return plugins.singleOrNull{it::class.java.canonicalName == pluginClassName}
    }

    fun <T : MusicPlugin> getPlugins(pluginClass: Class<T>): List<T> =
        plugins.filterIsInstance(pluginClass)

    private val musicLibraryContributors: List<MusicLibraryContributorPlugin>
        get() = getPlugins(MusicLibraryContributorPlugin::class.java)

    protected val playbackListeners: List<PlaybackListenerPlugin>
        get() = getPlugins(PlaybackListenerPlugin::class.java)

    val musicLibrary: MusicLibrary by lazy {
        musicLibraryContributors
            .sortedWith(compareBy({ it.preferredOrder }, { it.pluginId }))
            .fold(MusicLibrary(mutableListOf())) { acc, it -> it.contribute(acc) }
    }

    open val player: MusicPlayer by lazy {
        JLayerMusicPlayer(
            playbackListeners
        )
    }

    fun startPlayback(playlist: Playlist, fromPosition: Int) {
        player.playbackState = PlaybackState.Playing(
            PlaylistPosition(
                playlist,
                fromPosition
            ), isResumed = false
        )
    }

    fun nextOrStop() = player.playbackState.playlistPosition?.let {
        val nextPosition = it.position + 1
        player.playbackState =
            if (nextPosition in it.playlist.tracks.indices)
                PlaybackState.Playing(
                    PlaylistPosition(
                        it.playlist,
                        nextPosition
                    ), isResumed = false
                )
            else
                PlaybackState.Stopped
    }

    @Volatile
    var isClosed = false
        private set
}