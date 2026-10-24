/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.proify.lyricon.provider.ConnectionListener
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderLogo

/** Registers in the host process; the SDK caches state and replays it after reconnection. */
internal class AppleLyriconPublisher(
    private val context: Context,
    loadSong: (String) -> AppleSongModel?,
    requestLyrics: (TrackIdentity) -> Unit,
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
    private val state = AppleLyriconState(loadSong, requestLyrics) {
        provider.player.setSong(it)
        onEvent(if (it?.lyrics.isNullOrEmpty()) "LYRICON_PLACEHOLDER_SENT" else "LYRICON_LYRICS_SENT")
    }
    val progress = AppleLyriconProgress(
        sendPlaying = { playing -> safely {
            ensureRegistered()
            provider.player.setPlaybackState(playing)
        } },
        sendPosition = { safely { provider.player.setPosition(it) } },
        sendSeek = { safely { provider.player.seekTo(it) } },
        schedule = { task, delay -> handler.postDelayed(task, delay) },
        cancel = { handler.removeCallbacks(it) },
        intervalMs = ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL,
        onFailure = onFailure
    )
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
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                progress.onScreen(intent.action != Intent.ACTION_SCREEN_OFF)
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        progress.onScreen(context.getSystemService(PowerManager::class.java)?.isInteractive != false)
        ensureRegistered()
    }

    fun onTrack(track: TrackIdentity) = safely {
        ensureRegistered()
        syncTranslation()
        if (state.onTrack(track)) onEvent("LYRICON_METADATA_BOUND")
    }

    fun onSong(song: AppleSongModel) = safely {
        ensureRegistered()
        val accepted = state.onSong(song)
        onEvent(when {
            !accepted -> "LYRICON_SONG_IGNORED_ID_MISMATCH"
            song.lyrics.isEmpty() -> "LYRICON_SONG_EMPTY"
            else -> "LYRICON_SONG_READY"
        })
    }

    fun isCurrentId(id: String): Boolean = state.isCurrentId(id)

    fun onTranslation(selected: Boolean) = safely { provider.player.setDisplayTranslation(selected) }

    fun clear() = safely {
        state.clear()
        progress.onPlaying(false)
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
