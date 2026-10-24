/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

object AppleSongParser {
    fun parse(songNative: Any): AppleSongModel? {
        val adamId = AppleNativeCalls.call(songNative, "getAdamId")?.toString()
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?: return null
        val duration = AppleNativeCalls.callInt(songNative, "getDuration")
        val sections = AppleNativeCalls.call(songNative, "getSections")
        val lyrics = if (sections != null) parseSectionVector(sections) else emptyList()
        return AppleSongModel(
            adamId = adamId,
            durationMs = duration.toLong(),
            lyrics = lyrics,
            agents = AppleNativeCalls.call(songNative, "getAgents")?.let(::parseAgentVector).orEmpty()
        )
    }

    fun applySystemTranslation(songNative: Any, language: String?): Boolean {
        if (language.isNullOrBlank()) return false
        return AppleNativeCalls.callBoolean(songNative, "setTranslation", language) == true
    }

    private fun parseAgentVector(vector: Any): List<AppleLyricAgentModel> =
        AppleNativeCalls.parseVector(vector) { native ->
            AppleLyricAgentModel(
                id = AppleNativeCalls.callString(native, "getId"),
                type = AppleNativeCalls.callLong(native, "getType_"),
                nameTypes = (AppleNativeCalls.call(native, "getNameTypes_") as? IntArray)
                    ?.toList().orEmpty()
            )
        }

    private fun parseSectionVector(vector: Any): List<AppleLyricLineModel> =
        AppleNativeCalls.parseVector(vector) { sectionNative ->
            AppleNativeCalls.call(sectionNative, "getLines")?.let { lineVector ->
                AppleNativeCalls.parseVector(lineVector, ::parseLine)
            }
        }.flatten()

    private fun parseLine(lineNative: Any): AppleLyricLineModel {
        val words = AppleNativeCalls.call(lineNative, "getWords")?.let(::parseWordVector).orEmpty()
        val backgroundWords = parseBackgroundWords(lineNative)
        return AppleLyricLineModel(
            begin = AppleNativeCalls.callInt(lineNative, "getBegin"),
            end = AppleNativeCalls.callInt(lineNative, "getEnd"),
            duration = AppleNativeCalls.callInt(lineNative, "getDuration"),
            htmlLineText = AppleNativeCalls.callString(lineNative, "getHtmlLineText"),
            htmlTranslationLineText =
                AppleNativeCalls.callString(lineNative, "getHtmlTranslationLineText"),
            htmlBackgroundVocalsLineText =
                AppleNativeCalls.callString(lineNative, "getHtmlBackgroundVocalsLineText"),
            htmlPronunciationLineText =
                AppleNativeCalls.callString(lineNative, "getHtmlPronunciationLineText"),
            htmlTranslatedBackgroundVocalsLineText =
                AppleNativeCalls.callString(lineNative, "getHtmlTranslatedBackgroundVocalsLineText"),
            htmlPronunciationBackgroundVocalsLineText =
                AppleNativeCalls.callString(lineNative, "getHtmlPronunciationBackgroundVocalsLineText"),
            agent = AppleNativeCalls.callString(lineNative, "getAgent"),
            words = words,
            backgroundWords = backgroundWords
        )
    }

    private fun parseBackgroundWords(lineNative: Any): List<AppleLyricWordModel> {
        val withFlag = AppleNativeCalls.call(lineNative, "getBackgroundWords", false)
        val vector = withFlag ?: AppleNativeCalls.call(lineNative, "getBackgroundWords")
        return vector?.let(::parseWordVector).orEmpty()
    }

    private fun parseWordVector(vector: Any): List<AppleLyricWordModel> =
        AppleNativeCalls.parseVector(vector) { wordNative ->
            AppleLyricWordModel(
                begin = AppleNativeCalls.callInt(wordNative, "getBegin"),
                end = AppleNativeCalls.callInt(wordNative, "getEnd"),
                duration = AppleNativeCalls.callInt(wordNative, "getDuration"),
                text = AppleNativeCalls.callString(wordNative, "getHtmlLineText"),
                whitespace = AppleNativeCalls.callBoolean(wordNative, "isWhitespace") == true,
                agent = AppleNativeCalls.callString(wordNative, "getAgent")
            )
        }
}
