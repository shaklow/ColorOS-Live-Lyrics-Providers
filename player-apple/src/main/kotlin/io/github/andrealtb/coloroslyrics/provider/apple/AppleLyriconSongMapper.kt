/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song

/** Lyricon has separate translation and backing-vocal lanes, unlike ColorOS lyricInfo. */
internal object AppleLyriconSongMapper {
    fun map(song: AppleSongModel): Song {
        val rightAgent = song.agents.filter { it.type == 1L }.getOrNull(1)?.id
        return Song(
            id = song.adamId,
            name = song.name,
            artist = song.artist,
            duration = song.durationMs,
            lyrics = song.lyrics.map { line ->
                RichLyricLine(
                    begin = line.begin.toLong(),
                    end = line.end.toLong(),
                    duration = line.duration.toLong(),
                    text = AppleHtmlText.clean(line.htmlLineText),
                    words = line.words.map(::word),
                    secondary = AppleHtmlText.clean(line.htmlBackgroundVocalsLineText)
                        .takeIf { it.isNotBlank() },
                    secondaryWords = line.backgroundWords.map(::word),
                    translation = AppleSongMapper.usableTranslation(line),
                    roma = AppleHtmlText.clean(line.htmlPronunciationLineText)
                        .takeIf { it.isNotBlank() },
                    isAlignedRight = rightAgent != null && line.agent == rightAgent
                )
            }
        )
    }

    private fun word(word: AppleLyricWordModel): LyricWord {
        val raw = word.text.orEmpty()
        val clean = AppleHtmlText.clean(raw)
        val text = when {
            word.whitespace || (raw.isNotEmpty() && raw.isBlank()) -> " "
            clean.isBlank() -> ""
            else -> buildString {
                if (raw.firstOrNull()?.isWhitespace() == true) append(' ')
                append(clean)
                if (raw.lastOrNull()?.isWhitespace() == true) append(' ')
            }
        }
        return LyricWord(
            begin = word.begin.toLong(), end = word.end.toLong(),
            duration = word.duration.toLong(), text = text
        )
    }
}
