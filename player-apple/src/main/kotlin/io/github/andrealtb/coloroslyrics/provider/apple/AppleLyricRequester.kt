/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import android.app.Application
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method

/**
 * Owns a Provider-constructed [PlayerLyricsViewModel]. The official lyrics
 * fragment's instance may already be cleared or still bound to the previous
 * song; hitchhiking it makes skip-time `loadLyrics` a no-op until a later retry.
 * Uses a matching PlaybackItem when available, or constructs LyricProvider's
 * Song with the current MediaSession ID when the host supports that argument.
 */
class AppleLyricRequester(
    private val classLoader: ClassLoader,
    private val application: Application,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val onFailure: (Throwable) -> Unit = {}
) {
    @Volatile
    private var playerLyricsViewModel: Any? = null

    @Volatile
    private var requestFactory: AppleLyricRequestFactory? = null

    val canRequestById: Boolean
        get() = requestFactory?.canRequestById == true

    fun setLoadLyricsMethods(methods: List<Method>) {
        methods.forEach { it.isAccessible = true }
        val songClass = runCatching { classLoader.loadClass(ApplePlayerConstants.SONG) }.getOrNull()
        requestFactory = AppleLyricRequestFactory(methods, songClass)
    }

    fun requestDownload(
        playbackItem: Any?,
        mediaId: String?,
        isCurrent: () -> Boolean
    ): Boolean = runCatching {
        val factory = requestFactory ?: return false
        if (!factory.canRequest(playbackItem, mediaId)) return false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!isCurrent()) return false
            callLoadLyrics(factory, playbackItem, mediaId)
            true
        } else {
            mainHandler.post {
                runCatching {
                    if (isCurrent()) callLoadLyrics(factory, playbackItem, mediaId)
                }.onFailure(onFailure)
            }
        }
    }.onFailure(onFailure).getOrDefault(false)

    private fun callLoadLyrics(factory: AppleLyricRequestFactory, playbackItem: Any?, mediaId: String?) {
        val request = checkNotNull(factory.create(playbackItem, mediaId))
        val viewModel = ensurePlayerLyricsViewModel()
        request.method.invoke(viewModel, request.argument)
    }

    private fun ensurePlayerLyricsViewModel(): Any {
        playerLyricsViewModel?.let { return it }
        return classLoader
            .loadClass(ApplePlayerConstants.PLAYER_LYRICS_VIEW_MODEL)
            .getConstructor(Application::class.java)
            .newInstance(application)
            .also { playerLyricsViewModel = it }
    }
}
