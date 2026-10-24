/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackIdentityPolicy
import io.github.proify.lyricon.lyric.model.Song

/** Keeps Lyricon's song independent of ColorOS session/artwork publication gates. */
internal class AppleLyriconState(
    private val loadSong: (String) -> AppleSongModel? = { null },
    private val requestLyrics: (TrackIdentity) -> Unit = {},
    private val sendSong: (Song?) -> Unit
) {
    private var currentTrack: TrackIdentity? = null
    private var nativeSong: AppleSongModel? = null
    private var lastSong: Song? = null

    @Synchronized
    fun onTrack(track: TrackIdentity): Boolean {
        if (track.isBlank) return false
        val previous = currentTrack
        val same = TrackIdentityPolicy.isSameTrack(previous, track)
        if (!same) nativeSong = null
        currentTrack = if (same && previous != null) {
            TrackIdentityPolicy.mergePreferringFilled(previous, track)
        } else track
        publish()
        val changed = !same || previous?.id != currentTrack?.id
        if (changed) {
            currentTrack?.id?.let { id ->
                loadSong(id)?.let(::onSong)
                if (!hasLyrics()) requestLyrics(currentTrack!!)
            }
        }
        return changed
    }

    @Synchronized
    fun isCurrentId(id: String): Boolean = currentTrack?.id == id

    @Synchronized
    fun hasLyrics(): Boolean = !nativeSong?.lyrics.isNullOrEmpty()

    @Synchronized
    fun onSong(song: AppleSongModel): Boolean {
        val track = currentTrack ?: return false
        val songTrack = TrackIdentity(
            id = song.adamId, title = song.name, artist = song.artist, durationMs = song.durationMs
        )
        if (!TrackIdentityPolicy.isSameTrack(track, songTrack)) return false
        nativeSong = song
        publish()
        return true
    }

    @Synchronized
    fun clear() {
        currentTrack = null
        nativeSong = null
        lastSong = null
        sendSong(null)
    }

    private fun publish() {
        val track = currentTrack ?: return
        val song = nativeSong?.let {
            AppleLyriconSongMapper.map(it.copy(
                name = track.title ?: it.name,
                artist = track.artist ?: it.artist,
                durationMs = track.durationMs.takeIf { duration -> duration > 0L } ?: it.durationMs
            ))
        } ?: Song(id = track.id, name = track.title, artist = track.artist, duration = track.durationMs)
        if (song == lastSong) return
        sendSong(song)
        lastSong = song
    }
}
