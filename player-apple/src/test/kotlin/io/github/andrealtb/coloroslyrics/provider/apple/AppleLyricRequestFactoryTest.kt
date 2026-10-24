/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppleLyricRequestFactoryTest {
    @Test
    fun constructsSongByIdWithoutWaitingForPlaybackItem() {
        val factory = factory(SongViewModel::class.java, Song::class.java)
        assertTrue(factory.canRequestById)
        assertTrue(factory.canRequest(null, "1440935467"))
        val request = factory.create(null, " 1440935467 ")!!
        val viewModel = SongViewModel()
        request.method.invoke(viewModel, request.argument)
        assertEquals("1440935467", viewModel.loaded?.id)
        assertTrue(viewModel.loaded?.hasLyrics == true)
    }

    @Test
    fun existingPlaybackItemTakesPrecedenceOverConstructedSong() {
        val factory = factory(PlaybackViewModel::class.java, Song::class.java)
        val playbackItem = PlaybackItem()
        assertSame(playbackItem, factory.create(playbackItem, "1440935467")!!.argument)
    }

    @Test
    fun songCanUseLegacyLoadLyricsAcceptingItsBaseClass() {
        val factory = factory(PlaybackViewModel::class.java, Song::class.java)
        val request = factory.create(null, "1440935467")!!
        val viewModel = PlaybackViewModel()
        request.method.invoke(viewModel, request.argument)
        assertEquals("1440935467", (viewModel.loaded as Song).id)
    }

    @Test
    fun selectsTheMatchingLoadLyricsOverload() {
        val factory = factory(OverloadedViewModel::class.java, Song::class.java)
        assertEquals(Song::class.java, factory.create(null, "1")!!.method.parameterTypes[0])
        assertEquals(
            PlaybackItem::class.java,
            factory.create(PlaybackItem(), "1")!!.method.parameterTypes[0]
        )
    }

    @Test
    fun missingSongSupportKeepsPlaybackItemRequestsWorking() {
        val factory = factory(PlaybackViewModel::class.java, null)
        assertFalse(factory.canRequestById)
        assertFalse(factory.canRequest(null, "1"))
        assertNull(factory.create(null, "1"))
        val playbackItem = PlaybackItem()
        assertSame(playbackItem, factory.create(playbackItem, null)!!.argument)
    }

    @Test
    fun incompatibleSongAndInvalidIdsCannotCreateARequest() {
        val factory = factory(SongViewModel::class.java, Song::class.java)
        listOf(null, "", " ", "0").forEach { id ->
            assertFalse(factory.canRequest(null, id))
            assertNull(factory.create(null, id))
        }
        assertFalse(factory(PlaybackViewModel::class.java, IncompatibleSong::class.java).canRequestById)
        assertFalse(factory(PlaybackViewModel::class.java, SongWithoutSetters::class.java).canRequestById)
    }

    private fun factory(viewModel: Class<*>, song: Class<*>?): AppleLyricRequestFactory =
        AppleLyricRequestFactory(viewModel.declaredMethods.filter { it.name == "loadLyrics" }, song)

    open class PlaybackItem

    class Song : PlaybackItem() {
        var id: String? = null
        var hasLyrics: Boolean = false
    }

    class IncompatibleSong
    class SongWithoutSetters : PlaybackItem()

    class SongViewModel {
        var loaded: Song? = null
        fun loadLyrics(song: Song) { loaded = song }
    }

    class PlaybackViewModel {
        var loaded: PlaybackItem? = null
        fun loadLyrics(item: PlaybackItem) { loaded = item }
    }

    @Suppress("UNUSED_PARAMETER")
    class OverloadedViewModel {
        fun loadLyrics(item: PlaybackItem) {}
        fun loadLyrics(song: Song) {}
    }
}
