/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import java.lang.reflect.Method

/** Adapts LyricProvider's Song(id, hasLyrics) request to the host's loadLyrics signature. */
internal class AppleLyricRequestFactory(
    private val loadMethods: List<Method>,
    songClass: Class<*>?
) {
    private val songConstructor = runCatching {
        songClass?.getDeclaredConstructor()?.apply { isAccessible = true }
    }.getOrNull()
    private val setId = runCatching {
        songClass?.getMethod("setId", String::class.java)?.apply { isAccessible = true }
    }.getOrNull()
    private val setHasLyrics = runCatching {
        songClass?.getMethod("setHasLyrics", Boolean::class.javaPrimitiveType)
            ?.apply { isAccessible = true }
    }.getOrNull()
    private val songLoadMethod = songClass?.let { loadMethodFor(it) }

    val canRequestById: Boolean
        get() = songConstructor != null && setId != null && setHasLyrics != null &&
            songLoadMethod != null

    fun canRequest(playbackItem: Any?, mediaId: String?): Boolean =
        (playbackItem != null && loadMethodFor(playbackItem.javaClass) != null) ||
            (canRequestById && usableMediaId(mediaId) != null)

    fun create(playbackItem: Any?, mediaId: String?): Request? {
        if (playbackItem != null) {
            loadMethodFor(playbackItem.javaClass)?.let { return Request(playbackItem, it) }
        }
        val id = usableMediaId(mediaId) ?: return null
        if (!canRequestById) return null
        val song = songConstructor!!.newInstance()
        setId!!.invoke(song, id)
        setHasLyrics!!.invoke(song, true)
        return Request(song, songLoadMethod!!)
    }

    private fun loadMethodFor(argumentClass: Class<*>): Method? {
        val compatible = loadMethods.filter { method ->
            method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(argumentClass)
        }
        return compatible.singleOrNull { it.parameterTypes[0] == argumentClass }
            ?: compatible.singleOrNull()
    }

    data class Request(val argument: Any, val method: Method)

    companion object {
        fun usableMediaId(mediaId: String?): String? =
            mediaId?.trim()?.takeIf { it.isNotEmpty() && it != "0" }
    }
}
