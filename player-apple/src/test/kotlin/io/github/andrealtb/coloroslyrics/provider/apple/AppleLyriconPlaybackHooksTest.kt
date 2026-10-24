/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderAfterHook
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookRuntime
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookSpec
import java.lang.reflect.Executable
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleLyriconPlaybackHooksTest {
    @Test
    fun officialHostEventsReachManualSdkClockThroughInstalledHooks() {
        val fixture = Fixture()
        fixture.install()
        val player = ExoPlayer()
        fixture.runtime.after(ExoPlayer::class.java.declaredConstructors.single(), player)
        val state = Controller::class.java.getDeclaredMethod(
            "onPlaybackStateChanged", Any::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        fixture.runtime.after(state, Controller(), arrayOf(Any(), 3, 1))
        assertEquals(listOf(true), fixture.playing)
        assertEquals(listOf(1250L), fixture.positions)
        player.position = 6500L
        fixture.tick()
        assertEquals(6500L, fixture.positions.last())
        val seek = ExoPlayer::class.java.getDeclaredMethod("seekToPosition", Long::class.javaPrimitiveType)
        player.seekToPosition(8000L)
        fixture.runtime.after(seek, player, arrayOf(8000L))
        assertEquals(listOf(8000L), fixture.seeks)
        fixture.runtime.after(state, Controller(), arrayOf(Any(), 1, 2))
        assertEquals(false, fixture.playing.last())
        assertTrue(fixture.tasks.isEmpty())
        assertTrue(fixture.failures.isEmpty())
    }

    @Test
    fun missingTranslationHookDoesNotDisablePlayerAndPlaybackHooks() {
        val fixture = Fixture(translationAvailable = false)
        fixture.install()
        assertEquals(listOf("LYRICON_TRANSLATION_HOOK_FAILED"), fixture.failures)
        assertTrue("LYRICON_PLAYER_HOOK_INSTALLED" in fixture.events)
        assertTrue("LYRICON_PLAYBACK_HOOK_INSTALLED" in fixture.events)
        assertEquals(3, fixture.runtime.installedHookCount)
    }

    private class Fixture(private val translationAvailable: Boolean = true) {
        val runtime = RecordingRuntime()
        val playing = mutableListOf<Boolean>()
        val positions = mutableListOf<Long>()
        val seeks = mutableListOf<Long>()
        val tasks = linkedSetOf<Runnable>()
        val events = mutableListOf<String>()
        val failures = mutableListOf<String>()
        private val clock = AppleLyriconProgress(
            playing::add, positions::add, seeks::add,
            { task, _ -> tasks.add(task) }, { tasks.remove(it) }, 41L, { throw it }
        )

        fun install() {
            val loader = object : ClassLoader(ExoPlayer::class.java.classLoader) {
                override fun loadClass(name: String): Class<*> = when (name) {
                    "com.apple.android.music.playback.player.ExoMediaPlayer" -> ExoPlayer::class.java
                    "com.apple.android.music.playback.controller.LocalMediaPlayerController" -> Controller::class.java
                    "com.apple.android.music.utils.AppSharedPreferences" -> {
                        if (!translationAvailable) throw ClassNotFoundException(name)
                        Preferences::class.java
                    }
                    else -> super.loadClass(name)
                }
            }
            AppleLyriconPlaybackHooks(
                runtime, loader, clock, {}, events::add, { event, _ -> failures.add(event) }
            ).install()
        }

        fun tick() {
            val task = tasks.single()
            tasks.remove(task)
            task.run()
        }
    }

    /** Supplies host callbacks; the shared API 102 module separately tests framework dispatch. */
    private class RecordingRuntime : ProviderHookRuntime {
        private val specs = linkedMapOf<Executable, ProviderHookSpec>()
        override val installedHookCount: Int get() = specs.size
        override fun hook(executable: Executable, id: String, spec: ProviderHookSpec.() -> Unit): Boolean {
            val constructor = ProviderHookSpec::class.java.getDeclaredConstructor().apply { isAccessible = true }
            specs[executable] = constructor.newInstance().apply(spec)
            return true
        }

        fun after(executable: Executable, receiver: Any, args: Array<Any?> = emptyArray()) {
            val spec = specs.getValue(executable)
            val field = ProviderHookSpec::class.java.getDeclaredField("afterCallback").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val callback = field.get(spec) as (ProviderAfterHook.() -> Unit)
            val constructor = ProviderAfterHook::class.java.declaredConstructors.single().apply { isAccessible = true }
            callback(constructor.newInstance(executable, receiver, args, null, null) as ProviderAfterHook)
        }
    }

    class ExoPlayer {
        var position = 1250L
        fun getCurrentPosition(): Long = position
        fun seekToPosition(value: Long) { position = value }
    }

    @Suppress("UNUSED_PARAMETER")
    class Controller {
        fun onPlaybackStateChanged(player: Any, oldState: Int, newState: Int) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    class Preferences {
        fun setLyricsTranslationSelected(value: Boolean) = Unit
    }
}
