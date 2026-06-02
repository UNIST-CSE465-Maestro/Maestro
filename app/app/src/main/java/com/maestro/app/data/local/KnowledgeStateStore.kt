package com.maestro.app.data.local

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mutable per-learner MobileKT v4 state. Tensors are flattened row-major to
 * match the bundled `*.bin` initial state (raw little-endian float32).
 *
 * - [skillState]: `N_CONCEPTS * EMB_DIM`
 * - [allState]: `EMB_DIM`
 * - [lastSkillTime], [tapSeenCount], [tapRecentCorrectRate]: `N_CONCEPTS`
 */
class KnowledgeState(
    var step: Float,
    val skillState: FloatArray,
    val allState: FloatArray,
    val lastSkillTime: FloatArray,
    val tapSeenCount: FloatArray,
    val tapRecentCorrectRate: FloatArray,
    val ringBuffers: MutableMap<Int, ArrayDeque<Int>>
)

/**
 * Loads the bundled initial MobileKT state and persists each learner's evolving
 * state to a single binary file. One state per profile; the engine increments
 * the step after every answer.
 */
class KnowledgeStateStore(
    private val modelDir: File,
    private val stateFile: File
) {
    constructor(context: Context) : this(
        modelDir = File(context.filesDir, "models"),
        stateFile = File(context.filesDir, "knowledge_state/profile.bin")
    ) {
        stateFile.parentFile?.mkdirs()
    }

    /** Reads the persisted state, or builds the bundled initial state. */
    @Synchronized
    fun loadOrInit(): KnowledgeState {
        readPersisted()?.let { return it }
        return buildInitial()
    }

    @Synchronized
    fun save(state: KnowledgeState) {
        stateFile.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(stateFile.outputStream())).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeFloat(state.step)
            writeFloats(out, state.skillState)
            writeFloats(out, state.allState)
            writeFloats(out, state.lastSkillTime)
            writeFloats(out, state.tapSeenCount)
            writeFloats(out, state.tapRecentCorrectRate)
            out.writeInt(state.ringBuffers.size)
            state.ringBuffers.forEach { (conceptId, ring) ->
                out.writeInt(conceptId)
                out.writeInt(ring.size)
                ring.forEach { out.writeInt(it) }
            }
        }
    }

    /** Resets to the bundled initial state and removes the persisted file. */
    @Synchronized
    fun reset(): KnowledgeState {
        stateFile.delete()
        return buildInitial()
    }

    private fun readPersisted(): KnowledgeState? {
        if (!stateFile.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(stateFile.inputStream())).use { input ->
                val version = input.readInt()
                if (version != FORMAT_VERSION) return null
                val step = input.readFloat()
                val skillState = readFloats(input, N_CONCEPTS * EMB_DIM)
                val allState = readFloats(input, EMB_DIM)
                val lastSkillTime = readFloats(input, N_CONCEPTS)
                val tapSeenCount = readFloats(input, N_CONCEPTS)
                val tapRecentCorrectRate = readFloats(input, N_CONCEPTS)
                val ringCount = input.readInt()
                val rings = HashMap<Int, ArrayDeque<Int>>(ringCount)
                repeat(ringCount) {
                    val conceptId = input.readInt()
                    val len = input.readInt()
                    val ring = ArrayDeque<Int>(len)
                    repeat(len) { ring.addLast(input.readInt()) }
                    rings[conceptId] = ring
                }
                KnowledgeState(
                    step = step,
                    skillState = skillState,
                    allState = allState,
                    lastSkillTime = lastSkillTime,
                    tapSeenCount = tapSeenCount,
                    tapRecentCorrectRate = tapRecentCorrectRate,
                    ringBuffers = rings
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildInitial(): KnowledgeState = KnowledgeState(
        step = 0f,
        skillState = readBin("skill_state.bin", N_CONCEPTS * EMB_DIM, 0f),
        allState = readBin("all_state.bin", EMB_DIM, 0f),
        lastSkillTime = readBin("last_skill_time.bin", N_CONCEPTS, 0f),
        tapSeenCount = readBin("tap_seen_count.bin", N_CONCEPTS, 0f),
        tapRecentCorrectRate = readBin("tap_recent_correct_rate.bin", N_CONCEPTS, 0.5f),
        ringBuffers = HashMap()
    )

    /** Reads a bundled raw little-endian float32 array, or a filled default. */
    private fun readBin(name: String, size: Int, default: Float): FloatArray {
        val file = File(modelDir, name)
        if (!file.exists() || file.length() < size.toLong() * 4) {
            return FloatArray(size) { default }
        }
        return try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(size) { buffer.float }
        } catch (_: Throwable) {
            FloatArray(size) { default }
        }
    }

    private fun writeFloats(out: DataOutputStream, values: FloatArray) {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        out.write(buffer.array())
    }

    private fun readFloats(input: DataInputStream, size: Int): FloatArray {
        val bytes = ByteArray(size * 4)
        input.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size) { buffer.float }
    }

    companion object {
        const val N_CONCEPTS = 641
        const val EMB_DIM = 64
        const val RING_WINDOW = 5
        private const val FORMAT_VERSION = 1
    }
}
