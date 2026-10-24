/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import android.content.Context
import android.content.SharedPreferences
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.proify.lyricon.provider.ConnectionListener
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

/** Registers in the host process; the SDK caches state and replays it after reconnection. */
internal class AppleLyriconPublisher(
    private val context: Context,
    private val onEvent: (String) -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val provider = LyriconFactory.createProvider(
        context = context,
        providerPackageName = ApplePlayerConstants.MODULE_PACKAGE,
        playerPackageName = ApplePlayerConstants.HOST_PACKAGE,
        logo = runCatching {
            ProviderLogo.fromDrawable(context.applicationInfo.loadIcon(context.packageManager), 48, 48)
        }.getOrNull()
    )
    private val state = AppleLyriconState { provider.player.setSong(it) }
    private var registrationTime = Long.MIN_VALUE
    private var retries = 0
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TRANSLATION_KEY) safely { syncTranslation() }
    }

    fun initialize() {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        provider.service.addConnectionListener(object : ConnectionListener {
            override fun onConnected(provider: LyriconProvider) {
                retries = 0
                onEvent("LYRICON_CONNECTED")
            }

            override fun onReconnected(provider: LyriconProvider) {
                retries = 0
                onEvent("LYRICON_RECONNECTED")
            }

            override fun onDisconnected(provider: LyriconProvider) {
                onEvent("LYRICON_DISCONNECTED")
            }

            override fun onConnectTimeout(provider: LyriconProvider) {
                onEvent("LYRICON_CONNECT_TIMEOUT")
                if (retries < 3) {
                    retries++
                    handler.postDelayed({ safely { ensureRegistered() } }, 5_000L)
                }
            }
        })
        syncTranslation()
        ensureRegistered()
    }

    fun onTrack(track: TrackIdentity) = safely {
        ensureRegistered()
        syncTranslation()
        state.onTrack(track)
    }

    fun onSong(song: AppleSongModel) = safely {
        ensureRegistered()
        if (state.onSong(song)) onEvent("LYRICON_SONG_READY")
    }

    fun onPlaybackState(playbackState: PlaybackState?) = safely {
        ensureRegistered()
        // SDK 0.1.70 extrapolates position and preserves seek/speed/pause timestamps itself.
        provider.player.setPlaybackState(playbackState)
    }

    fun clear() = safely {
        state.clear()
        provider.player.setPlaybackState(null as PlaybackState?)
    }

    @Synchronized
    private fun ensureRegistered() {
        if (provider.player.isActive) return
        val now = SystemClock.elapsedRealtime()
        if (registrationTime != Long.MIN_VALUE && now - registrationTime < 5_000L) return
        registrationTime = now
        if (provider.register()) onEvent("LYRICON_REGISTER_REQUESTED")
    }

    private fun syncTranslation() {
        provider.player.setDisplayTranslation(preferences.getBoolean(TRANSLATION_KEY, false))
    }

    private inline fun safely(block: () -> Unit) {
        runCatching(block).onFailure(onFailure)
    }

    private companion object {
        const val TRANSLATION_KEY = "key_player_lyrics_translation_selected"
    }
}
