package com.rewire.app.data

import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Small persisted state: loaded synchronously once (files are tiny), written atomically off-thread,
 * latest value wins. Lets the accessibility service read rules even when the UI never started.
 */
// ponytail: whole-file JSON rewrite per change; fine for hundreds of rows, Room in Phase 2 when history grows.
class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    scope: CoroutineScope,
    default: () -> T,
) {
    private val atomic = AtomicFile(file)
    private val state = MutableStateFlow(load() ?: default())
    val value: StateFlow<T> = state.asStateFlow()
    private val pending = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) { for (v in pending) write(v) }
    }

    fun update(transform: (T) -> T) {
        state.update(transform)
        pending.trySend(state.value)
    }

    private fun load(): T? = runCatching {
        if (!file.exists()) null else json.decodeFromString(serializer, atomic.readFully().decodeToString())
    }.getOrNull() // corrupt file -> defaults; never crash the guard

    private fun write(v: T) {
        val out = atomic.startWrite()
        runCatching {
            out.write(json.encodeToString(serializer, v).encodeToByteArray())
            atomic.finishWrite(out)
        }.onFailure { atomic.failWrite(out) }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
