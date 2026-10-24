/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import kotlinx.serialization.Serializable

@Serializable
data class AppleSongModel(
    val adamId: String,
    val name: String? = null,
    val artist: String? = null,
    val durationMs: Long = 0L,
    val lyrics: List<AppleLyricLineModel> = emptyList(),
    val agents: List<AppleLyricAgentModel> = emptyList()
)

@Serializable
data class AppleLyricAgentModel(
    val id: String? = null,
    val type: Long = 0L,
    val nameTypes: List<Int> = emptyList()
)

@Serializable
data class AppleLyricLineModel(
    val begin: Int = 0,
    val end: Int = 0,
    val duration: Int = 0,
    val htmlLineText: String? = null,
    val htmlTranslationLineText: String? = null,
    val htmlBackgroundVocalsLineText: String? = null,
    val htmlPronunciationLineText: String? = null,
    val words: List<AppleLyricWordModel> = emptyList(),
    val backgroundWords: List<AppleLyricWordModel> = emptyList(),
    val agent: String? = null,
    val htmlTranslatedBackgroundVocalsLineText: String? = null,
    val htmlPronunciationBackgroundVocalsLineText: String? = null
)

@Serializable
data class AppleLyricWordModel(
    val begin: Int = 0,
    val end: Int = 0,
    val duration: Int = 0,
    val text: String? = null,
    val whitespace: Boolean = false,
    val agent: String? = null
)
