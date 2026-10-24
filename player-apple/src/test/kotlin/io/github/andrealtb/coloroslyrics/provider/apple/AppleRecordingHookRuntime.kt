/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderAfterHook
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookRuntime
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookSpec
import java.lang.reflect.Executable

/** Supplies host callbacks; the shared API 102 module separately tests framework dispatch. */
internal class AppleRecordingHookRuntime : ProviderHookRuntime {
    private val specs = linkedMapOf<Executable, ProviderHookSpec>()
    override val installedHookCount: Int get() = specs.size
    override fun hook(executable: Executable, id: String, spec: ProviderHookSpec.() -> Unit): Boolean {
        val constructor = ProviderHookSpec::class.java.getDeclaredConstructor().apply { isAccessible = true }
        specs[executable] = constructor.newInstance().apply(spec)
        return true
    }

    fun after(
        executable: Executable,
        receiver: Any,
        args: Array<Any?> = emptyArray(),
        throwable: Throwable? = null
    ) {
        val spec = specs.getValue(executable)
        val field = ProviderHookSpec::class.java.getDeclaredField("afterCallback").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val callback = field.get(spec) as (ProviderAfterHook.() -> Unit)
        val constructor = ProviderAfterHook::class.java.declaredConstructors.single().apply { isAccessible = true }
        callback(constructor.newInstance(executable, receiver, args, throwable, null) as ProviderAfterHook)
    }
}
