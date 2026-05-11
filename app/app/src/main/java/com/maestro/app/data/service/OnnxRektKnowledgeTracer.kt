package com.maestro.app.data.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.maestro.app.data.local.DeviceResourceSampler
import com.maestro.app.data.local.ModelArtifactLocalDataSource
import com.maestro.app.data.local.ModelArtifactType
import com.maestro.app.data.local.MonitoringLogCategory
import com.maestro.app.data.local.MonitoringLogLocalDataSource
import com.maestro.app.data.local.StudyEvent
import com.maestro.app.domain.service.KnowledgeTraceResult
import com.maestro.app.domain.service.RektKnowledgeTracer
import com.maestro.app.domain.service.RektTraceInput
import java.io.File
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.abs

class OnnxRektKnowledgeTracer(
    private val context: Context,
    private val modelArtifacts: ModelArtifactLocalDataSource,
    private val monitoringLogs: MonitoringLogLocalDataSource,
    private val resourceSampler: DeviceResourceSampler,
    private val fallback: RektKnowledgeTracer = HeuristicKnowledgeTracer()
) : RektKnowledgeTracer {
    override fun trace(inputs: List<RektTraceInput>): Map<String, KnowledgeTraceResult> {
        val model = selectModel()
            ?: return fallback.trace(inputs)
        val before = resourceSampler.sample()
        val startedAt = System.currentTimeMillis()
        monitoringLogs.append(
            category = MonitoringLogCategory.KT_RUNTIME,
            eventType = "kt_inference_requested",
            metadata = mapOf(
                "model_type" to model.kind.logName,
                "model_display_name" to model.kind.displayName,
                "input_count" to inputs.size.toString(),
                "sequence_event_count" to inputs.sumOf {
                    it.events.size
                }.toString(),
                "model_path" to model.file.absolutePath
            ) + before.toMetadata("before")
        )
        return try {
            val result = runOnnx(model, inputs)
            val after = resourceSampler.sample()
            monitoringLogs.append(
                category = MonitoringLogCategory.KT_RUNTIME,
                eventType = "kt_inference_completed",
                metadata = mapOf(
                    "model_type" to model.kind.logName,
                    "model_display_name" to model.kind.displayName,
                    "latency_ms" to (
                        System.currentTimeMillis() - startedAt
                        ).toString(),
                    "output_count" to result.size.toString(),
                    "average_mastery" to (
                        result.values.map { it.mastery }
                            .average()
                            .takeIf { !it.isNaN() }
                            ?.toFloat() ?: 0f
                        ).toString()
                ) + after.toMetadata("after")
            )
            monitoringLogs.append(
                category = MonitoringLogCategory.DEVICE_RESOURCE,
                eventType = "kt_resource_sample",
                metadata = mapOf(
                    "latency_ms" to (
                        System.currentTimeMillis() - startedAt
                        ).toString(),
                    "battery_delta_percent" to (
                        (before.batteryPercent ?: 0) -
                            (after.batteryPercent ?: 0)
                        ).toString(),
                    "heap_delta_mb" to (
                        after.appHeapUsedMb - before.appHeapUsedMb
                        ).toString(),
                    "app_cpu_delta_ms" to (
                        after.appCpuTimeMs - before.appCpuTimeMs
                        ).toString()
                ) + before.toMetadata("before") +
                    after.toMetadata("after")
            )
            result
        } catch (e: Throwable) {
            monitoringLogs.append(
                category = MonitoringLogCategory.UX_RELIABILITY,
                eventType = "kt_inference_failed",
                metadata = mapOf(
                    "model_type" to model.kind.logName,
                    "model_display_name" to model.kind.displayName,
                    "fallback" to "heuristic",
                    "error" to (e.message ?: e::class.java.name)
                )
            )
            fallback.trace(inputs)
        }
    }

    private fun selectModel(): OnnxKtModel? {
        val mikt = modelArtifacts.getModelFile(
            ModelArtifactType.MIKT_ONNX
        )
        if (mikt != null) {
            return OnnxKtModel(mikt, KtModelKind.MIKT)
        }
        val generic = modelArtifacts.getModelFile(
            ModelArtifactType.KT_ONNX
        )
        if (generic != null) {
            return OnnxKtModel(generic, KtModelKind.GENERIC_KT)
        }
        return null
    }

    private fun runOnnx(
        model: OnnxKtModel,
        inputs: List<RektTraceInput>
    ): Map<String, KnowledgeTraceResult> {
        val env = OrtEnvironment.getEnvironment()
        env.createSession(
            model.file.absolutePath,
            OrtSession.SessionOptions()
        ).use { session ->
            return inputs.associate { input ->
                input.keyId to runTrace(
                    env = env,
                    session = session,
                    input = input,
                    kind = model.kind
                )
            }
        }
    }

    private fun runTrace(
        env: OrtEnvironment,
        session: OrtSession,
        input: RektTraceInput,
        kind: KtModelKind
    ): KnowledgeTraceResult {
        val events = input.events.ifEmpty {
            return KnowledgeTraceResult(
                mastery = 0f,
                confidence = 0f,
                usingModel = true,
                modelName = kind.displayName
            )
        }
        val sequences = buildSequences(input, events)
        val tensors = mutableListOf<OnnxTensor>()
        tensors.useAll {
            val sessionInputs = session.inputNames.toList()
            val inputMap = mutableMapOf<String, OnnxTensor>()
            sessionInputs.forEachIndexed { index, name ->
                val values = valuesForInput(
                    name = name,
                    index = index,
                    totalInputs = sessionInputs.size,
                    sequences = sequences,
                    kind = kind
                )
                val tensor = createTensor(
                    env = env,
                    session = session,
                    name = name,
                    values = values
                )
                tensors += tensor
                inputMap[name] = tensor
            }
            session.run(inputMap).use { result ->
                val value = result.get(0).value
                val mastery = extractMastery(value)
                return KnowledgeTraceResult(
                    mastery = mastery.coerceIn(0f, 1f),
                    confidence = 1f,
                    usingModel = true,
                    modelName = kind.displayName
                )
            }
        }
    }

    private fun buildSequences(
        input: RektTraceInput,
        events: List<StudyEvent>
    ): KtSequences {
        val sorted = events.sortedBy { it.timestamp }
        val problemIds = sorted.mapIndexed { index, event ->
            stablePositiveId(
                event.id.ifBlank { "${input.keyId}_$index" },
                MAX_SEQUENCE_ID
            )
        }.toLongArray()
        val skillIds = sorted.map { event ->
            stablePositiveId(
                event.conceptIds.firstOrNull() ?: input.keyId,
                MAX_SEQUENCE_ID
            )
        }.toLongArray()
        val answers = sorted.map {
            if (it.correctness == false) 0L else 1L
        }.toLongArray()
        val interactions = problemIds.mapIndexed { index, problemId ->
            problemId + answers[index] * MAX_SEQUENCE_ID
        }.toLongArray()
        val domainIds = LongArray(sorted.size) {
            stablePositiveId(input.keyId, MAX_DOMAIN_ID)
        }
        val masks = LongArray(sorted.size) { 1L }
        return KtSequences(
            problemIds = problemIds,
            skillIds = skillIds,
            answers = answers,
            interactions = interactions,
            domainIds = domainIds,
            masks = masks
        )
    }

    private fun valuesForInput(
        name: String,
        index: Int,
        totalInputs: Int,
        sequences: KtSequences,
        kind: KtModelKind
    ): LongArray {
        val normalized = name.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        return when {
            normalized.contains("mask") ->
                sequences.masks
            normalized.contains("domain") ||
                normalized.contains("coarse") ||
                normalized.contains("subject") ->
                sequences.domainIds
            normalized.contains("qa") ||
                normalized.contains("interaction") ||
                normalized.contains("xseq") ->
                sequences.interactions
            normalized.contains("answer") ||
                normalized.contains("correct") ||
                normalized.contains("response") ||
                normalized.contains("label") ||
                normalized.contains("target") ||
                normalized.contains("rseq") ->
                sequences.answers
            normalized.contains("skill") ||
                normalized.contains("concept") ||
                normalized.contains("kc") ||
                normalized.contains("cseq") ->
                sequences.skillIds
            normalized.contains("problem") ||
                normalized.contains("question") ||
                normalized.contains("item") ||
                normalized.contains("exercise") ||
                normalized.contains("exer") ||
                normalized.contains("pid") ||
                normalized.contains("qseq") ||
                normalized == "q" ->
                sequences.problemIds
            kind == KtModelKind.MIKT && index == 1 && totalInputs <= 3 ->
                sequences.interactions
            index == 0 -> sequences.problemIds
            index == 1 -> sequences.skillIds
            index == 2 -> sequences.answers
            index == 3 -> sequences.domainIds
            else -> sequences.masks
        }
    }

    private fun createTensor(
        env: OrtEnvironment,
        session: OrtSession,
        name: String,
        values: LongArray
    ): OnnxTensor {
        val shape = shapeFor(session, name, values.size)
        val type = (session.inputInfo[name]?.info as? TensorInfo)
            ?.type
        return when (type) {
            OnnxJavaType.INT32 -> OnnxTensor.createTensor(
                env,
                IntBuffer.wrap(values.map { it.toInt() }.toIntArray()),
                shape
            )
            OnnxJavaType.FLOAT -> OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(values.map { it.toFloat() }.toFloatArray()),
                shape
            )
            OnnxJavaType.DOUBLE -> OnnxTensor.createTensor(
                env,
                DoubleBuffer.wrap(values.map { it.toDouble() }.toDoubleArray()),
                shape
            )
            else -> OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(values),
                shape
            )
        }
    }

    private fun shapeFor(
        session: OrtSession,
        name: String,
        sequenceLength: Int
    ): LongArray {
        val rank = (session.inputInfo[name]?.info as? TensorInfo)
            ?.shape
            ?.size
            ?: 2
        return when (rank) {
            0 -> longArrayOf(1L)
            1 -> longArrayOf(sequenceLength.toLong())
            2 -> longArrayOf(1L, sequenceLength.toLong())
            3 -> longArrayOf(1L, sequenceLength.toLong(), 1L)
            else -> LongArray(rank) { index ->
                when (index) {
                    0 -> 1L
                    1 -> sequenceLength.toLong()
                    else -> 1L
                }
            }
        }
    }

    private fun stablePositiveId(value: String, modulo: Int): Long {
        return (abs(value.hashCode()) % modulo).toLong()
    }

    private fun extractMastery(value: Any?): Float {
        return when (value) {
            is Array<*> -> {
                val row = value.lastOrNull()
                when (row) {
                    is FloatArray -> row.lastOrNull() ?: 0f
                    is DoubleArray -> row.lastOrNull()?.toFloat() ?: 0f
                    is Array<*> -> extractMastery(row)
                    else -> 0f
                }
            }
            is FloatArray -> value.lastOrNull() ?: 0f
            is DoubleArray -> value.lastOrNull()?.toFloat() ?: 0f
            else -> 0f
        }
    }

    private inline fun <T : AutoCloseable, R> List<T>.useAll(block: () -> R): R {
        try {
            return block()
        } finally {
            forEach { it.close() }
        }
    }

    companion object {
        private const val MAX_SEQUENCE_ID = 199
        private const val MAX_DOMAIN_ID = 32
    }
}

private data class OnnxKtModel(
    val file: File,
    val kind: KtModelKind
)

private enum class KtModelKind(
    val logName: String,
    val displayName: String
) {
    MIKT("mikt_onnx", "MIKT ONNX"),
    GENERIC_KT("kt_onnx", "KT ONNX")
}

private data class KtSequences(
    val problemIds: LongArray,
    val skillIds: LongArray,
    val answers: LongArray,
    val interactions: LongArray,
    val domainIds: LongArray,
    val masks: LongArray
)
