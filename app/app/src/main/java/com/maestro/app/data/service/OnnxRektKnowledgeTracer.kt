package com.maestro.app.data.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
        val modelFile = modelArtifacts.getModelFile(
            ModelArtifactType.KT_ONNX
        ) ?: return fallback.trace(inputs)
        val before = resourceSampler.sample()
        val startedAt = System.currentTimeMillis()
        monitoringLogs.append(
            category = MonitoringLogCategory.KT_RUNTIME,
            eventType = "kt_inference_requested",
            metadata = mapOf(
                "model_type" to "kt_onnx",
                "input_count" to inputs.size.toString(),
                "sequence_event_count" to inputs.sumOf {
                    it.events.size
                }.toString(),
                "model_path" to modelFile.absolutePath
            ) + before.toMetadata("before")
        )
        return try {
            val result = runOnnx(modelFile, inputs)
            val after = resourceSampler.sample()
            monitoringLogs.append(
                category = MonitoringLogCategory.KT_RUNTIME,
                eventType = "kt_inference_completed",
                metadata = mapOf(
                    "model_type" to "kt_onnx",
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
                    "model_type" to "kt_onnx",
                    "fallback" to "heuristic",
                    "error" to (e.message ?: e::class.java.name)
                )
            )
            fallback.trace(inputs)
        }
    }

    private fun runOnnx(
        modelFile: File,
        inputs: List<RektTraceInput>
    ): Map<String, KnowledgeTraceResult> {
        val env = OrtEnvironment.getEnvironment()
        env.createSession(
            modelFile.absolutePath,
            OrtSession.SessionOptions()
        ).use { session ->
            return inputs.associate { input ->
                input.keyId to runTrace(env, session, input)
            }
        }
    }

    private fun runTrace(
        env: OrtEnvironment,
        session: OrtSession,
        input: RektTraceInput
    ): KnowledgeTraceResult {
        val events = input.events.ifEmpty {
            return KnowledgeTraceResult(0f, 0f, true)
        }
        val problemIds = events.mapIndexed { index, event ->
            stablePositiveId(event.id.ifBlank { "$index" })
        }.toLongArray()
        val skillIds = events.map { event ->
            stablePositiveId(
                event.conceptIds.firstOrNull() ?: input.keyId
            )
        }.toLongArray()
        val answers = events.map {
            if (it.correctness == false) 0L else 1L
        }.toLongArray()
        val shape = longArrayOf(1L, events.size.toLong())
        val tensors = listOf(
            OnnxTensor.createTensor(env, LongBuffer.wrap(problemIds), shape),
            OnnxTensor.createTensor(env, LongBuffer.wrap(skillIds), shape),
            OnnxTensor.createTensor(env, LongBuffer.wrap(answers), shape)
        )
        tensors.useAll {
            val sessionInputs = session.inputNames.toList()
            val inputMap = mutableMapOf<String, OnnxTensor>()
            val names = listOf(
                "problemIds",
                "skillIds",
                "answerCorrectness"
            )
            names.forEachIndexed { index, name ->
                inputMap[if (name in sessionInputs) name else sessionInputs[index]] =
                    tensors[index]
            }
            session.run(inputMap).use { result ->
                val value = result.get(0).value
                val mastery = extractMastery(value)
                return KnowledgeTraceResult(
                    mastery = mastery.coerceIn(0f, 1f),
                    confidence = 1f,
                    usingModel = true
                )
            }
        }
    }

    private fun stablePositiveId(value: String): Long {
        return (abs(value.hashCode()) % MAX_REKT_ID).toLong()
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
        private const val MAX_REKT_ID = 199
    }
}
