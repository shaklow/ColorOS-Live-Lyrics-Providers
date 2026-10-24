/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

/** LyricProvider's manual player clock. Does not depend on ColorOS session selection. */
internal class AppleLyriconProgress(
    private val sendPlaying: (Boolean) -> Unit,
    private val sendPosition: (Long) -> Unit,
    private val sendSeek: (Long) -> Unit,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val intervalMs: Long,
    private val onFailure: (Throwable) -> Unit
) {
    private var readPosition: (() -> Long)? = null
    private var playing = false
    private var screenOn = true
    private var scheduled = false
    private var lastPosition: Long? = null

    private val tick = object : Runnable {
        override fun run() = synchronized(this@AppleLyriconProgress) {
            scheduled = false
            if (!playing || !screenOn) return@synchronized
            updatePosition()
            scheduleNext()
        }
    }

    @Synchronized
    fun onPlayer(reader: () -> Long) {
        readPosition = reader
        if (playing && screenOn) updatePosition()
    }

    @Synchronized
    fun onPlaying(value: Boolean) {
        val changed = playing != value
        playing = value
        // Also establish the manual SDK mode on the first paused/stopped callback.
        sendPlaying(value)
        if (value && screenOn) {
            if (changed) updatePosition()
            scheduleNext()
        } else {
            cancel(tick)
            scheduled = false
            if (!value) updatePosition()
        }
    }

    @Synchronized
    fun onSeek(position: Long) {
        // A paused seek must update the cached clock too, before playback resumes.
        lastPosition = position.coerceAtLeast(0L)
        sendSeek(lastPosition!!)
    }

    /** Host events can precede SDK creation; restore their current clock after startup. */
    @Synchronized
    fun replay() {
        sendPlaying(playing)
        updatePosition()
        lastPosition?.let(sendSeek)
        if (playing && screenOn) scheduleNext()
    }

    @Synchronized
    fun onScreen(value: Boolean) {
        screenOn = value
        if (value && playing) {
            updatePosition()
            scheduleNext()
        } else {
            cancel(tick)
            scheduled = false
        }
    }

    private fun updatePosition() {
        val reader = readPosition ?: return
        runCatching {
            val position = reader().coerceAtLeast(0L)
            lastPosition = position
            sendPosition(position)
        }.onFailure(onFailure)
    }

    private fun scheduleNext() {
        if (scheduled) return
        scheduled = true
        schedule(tick, intervalMs)
    }
}
