/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppleNativeCallsTest {
    @Test
    fun resolvesOverloadsByArgumentTypeIncludingPrimitiveTypes() {
        val native = OverloadedNative()
        assertEquals("int:2", AppleNativeCalls.call(native, "get", 2))
        assertEquals("long:2", AppleNativeCalls.call(native, "get", 2L))
        assertEquals("string:2", AppleNativeCalls.call(native, "get", "2"))
        assertEquals("flag:false", AppleNativeCalls.call(native, "get", false))
        assertNull(AppleNativeCalls.call(native, "get", Any()))
    }

    @Test
    fun walksIntIndexedVectorsAndSkipsMissingPointers() {
        val vector = IntVector(listOf(Ptr("first"), null, Ptr("last")))
        assertEquals(listOf("first", "last"), AppleNativeCalls.parseVector(vector) { it as? String })
    }

    @Test
    fun findsInheritedMethodsAndSafelyIgnoresMissingMethods() {
        val native = InheritedNative()
        assertEquals("inherited", AppleNativeCalls.call(native, "getText"))
        assertNull(AppleNativeCalls.call(native, "unavailable"))
    }

    private class OverloadedNative {
        fun get(index: Int): String = "int:$index"
        fun get(index: Long): String = "long:$index"
        fun get(index: String): String = "string:$index"
        fun get(flag: Boolean): String = "flag:$flag"
    }

    private class IntVector(private val values: List<Ptr?>) {
        fun size(): Int = values.size
        fun get(index: Int): Ptr? = values[index]
    }

    private class Ptr(private val value: String) {
        fun get(): String = value
    }

    private open class BaseNative {
        fun getText(): String = "inherited"
    }

    private class InheritedNative : BaseNative()
}
