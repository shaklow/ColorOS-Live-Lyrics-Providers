/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.apple

import java.lang.reflect.Method

internal object AppleNativeCalls {
    fun call(any: Any, name: String, vararg args: Any?): Any? = runCatching {
        val method = findMethod(any.javaClass, name, args) ?: return@runCatching null
        method.isAccessible = true
        method.invoke(any, *args)
    }.getOrNull()

    fun callString(any: Any, name: String, vararg args: Any?): String? =
        call(any, name, *args) as? String

    fun callBoolean(any: Any, name: String, vararg args: Any?): Boolean? =
        call(any, name, *args) as? Boolean

    fun callLong(any: Any, name: String): Long = when (val value = call(any, name)) {
        is Number -> value.toLong()
        else -> 0L
    }

    fun callInt(any: Any, name: String): Int = when (val value = call(any, name)) {
        is Number -> value.toInt()
        else -> 0
    }

    fun vectorSize(vector: Any): Long = when (val value = call(vector, "size")) {
        is Number -> value.toLong()
        else -> 0L
    }

    fun vectorItem(vector: Any, index: Long): Any? {
        call(vector, "get", index)?.let { return it }
        return call(vector, "get", index.toInt())
    }

    fun unwrapPtr(ptr: Any?): Any? {
        if (ptr == null) return null
        return call(ptr, "get") ?: ptr
    }

    /** Native vectors contain smart pointers; missing elements are safe to skip. */
    fun <T> parseVector(vector: Any, mapper: (Any) -> T?): List<T> {
        val result = mutableListOf<T>()
        var index = 0L
        val size = vectorSize(vector)
        while (index < size) {
            unwrapPtr(vectorItem(vector, index))?.let { native ->
                mapper(native)?.let(result::add)
            }
            index++
        }
        return result
    }

    private fun findMethod(type: Class<*>, name: String, args: Array<out Any?>): Method? {
        val candidates = generateSequence(type) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .plus(type.methods.asSequence())
            .filter { it.name == name && it.parameterCount == args.size }
            .distinctBy { it.parameterTypes.toList() }
            .filter { method ->
                method.parameterTypes.indices.all { index ->
                    val argument = args[index]
                    val parameter = method.parameterTypes[index]
                    if (argument == null) !parameter.isPrimitive
                    else boxed(parameter).isInstance(argument)
                }
            }.toList()
        val exact = candidates.filter { method ->
            method.parameterTypes.indices.all { index ->
                boxed(method.parameterTypes[index]) == args[index]?.javaClass
            }
        }
        return exact.singleOrNull() ?: candidates.singleOrNull()
    }

    private fun boxed(type: Class<*>): Class<*> = type.kotlin.javaObjectType
}
