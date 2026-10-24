/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookRuntime

/** Application.attach is too early for the SDK's context.applicationContext receiver. */
internal class AppleLyriconStartup(
    private val application: Any,
    private val runtime: ProviderHookRuntime,
    private val isContextReady: () -> Boolean,
    private val post: (Runnable, Long) -> Unit,
    private val initialize: () -> Unit,
    private val onEvent: (String) -> Unit,
    private val onFailure: (String, Throwable) -> Unit
) {
    private var initialized = false
    private var pending = false
    private var attempts = 0

    fun install() {
        runCatching {
            // Hook the host's actual override, not only Application.onCreate (a subclass
            // need not call super). Context is ready after Instrumentation creates the app.
            val method = generateSequence<Class<*>>(application.javaClass) { it.superclass }
                .mapNotNull { runCatching { it.getDeclaredMethod("onCreate") }.getOrNull() }
                .first()
            check(runtime.hook(method, "apple.lyricon.Application#onCreate") {
                after {
                    if (instanceOrNull === application && throwable == null) schedule(0L)
                }
            }) { "Application.onCreate hook rejected" }
            onEvent("LYRICON_LIFECYCLE_HOOK_INSTALLED")
        }.onFailure {
            onFailure("LYRICON_LIFECYCLE_HOOK_FAILED", it)
            // Main queue runs after the current application creation stack unwinds.
            // The same readiness guard still prevents SDK access to a null context.
            schedule(0L)
        }
    }

    @Synchronized
    private fun schedule(delay: Long) {
        if (initialized || pending || attempts >= MAX_ATTEMPTS) return
        pending = true
        post(Runnable { attempt() }, delay)
    }

    @Synchronized
    private fun attempt() {
        pending = false
        if (initialized || attempts >= MAX_ATTEMPTS) return
        attempts++
        runCatching {
            check(isContextReady()) { "Host applicationContext is not ready after onCreate" }
            initialize()
        }.onSuccess {
            initialized = true
            onEvent("LYRICON_INITIALIZED")
        }.onFailure {
            onFailure("LYRICON_INITIALIZATION_FAILED", it)
            schedule(100L)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
