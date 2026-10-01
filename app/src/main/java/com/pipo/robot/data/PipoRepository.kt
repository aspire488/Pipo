package com.pipo.robot.data

import android.content.Context
import com.pipo.robot.engine.DAY
import com.pipo.robot.engine.Personality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

/**
 * Single source of truth for Pipo's persisted state. All access goes through [read]/[mutate]
 * under one lock, so the UI thread and the background worker never corrupt each other.
 * Stored as one JSON file, written atomically (tmp file + rename).
 */
class PipoRepository private constructor(private val file: File) {
    private val lock = Any()
    private val json = PipoJson
    private var state: PipoState = load()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSave: Job? = null

    private val _version = MutableStateFlow(0L)
    /** Bumps whenever something user-visible changes. Screens re-read on change. */
    val version: StateFlow<Long> = _version

    private fun load(): PipoState {
        return try {
            if (!file.exists()) return newState()
            val s = json.decodeFromString(PipoState.serializer(), file.readText())
            if (s.schema < CURRENT_SCHEMA) {
                // Keep the old save next to the new one, so an upgrade can never cost anyone their Pipo.
                runCatching { file.copyTo(File(file.parentFile, "pipo_state.v${s.schema}.bak.json"), overwrite = false) }
            }
            if (Migrations.migrate(s, System.currentTimeMillis())) {
                runCatching { writeAtomically(json.encodeToString(PipoState.serializer(), s)) }
            }
            s
        } catch (e: Exception) {
            // Corrupt file: keep a copy for debugging, start fresh rather than crash.
            runCatching { file.copyTo(File(file.parentFile, "pipo_state.corrupt.json"), overwrite = true) }
            newState()
        }
    }

    private fun newState(): PipoState {
        val now = System.currentTimeMillis()
        val rng = Random(now)
        return PipoState(seed = now).apply {
            profile.createdAt = now
            profile.traits = Personality.newTraits(rng)
            activity.type = ActivityType.SLEEP
            activity.startedAt = now
            activity.durationMs = DAY
            lastSimulatedAt = now
            pet.traits = com.pipo.robot.engine.PetEngine.newTraits(rng)
            pet.adoptedAt = now
        }
    }

    fun <T> read(block: (PipoState) -> T): T = synchronized(lock) { block(state) }

    /** Mutate state. [notify]=false for high-frequency ticks that no screen needs to observe. */
    fun <T> mutate(notify: Boolean = true, save: Boolean = true, block: (PipoState) -> T): T {
        val r = synchronized(lock) { block(state) }
        if (notify) _version.value = _version.value + 1
        if (save) scheduleSave()
        return r
    }

    private fun scheduleSave() {
        if (pendingSave?.isActive == true) return
        pendingSave = scope.launch {
            delay(1500)
            saveNow()
        }
    }

    fun saveAsync() { scope.launch { saveNow() } }

    fun saveNow() {
        val text = synchronized(lock) { json.encodeToString(PipoState.serializer(), state) }
        writeAtomically(text)
    }

    private fun writeAtomically(text: String) {
        synchronized(file) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    fun reset() {
        synchronized(lock) { state = newState() }
        _version.value = _version.value + 1
        saveNow()
    }

    companion object {
        @Volatile private var instance: PipoRepository? = null
        fun get(ctx: Context): PipoRepository = instance ?: synchronized(this) {
            instance ?: PipoRepository(File(ctx.applicationContext.filesDir, "pipo_state.json")).also { instance = it }
        }
    }
}
