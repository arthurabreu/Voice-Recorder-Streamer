package com.mercantil.core.websockets.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import timber.log.Timber
import kotlin.math.min

/**
 * Centralized logger and small utilities for Assistant IA WebSocket flows.
 *
 * Rules:
 * - All logs use tag "MelWS"
 * - Mask format: ClassName -> MethodName : "message"
 * - Use Timber.tag
 */
object AssistantWsLog {
    private const val TAG = "MelWS"

    // region Public logging API
    fun d(owner: Any, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).d(mask(ownerName(owner), method, message), *args)

    fun d(owner: Class<*>, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).d(mask(owner.simpleName, method, message), *args)

    fun i(owner: Any, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).i(mask(ownerName(owner), method, message), *args)

    fun i(owner: Class<*>, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).i(mask(owner.simpleName, method, message), *args)

    fun w(owner: Any, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).w(mask(ownerName(owner), method, message), *args)

    fun w(owner: Class<*>, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).w(mask(owner.simpleName, method, message), *args)

    fun e(owner: Any, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).e(mask(ownerName(owner), method, message), *args)

    fun e(owner: Class<*>, method: String, message: String, vararg args: Any?) =
        Timber.tag(TAG).e(mask(owner.simpleName, method, message), *args)

    fun e(owner: Any, method: String, t: Throwable, message: String? = null) {
        val m = mask(ownerName(owner), method, message ?: "")
        Timber.tag(TAG).e(t, m)
    }

    fun e(owner: Class<*>, method: String, t: Throwable, message: String? = null) {
        val m = mask(owner.simpleName, method, message ?: "")
        Timber.tag(TAG).e(t, m)
    }
    // endregion

    // region Throttled logging support
    private val lastLogMs = ConcurrentHashMap<String, AtomicLong>()
    fun shouldLog(key: String = "MelWS_DEFAULT", intervalMs: Long = 1_500L, nowMs: Long = android.os.SystemClock.elapsedRealtime()): Boolean {
        val last = lastLogMs.getOrPut(key) { AtomicLong(0L) }
        val prev = last.get()
        return if (nowMs - prev >= intervalMs) {
            last.set(nowMs)
            true
        } else {
            false
        }
    }
    // endregion

    // region Small helpers
    /**
     * Produces a compact hexadecimal preview of the given ByteArray.
     * Converts up to [maxBytes] bytes into uppercase two-digit hex pairs
     * separated by single spaces (e.g., "01 AB 00"). Trailing space is trimmed.
     * Why: lets us quickly inspect binary payloads (audio/WS frames) in logs without flooding them,
     * helping debug format/endianness issues and anomalies while keeping logs lightweight.
     */
    fun hexDump(bytes: ByteArray, maxBytes: Int = 32): String {
        val dumpLen = min(maxBytes, bytes.size)
        val sb = StringBuilder(dumpLen * 3)
        var i = 0
        while (i < dumpLen) {
            sb.append(String.format("%02X", bytes[i])).append(' ')
            i++
        }
        return sb.toString().trim()
    }

    private fun mask(owner: String, method: String, message: String): String =
        "$owner -> $method : \"$message\""

    private fun ownerName(owner: Any?): String = when (owner) {
        is Class<*> -> owner.simpleName
        null -> "Unknown"
        else -> owner.javaClass.simpleName
    }
    // endregion
}
