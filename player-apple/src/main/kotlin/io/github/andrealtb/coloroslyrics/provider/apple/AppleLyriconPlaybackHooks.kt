/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookRuntime
import java.lang.reflect.Modifier

/** The same host events as LyricProvider/apple-music/Apple.kt, using API 102. */
internal class AppleLyriconPlaybackHooks(
    private val runtime: ProviderHookRuntime,
    private val classLoader: ClassLoader,
    private val progress: AppleLyriconProgress,
    private val onTranslation: (Boolean) -> Unit,
    private val onEvent: (String) -> Unit,
    private val onFailure: (String, Throwable) -> Unit
) {
    fun install() {
        install("LYRICON_PLAYER_HOOK") {
            val type = classLoader.loadClass("com.apple.android.music.playback.player.ExoMediaPlayer")
            val position = generateSequence(type) { it.superclass }
                .mapNotNull { runCatching { it.getDeclaredMethod("getCurrentPosition") }.getOrNull() }
                .first().apply { isAccessible = true }
            type.declaredConstructors.forEachIndexed { index, constructor ->
                runtime.hook(constructor, "apple.lyricon.ExoMediaPlayer#constructor$index") {
                    after {
                        if (throwable != null) return@after
                        val player = instanceOrNull ?: return@after
                        progress.onPlayer { (position.invoke(player) as Number).toLong() }
                        onEvent("LYRICON_PLAYER_READY")
                    }
                }
            }
            val seek = type.getDeclaredMethod("seekToPosition", Long::class.javaPrimitiveType)
            runtime.hook(seek, "apple.lyricon.ExoMediaPlayer#seekToPosition") {
                after {
                    if (throwable == null) (args.getOrNull(0) as? Number)?.let {
                        progress.onSeek(it.toLong())
                    }
                }
            }
        }
        // A missing optional hook must not prevent the other host hooks from installing.
        install("LYRICON_PLAYBACK_HOOK") {
            val type = classLoader.loadClass(
                "com.apple.android.music.playback.controller.LocalMediaPlayerController"
            )
            val methods = type.declaredMethods.filter {
                it.name == "onPlaybackStateChanged" && it.parameterCount == 3 &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    !Modifier.isStatic(it.modifiers)
            }
            check(methods.isNotEmpty()) { "LocalMediaPlayerController.onPlaybackStateChanged missing" }
            methods.forEachIndexed { index, method ->
                runtime.hook(method, "apple.lyricon.LocalMediaPlayerController#onPlaybackStateChanged$index") {
                    after {
                        if (throwable != null) return@after
                        // Apple enum: 0 stopped, 1 playing, 2 paused (not Android's enum).
                        val playing = args.getOrNull(2) == 1
                        progress.onPlaying(playing)
                        onEvent(if (playing) "LYRICON_PLAYING" else "LYRICON_PAUSED")
                    }
                }
            }
        }
        install("LYRICON_TRANSLATION_HOOK") {
            val method = classLoader.loadClass("com.apple.android.music.utils.AppSharedPreferences")
                .getDeclaredMethod("setLyricsTranslationSelected", Boolean::class.javaPrimitiveType)
            runtime.hook(method, "apple.lyricon.AppSharedPreferences#setLyricsTranslationSelected") {
                after {
                    if (throwable == null) (args.getOrNull(0) as? Boolean)?.let(onTranslation)
                }
            }
        }
    }

    private inline fun install(event: String, block: () -> Unit) {
        runCatching(block).onSuccess { onEvent("${event}_INSTALLED") }
            .onFailure { onFailure("${event}_FAILED", it) }
    }
}
