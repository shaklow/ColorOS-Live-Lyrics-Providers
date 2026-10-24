/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleLyriconProgressTest {
    private val playing = mutableListOf<Boolean>()
    private val positions = mutableListOf<Long>()
    private val seeks = mutableListOf<Long>()
    private val failures = mutableListOf<Throwable>()
    private val scheduled = linkedSetOf<Runnable>()
    private var position = 1300L
    private val clock = AppleLyriconProgress(
        sendPlaying = playing::add,
        sendPosition = positions::add,
        sendSeek = seeks::add,
        schedule = { task, delay -> assertEquals(41L, delay); scheduled.add(task) },
        cancel = { scheduled.remove(it) },
        intervalMs = 41L,
        onFailure = failures::add
    )

    @Test
    fun playingUsesActualPlayerClockWithoutMediaSessionOrArtwork() {
        clock.onPlayer { position }
        clock.onPlaying(true)
        assertEquals(listOf(true), playing)
        assertEquals(listOf(1300L), positions)
        position = 5700L
        tick()
        assertEquals(listOf(1300L, 5700L), positions)
        assertEquals(1, scheduled.size)
    }

    @Test
    fun duplicatePlayEventsDoNotCreateMultipleLoopsAndPauseCancelsPendingTick() {
        clock.onPlayer { position }
        repeat(3) { clock.onPlaying(true) }
        assertEquals(1, scheduled.size)
        clock.onPlaying(false)
        assertEquals(false, playing.last())
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun seekUpdatesProviderWhilePlayingAndWhilePaused() {
        clock.onPlaying(true)
        clock.onSeek(4500)
        clock.onPlaying(false)
        clock.onSeek(7000)
        assertEquals(listOf(4500L, 7000L), seeks)
    }

    @Test
    fun screenOffSuspendsPollingAndUnlockResumesAtActualPosition() {
        clock.onPlayer { position }
        clock.onPlaying(true)
        clock.onScreen(false)
        assertTrue(scheduled.isEmpty())
        assertEquals(listOf(true), playing)
        position = 8000L
        clock.onScreen(true)
        assertEquals(8000L, positions.last())
        assertEquals(1, scheduled.size)
        clock.onPlaying(false)
        clock.onScreen(true)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun playerReplacementUsesNewClockAndReadFailureDoesNotKillLoop() {
        clock.onPlayer { error("released player") }
        clock.onPlaying(true)
        assertFalse(failures.isEmpty())
        assertEquals(1, scheduled.size)
        clock.onPlayer { 1900L }
        tick()
        assertEquals(1900L, positions.last())
        assertEquals(1, scheduled.size)
    }

    @Test
    fun doesNotPublishZeroWhileWaitingForPlayerConstruction() {
        clock.onPlaying(true)
        tick()
        assertTrue(positions.isEmpty())
        clock.onPlayer { 2300L }
        assertEquals(listOf(2300L), positions)
    }

    private fun tick() {
        val task = scheduled.single()
        scheduled.remove(task)
        task.run()
    }
}
