/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleLyriconSongMapperTest {
    @Test
    fun translationAndBackingVocalsUseSeparateLyriconFields() {
        val song = AppleSongModel("1", lyrics = listOf(AppleLyricLineModel(
            begin = 1000, end = 2000, htmlLineText = "lead",
            htmlTranslationLineText = "译文", htmlBackgroundVocalsLineText = "harmony",
            htmlPronunciationLineText = "lead pronunciation",
            words = listOf(AppleLyricWordModel(begin = 1000, end = 2000, text = "lead")),
            backgroundWords = listOf(AppleLyricWordModel(begin = 1100, end = 1900, text = "harmony"))
        )))
        val mapped = AppleLyriconSongMapper.map(song).lyrics!!.single()
        assertEquals("译文", mapped.translation)
        assertEquals("harmony", mapped.secondary)
        assertEquals("lead pronunciation", mapped.roma)
        assertEquals("harmony", mapped.secondaryWords!!.single().text)
        assertEquals(1000L, mapped.words!!.single().begin)
    }

    @Test
    fun preservesWordSpacesAndSyllableTimingForLyricon() {
        val song = AppleSongModel("1", lyrics = listOf(AppleLyricLineModel(
            begin = 0, end = 800, htmlLineText = "Galway Girl",
            words = listOf(
                AppleLyricWordModel(begin = 0, end = 180, text = "Gal"),
                AppleLyricWordModel(begin = 180, end = 420, text = "way"),
                AppleLyricWordModel(begin = 420, end = 500, text = " ", whitespace = true),
                AppleLyricWordModel(begin = 500, end = 800, text = "Girl")
            )
        )))
        val words = AppleLyriconSongMapper.map(song).lyrics!!.single().words!!
        assertEquals("Galway Girl", words.joinToString("") { it.text.orEmpty() })
        assertEquals(listOf(0L, 180L, 420L, 500L), words.map { it.begin })
    }

    @Test
    fun preservesBackingVocalsFilteredOutOfColorOsOutput() {
        val song = AppleSongModel("1", lyrics = listOf(AppleLyricLineModel(
            begin = 0, end = 1000, htmlLineText = "oh", htmlBackgroundVocalsLineText = "oh"
        )))
        assertTrue(AppleSongMapper.toRichLines(song).isEmpty())
        assertEquals("oh", AppleLyriconSongMapper.map(song).lyrics!!.single().secondary)
    }

    @Test
    fun assignsSecondPersonToRightAndKeepsRomajiOutOfTranslation() {
        val song = AppleSongModel("1", agents = listOf(
            AppleLyricAgentModel("group", 3), AppleLyricAgentModel("left", 1),
            AppleLyricAgentModel("right", 1)
        ), lyrics = listOf(
            AppleLyricLineModel(agent = "left", htmlLineText = "夏"),
            AppleLyricLineModel(agent = "right", htmlLineText = "夏",
                htmlTranslationLineText = "natsu", htmlPronunciationLineText = "natsu")
        ))
        val lines = AppleLyriconSongMapper.map(song).lyrics!!
        assertFalse(lines[0].isAlignedRight)
        assertTrue(lines[1].isAlignedRight)
        assertNull(lines[1].translation)
        assertEquals("natsu", lines[1].roma)
    }
}
