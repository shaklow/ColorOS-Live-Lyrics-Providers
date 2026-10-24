/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleLyriconStartupTest {
    @Test
    fun attachDoesNotConstructSdkAndOnCreateQueuesInitializationAfterContextIsReady() {
        val fixture = Fixture()
        fixture.startup.install()
        assertEquals(0, fixture.creates)
        assertTrue(fixture.tasks.isEmpty())
        fixture.application.onCreate()
        fixture.onCreateCompleted()
        assertEquals(0, fixture.creates)
        fixture.tick()
        assertEquals(1, fixture.creates)
        assertEquals(listOf("LYRICON_LIFECYCLE_HOOK_INSTALLED", "LYRICON_INITIALIZED"), fixture.events)
        fixture.onCreateCompleted()
        assertTrue(fixture.tasks.isEmpty())
    }

    @Test
    fun nullContextNeverReachesSdkConstructorAndCanRecoverWhenContextBecomesReady() {
        val fixture = Fixture()
        fixture.startup.install()
        fixture.onCreateCompleted()
        fixture.tick()
        assertEquals(0, fixture.creates)
        assertEquals(1, fixture.tasks.size)
        assertEquals(listOf("LYRICON_INITIALIZATION_FAILED"), fixture.failures)
        fixture.application.contextReady = true
        fixture.tick()
        assertEquals(1, fixture.creates)
        assertTrue(fixture.tasks.isEmpty())
    }

    @Test
    fun failedInitializationRetriesWithoutPermanentlyLeavingPublisherNull() {
        val fixture = Fixture(failFirstCreation = true)
        fixture.startup.install()
        fixture.application.onCreate()
        fixture.onCreateCompleted()
        fixture.tick()
        assertEquals(1, fixture.creates)
        assertEquals(1, fixture.tasks.size)
        fixture.tick()
        assertEquals(2, fixture.creates)
        assertTrue("LYRICON_INITIALIZED" in fixture.events)
        assertTrue(fixture.tasks.isEmpty())
    }

    @Test
    fun contextFailureRetriesAreBounded() {
        val fixture = Fixture()
        fixture.startup.install()
        fixture.onCreateCompleted()
        repeat(3) { fixture.tick() }
        assertEquals(0, fixture.creates)
        assertEquals(3, fixture.failures.size)
        assertTrue(fixture.tasks.isEmpty())
        fixture.onCreateCompleted()
        assertTrue(fixture.tasks.isEmpty())
    }

    @Test
    fun otherApplicationInstancesAndFailedOnCreateDoNotStartSdk() {
        val fixture = Fixture()
        fixture.startup.install()
        val method = HostApplication::class.java.getDeclaredMethod("onCreate")
        fixture.runtime.after(method, HostApplication())
        fixture.runtime.after(method, fixture.application, throwable = IllegalStateException("host failure"))
        assertEquals(0, fixture.creates)
        assertTrue(fixture.tasks.isEmpty())
    }

    @Test
    fun startupReplaysActualClockCapturedBeforeSdkCreation() {
        val fixture = Fixture()
        var sdkReady = false
        val positions = mutableListOf<Long>()
        val playback = mutableListOf<Boolean>()
        val seeks = mutableListOf<Long>()
        val clockTasks = linkedSetOf<Runnable>()
        var position = 1234L
        val clock = AppleLyriconProgress(
            { if (sdkReady) playback.add(it) }, { if (sdkReady) positions.add(it) },
            { if (sdkReady) seeks.add(it) },
            { task, _ -> clockTasks.add(task) }, { clockTasks.remove(it) }, 41L, { throw it }
        )
        // Exo construction and play can happen inside the host's onCreate.
        clock.onPlayer { position }
        clock.onPlaying(true)
        assertTrue(positions.isEmpty())
        assertTrue(playback.isEmpty())
        fixture.startup.install()
        fixture.application.onCreate()
        fixture.onCreateCompleted()
        position = 2456L
        fixture.tick()
        sdkReady = fixture.creates == 1
        clock.replay()
        assertEquals(listOf(true), playback)
        assertEquals(listOf(2456L), positions)
        assertEquals(listOf(2456L), seeks)
        assertEquals(1, clockTasks.size)
        assertFalse(fixture.tasks.any())
    }

    @Test
    fun hooksActualApplicationOverrideEvenWhenItDoesNotCallSuper() {
        val app = OverridingApplication()
        val runtime = AppleRecordingHookRuntime()
        val tasks = mutableListOf<Runnable>()
        var creates = 0
        AppleLyriconStartup(
            app, runtime, { app.contextReady }, { task, _ -> tasks.add(task) },
            { creates++ }, {}, { _, error -> throw error }
        ).install()
        app.onCreate()
        runtime.after(OverridingApplication::class.java.getDeclaredMethod("onCreate"), app)
        tasks.single().run()
        assertEquals(1, creates)
    }

    private class Fixture(failFirstCreation: Boolean = false) {
        val application = HostApplication()
        val runtime = AppleRecordingHookRuntime()
        val tasks = mutableListOf<Runnable>()
        val events = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var creates = 0
        val startup = AppleLyriconStartup(
            application = application,
            runtime = runtime,
            isContextReady = { application.contextReady },
            post = { task, _ -> tasks.add(task) },
            initialize = {
                check(application.contextReady) { "SDK registerReceiver requires non-null applicationContext" }
                creates++
                if (failFirstCreation && creates == 1) error("temporary initialization failure")
            },
            onEvent = events::add,
            onFailure = { event, _ -> failures.add(event) }
        )

        fun onCreateCompleted() {
            runtime.after(HostApplication::class.java.getDeclaredMethod("onCreate"), application)
        }

        fun tick() = tasks.removeAt(0).run()
    }

    open class HostApplication {
        var contextReady = false
        open fun onCreate() { contextReady = true }
    }

    class OverridingApplication : HostApplication() {
        override fun onCreate() { contextReady = true }
    }
}
