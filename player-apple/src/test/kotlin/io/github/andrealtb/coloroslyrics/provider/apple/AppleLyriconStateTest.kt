/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.proify.lyricon.lyric.model.Song
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleLyriconStateTest {
    private val sent = mutableListOf<Song?>()
    private val state = AppleLyriconState { sent.add(it) }
    private val first = TrackIdentity(id = "1", title = "First", artist = "Artist", durationMs = 4000)
    private val second = TrackIdentity(id = "2", title = "Second", artist = "Artist", durationMs = 4000)

    @Test
    fun sendsCurrentSongWithoutWaitingForColorOsSessionOrArtwork() {
        state.onTrack(first)
        assertNull(sent.single()!!.lyrics)
        assertTrue(state.onSong(song("1")))
        assertEquals("First", sent.last()!!.name)
        assertEquals("lead", sent.last()!!.lyrics!!.single().text)
    }

    @Test
    fun clearsOldLyricsImmediatelyAndRejectsLateResultsAfterSkip() {
        state.onTrack(first)
        state.onSong(song("1"))
        state.onTrack(second)
        assertEquals("2", sent.last()!!.id)
        assertNull(sent.last()!!.lyrics)
        val count = sent.size
        assertFalse(state.onSong(song("1")))
        assertEquals(count, sent.size)
        assertTrue(state.onSong(song("2")))
        assertEquals("lead", sent.last()!!.lyrics!!.single().text)
    }

    @Test
    fun repeatedMetadataAndCachedLyricsDoNotSendDuplicateSongs() {
        state.onTrack(first)
        state.onSong(song("1"))
        state.onTrack(first)
        state.onSong(song("1"))
        assertEquals(2, sent.size)
    }

    @Test
    fun titleOnlyMetadataCanAcquireIdWithoutClearingReadyLyrics() {
        state.onTrack(first.copy(id = null))
        state.onSong(song("1").copy(name = first.title, artist = first.artist, durationMs = 4000))
        state.onTrack(first)
        assertEquals("1", sent.last()!!.id)
        assertEquals("lead", sent.last()!!.lyrics!!.single().text)
        assertEquals(2, sent.size)
    }

    @Test
    fun sessionReleaseClearsLyricsAndRejectsPendingResults() {
        state.onTrack(first)
        state.onSong(song("1"))
        state.clear()
        assertNull(sent.last())
        assertFalse(state.onSong(song("1")))
    }

    @Test
    fun cachedSongCanBeRestoredWhenReturningToTrack() {
        val cached = song("1")
        state.onTrack(first)
        state.onSong(cached)
        state.onTrack(second)
        state.onTrack(first)
        assertTrue(state.onSong(cached))
        assertEquals("lead", sent.last()!!.lyrics!!.single().text)
    }

    @Test
    fun committedMediaIdRequestsLyricsWithoutColorOsGenerationOrPlaybackItem() {
        val requested = mutableListOf<String?>()
        val pipeline = AppleLyriconState(requestLyrics = { requested.add(it.id) }) { sent.add(it) }
        pipeline.onTrack(first)
        pipeline.onTrack(first)
        assertEquals(listOf<String?>("1"), requested)
        assertTrue(pipeline.isCurrentId("1"))
        pipeline.onTrack(second)
        assertFalse(pipeline.isCurrentId("1"))
        assertTrue(pipeline.isCurrentId("2"))
        assertEquals(listOf<String?>("1", "2"), requested)
        assertFalse(pipeline.onSong(song("1")))
        assertTrue(pipeline.onSong(song("2")))
    }

    @Test
    fun lyricsBuiltBeforeMetadataAreRecoveredFromCacheWhenIdCommits() {
        val cache = mapOf("1" to song("1"))
        val requested = mutableListOf<String?>()
        val pipeline = AppleLyriconState(
            loadSong = cache::get, requestLyrics = { requested.add(it.id) }
        ) { sent.add(it) }
        assertFalse(pipeline.onSong(cache.getValue("1")))
        pipeline.onTrack(first)
        assertEquals("lead", sent.last()!!.lyrics!!.single().text)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun nativeSongIdentityDoesNotDependOnDifferentColorOsMetadataTitle() {
        state.onTrack(first)
        assertTrue(state.onSong(song("1").copy(name = "queue title", artist = "queue artist")))
        assertEquals("First", sent.last()!!.name)
        assertEquals("Artist", sent.last()!!.artist)
        assertEquals("lead", sent.last()!!.lyrics!!.single().text)
    }

    private fun song(id: String) = AppleSongModel(
        adamId = id,
        lyrics = listOf(AppleLyricLineModel(begin = 1000, end = 2000, htmlLineText = "lead"))
    )
}
