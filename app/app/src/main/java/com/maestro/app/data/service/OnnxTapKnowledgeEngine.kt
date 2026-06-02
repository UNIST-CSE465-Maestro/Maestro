package com.maestro.app.data.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.maestro.app.data.local.DeviceResourceSampler
import com.maestro.app.data.local.KnowledgeState
import com.maestro.app.data.local.KnowledgeStateStore
import com.maestro.app.data.local.KnowledgeStateStore.Companion.EMB_DIM
import com.maestro.app.data.local.KnowledgeStateStore.Companion.N_CONCEPTS
import com.maestro.app.data.local.KnowledgeStateStore.Companion.RING_WINDOW
import com.maestro.app.data.local.MonitoringLogCategory
import com.maestro.app.data.local.MonitoringLogLocalDataSource
import com.maestro.app.domain.service.ConceptMastery
import com.maestro.app.domain.service.KnowledgeTracingEngine
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bundled MobileKT v4 (MIKT + TAP) knowledge-tracing engine.
 *
 * Loads the ONNX models and initial state bundled in
 * `assets/model_artifacts/`, derives per-concept mastery purely from the TAP
 * readout, and persists one evolving state per learner profile.
 */
class OnnxTapKnowledgeEngine(
    private val context: Context,
    private val store: KnowledgeStateStore,
    private val monitoringLogs: MonitoringLogLocalDataSource? = null,
    private val resourceSampler: DeviceResourceSampler? = null
) : KnowledgeTracingEngine {
    private val modelDir = File(context.filesDir, "models").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private var env: OrtEnvironment? = null
    private var updateSession: OrtSession? = null
    private var readoutSession: OrtSession? = null
    private var sessionsLoaded = false

    // conceptId (1..640) -> key / display name
    private data class CatalogEntry(val key: String, val name: String)

    private val catalog: Map<Int, CatalogEntry> by lazy { loadCatalog() }

    init {
        syncBundledFiles()
    }

    override fun isReady(): Boolean = synchronized(lock) {
        updateModelFile().exists() && readoutModelFile().exists()
    }

    override fun recordAnswer(
        questionEmbedding: FloatArray,
        difficulty: Float,
        conceptIds: List<Int>,
        correct: Boolean
    ) = synchronized(lock) {
        if (!ensureSessions()) {
            monitoringLogs?.append(
                category = MonitoringLogCategory.KT_RUNTIME,
                eventType = "tap_engine_unavailable",
                metadata =
                mapOf(
                    "stage" to "record_answer",
                    "concept_ids" to conceptIds.joinToString(",")
                )
            )
            return
        }
        val update = updateSession ?: return
        val ortEnv = env ?: return
        val state = store.loadOrInit()
        val emb = questionEmbedding.fitTo(EMB_DIM)
        val paddedConcepts = padConceptIds(conceptIds)
        val response = if (correct) 1L else 0L
        val stepUsed = state.step
        val before = resourceSampler?.sample()
        val startedAt = System.currentTimeMillis()

        val inputs =
            mapOf(
                "question_embedding" to floatTensor(ortEnv, emb, longArrayOf(1, EMB_DIM.toLong())),
                "difficulty" to floatTensor(ortEnv, floatArrayOf(difficulty), longArrayOf(1)),
                "concept_ids" to longTensor(ortEnv, paddedConcepts, longArrayOf(1, 10)),
                "response" to longTensor(ortEnv, longArrayOf(response), longArrayOf(1)),
                "skill_state" to
                    floatTensor(
                        ortEnv,
                        state.skillState,
                        longArrayOf(1, N_CONCEPTS.toLong(), EMB_DIM.toLong())
                    ),
                "all_state" to
                    floatTensor(
                        ortEnv,
                        state.allState,
                        longArrayOf(1, EMB_DIM.toLong())
                    ),
                "last_skill_time" to
                    floatTensor(
                        ortEnv,
                        state.lastSkillTime,
                        longArrayOf(1, N_CONCEPTS.toLong())
                    ),
                "step" to floatTensor(ortEnv, floatArrayOf(stepUsed), longArrayOf(1))
            )
        try {
            update.run(inputs).use { result ->
                copyInto(result, "next_skill_state", state.skillState)
                copyInto(result, "next_all_state", state.allState)
                copyInto(result, "next_last_skill_time", state.lastSkillTime)
            }
        } catch (e: Throwable) {
            inputs.values.forEach { it.close() }
            monitoringLogs?.append(
                category = MonitoringLogCategory.KT_RUNTIME,
                eventType = "tap_update_failed",
                metadata =
                mapOf(
                    "concept_ids" to conceptIds.joinToString(","),
                    "error" to (e.message ?: e::class.java.name)
                )
            )
            return
        }
        inputs.values.forEach { it.close() }

        // Update TAP profile stats for every unique positive concept.
        conceptIds.filter { it in 1 until N_CONCEPTS }.distinct().forEach { id ->
            state.tapSeenCount[id] = state.tapSeenCount[id] + 1f
            val ring = state.ringBuffers.getOrPut(id) { ArrayDeque() }
            ring.addLast(if (correct) 1 else 0)
            while (ring.size > RING_WINDOW) ring.removeFirst()
            state.tapRecentCorrectRate[id] =
                if (ring.isEmpty()) 0.5f else ring.average().toFloat()
        }

        state.step = stepUsed + 1f
        store.save(state)

        // Experiment monitoring: the bundled MIKT update is the on-device KT
        // inference run once per answered question.
        val latencyMs = System.currentTimeMillis() - startedAt
        monitoringLogs?.append(
            category = MonitoringLogCategory.KT_RUNTIME,
            eventType = "tap_update",
            metadata =
            mapOf(
                "engine" to "mobile_mikt+tap",
                "latency_ms" to latencyMs.toString(),
                "concept_ids" to conceptIds.joinToString(","),
                "concept_count" to conceptIds.distinct().size.toString(),
                "correct" to correct.toString(),
                "difficulty" to difficulty.toString(),
                "embedding_dim" to emb.size.toString(),
                "step" to stepUsed.toInt().toString()
            )
        )
        val after = resourceSampler?.sample()
        if (before != null && after != null) {
            monitoringLogs?.append(
                category = MonitoringLogCategory.DEVICE_RESOURCE,
                eventType = "tap_resource_sample",
                metadata =
                mapOf(
                    "latency_ms" to latencyMs.toString(),
                    "battery_delta_percent" to
                        (
                            (before.batteryPercent ?: 0) -
                                (after.batteryPercent ?: 0)
                            ).toString(),
                    "heap_delta_mb" to
                        (after.appHeapUsedMb - before.appHeapUsedMb).toString(),
                    "app_cpu_delta_ms" to
                        (after.appCpuTimeMs - before.appCpuTimeMs).toString()
                ) + before.toMetadata("before") + after.toMetadata("after")
            )
        }
    }

    override fun masteryByConceptKey(): Map<String, ConceptMastery> = synchronized(lock) {
        if (!ensureSessions()) return emptyMap()
        val readout = readoutSession ?: return emptyMap()
        val ortEnv = env ?: return emptyMap()
        val state = store.loadOrInit()
        val mastery = runReadout(ortEnv, readout, state) ?: return emptyMap()
        buildMap {
            for (id in 1 until N_CONCEPTS) {
                val entry = catalog[id] ?: continue
                put(
                    entry.key,
                    ConceptMastery(
                        conceptId = id,
                        conceptKey = entry.key,
                        name = entry.name,
                        mastery = mastery[id].coerceIn(0f, 1f),
                        seenCount = state.tapSeenCount[id].toInt()
                    )
                )
            }
        }
    }

    override fun reset() {
        synchronized(lock) {
            store.reset()
        }
    }

    private fun runReadout(
        ortEnv: OrtEnvironment,
        readout: OrtSession,
        state: KnowledgeState
    ): FloatArray? {
        val inputs =
            mapOf(
                "skill_state" to
                    floatTensor(
                        ortEnv,
                        state.skillState,
                        longArrayOf(1, N_CONCEPTS.toLong(), EMB_DIM.toLong())
                    ),
                "all_state" to
                    floatTensor(
                        ortEnv,
                        state.allState,
                        longArrayOf(1, EMB_DIM.toLong())
                    ),
                "last_skill_time" to
                    floatTensor(
                        ortEnv,
                        state.lastSkillTime,
                        longArrayOf(1, N_CONCEPTS.toLong())
                    ),
                "as_of_step" to
                    floatTensor(ortEnv, floatArrayOf(asOfStep(state)), longArrayOf(1)),
                "tap_seen_count" to
                    floatTensor(
                        ortEnv,
                        state.tapSeenCount,
                        longArrayOf(1, N_CONCEPTS.toLong())
                    ),
                "tap_recent_correct_rate" to
                    floatTensor(
                        ortEnv,
                        state.tapRecentCorrectRate,
                        longArrayOf(1, N_CONCEPTS.toLong())
                    )
            )
        return try {
            readout.run(inputs).use { result ->
                FloatArray(N_CONCEPTS).also { copyInto(result, "concept_mastery", it) }
            }
        } catch (_: Throwable) {
            null
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun ensureSessions(): Boolean {
        if (sessionsLoaded) return updateSession != null && readoutSession != null
        sessionsLoaded = true
        if (!updateModelFile().exists() || !readoutModelFile().exists()) {
            return false
        }
        return try {
            val ortEnv = OrtEnvironment.getEnvironment()
            env = ortEnv
            updateSession =
                ortEnv.createSession(
                    updateModelFile().absolutePath,
                    OrtSession.SessionOptions()
                )
            readoutSession =
                ortEnv.createSession(
                    readoutModelFile().absolutePath,
                    OrtSession.SessionOptions()
                )
            true
        } catch (_: Throwable) {
            updateSession = null
            readoutSession = null
            false
        }
    }

    private fun floatTensor(
        ortEnv: OrtEnvironment,
        data: FloatArray,
        shape: LongArray
    ): OnnxTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(data), shape)

    private fun longTensor(ortEnv: OrtEnvironment, data: LongArray, shape: LongArray): OnnxTensor =
        OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(data), shape)

    private fun copyInto(result: OrtSession.Result, name: String, target: FloatArray) {
        val tensor = result.get(name).orElse(null) as? OnnxTensor ?: return
        val buffer = tensor.floatBuffer ?: return
        val count = minOf(buffer.remaining(), target.size)
        buffer.get(target, 0, count)
    }

    private fun FloatArray.fitTo(size: Int): FloatArray {
        if (this.size == size) return this
        return FloatArray(size) { if (it < this.size) this[it] else 0f }
    }

    private fun padConceptIds(conceptIds: List<Int>): LongArray {
        val out = LongArray(10) { -1L }
        conceptIds.filter { it > 0 }.distinct().take(10).forEachIndexed { i, v ->
            out[i] = v.toLong()
        }
        return out
    }

    /**
     * The interaction-step index the readout is taken "as of". The step counter
     * holds the index for the *next* answer, so the most recent interaction is
     * `step - 1` (matches the repo's golden TAP readout where `as_of_step = t`
     * and the persisted step is `t + 1`).
     */
    private fun asOfStep(state: KnowledgeState): Float = (state.step - 1f).coerceAtLeast(0f)

    private fun updateModelFile() = File(modelDir, UPDATE_MODEL)

    private fun readoutModelFile() = File(modelDir, READOUT_MODEL)

    private fun loadCatalog(): Map<Int, CatalogEntry> {
        val file = File(modelDir, CATALOG_FILE)
        if (!file.exists()) return emptyMap()
        return try {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val concepts = root["concepts"]?.jsonArray ?: return emptyMap()
            buildMap {
                concepts.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val id = obj["id"]?.jsonPrimitive?.int ?: return@forEach
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: "concept_$id"
                    val name =
                        obj["display_name"]?.jsonPrimitive?.contentOrNull
                            ?: key.replace('_', ' ')
                    put(id, CatalogEntry(key = key, name = name))
                }
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    /** Copies bundled engine files from assets into files/models when changed. */
    private fun syncBundledFiles() {
        BUNDLED_FILES.forEach { name ->
            val assetPath = "$BUNDLED_DIR/$name"
            val out = File(modelDir, name)
            val tmp = File(modelDir, "$name.tmp")
            try {
                context.assets.open(assetPath).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!out.exists() || out.length() != tmp.length()) {
                    if (out.exists()) out.delete()
                    tmp.renameTo(out)
                } else {
                    tmp.delete()
                }
            } catch (_: Throwable) {
                // Optional files (e.g. the ONNX binaries) may be absent until
                // they are dropped into assets/model_artifacts/.
                tmp.delete()
            }
        }
    }

    companion object {
        private const val BUNDLED_DIR = "model_artifacts"
        private const val UPDATE_MODEL = "mobile_mikt_update.onnx"
        private const val READOUT_MODEL = "mobile_tap_readout.onnx"
        private const val CATALOG_FILE = "concept_catalog.json"
        private val BUNDLED_FILES =
            listOf(
                "skill_state.bin",
                "all_state.bin",
                "last_skill_time.bin",
                "tap_seen_count.bin",
                "tap_recent_correct_rate.bin",
                CATALOG_FILE,
                UPDATE_MODEL,
                READOUT_MODEL,
                "mobile_mikt_predict.onnx"
            )
    }
}
