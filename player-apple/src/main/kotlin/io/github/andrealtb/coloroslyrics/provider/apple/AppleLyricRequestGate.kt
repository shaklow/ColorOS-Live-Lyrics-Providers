/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity

/**
 * loadLyrics uses a matching PlaybackItem, or a Song constructed with the
 * authoritative MediaSession ID when supported by the host. Title-only
 * metadata still waits for a cached item matching by title/artist.
 *
 * Cold skips (`lyrics-log-20260828-002416.txt`) used 1.5s/4s retries and no
 * PlaybackItem poll, so lockscreen stayed empty until the second or third
 * attempt. Bounded 400ms/1.2s retries plus 200ms PlaybackItem polls recover
 * a missed mapper without waiting for the bitmap.
 */
object AppleLyricRequestGate {
    const val MAX_ATTEMPTS = 3
    const val MAX_PLAYBACK_ITEM_POLLS = 8
    const val PLAYBACK_ITEM_POLL_DELAY_MS = 200L

    fun shouldStartRequest(
        track: TrackIdentity,
        generation: Long,
        lastRequestGeneration: Long?,
        hasPlaybackItem: Boolean,
        canRequestById: Boolean = false
    ): Boolean {
        if (!AppleTrackBindPolicy.hasRequestableIdentity(track)) return false
        if (!hasPlaybackItem &&
            !(canRequestById && AppleLyricRequestFactory.usableMediaId(track.id) != null)
        ) return false
        if (generation <= 0L) return false
        return lastRequestGeneration != generation
    }

    fun retryDelayMs(attempt: Int): Long = if (attempt <= 1) 400L else 1_200L

    fun shouldRetry(attempt: Int, hasLyrics: Boolean): Boolean =
        !hasLyrics && attempt in 1 until MAX_ATTEMPTS

    fun shouldPollForPlaybackItem(pollsSoFar: Int): Boolean =
        pollsSoFar in 0 until MAX_PLAYBACK_ITEM_POLLS
}
