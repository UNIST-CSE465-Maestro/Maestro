package com.maestro.app.data.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.maestro.app.data.local.StudyEvent
import com.maestro.app.domain.service.KnowledgeTraceResult
import com.maestro.app.domain.service.RektKnowledgeTracer
import com.maestro.app.domain.service.RektTraceInput
import java.io.File
import java.nio.LongBuffer
import kotlin.math.abs

class OnnxRektKnowledgeTracer(
    private val context: Context,
    private val fallback: RektKnowledgeTracer = HeuristicKnowledgeTracer()
) : RektKnowledgeTracer {
    override fun trace(inputs: List<RektTraceInput>): Map<String, KnowledgeTraceResult> {
        val modelFile = copyModelToCache()
            ?: return fallback.trace(inputs)
        return try {
            runOnnx(modelFile, inputs)
        } catch (_: Throwable) {
            fallback.trace(inputs)
        }
    }

    private fun copyModelToCache(): File? {
        return try {
            context.assets.open(MODEL_ASSET).use { input ->
                val out = File(context.cacheDir, "rekt.onnx")
                out.outputStream().use { input.copyTo(it) }
                out
            }
        } catch (_: Throwable) {
            null
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
        private const val MODEL_ASSET = "rekt/rekt.onnx"
        private const val MAX_REKT_ID = 199
    }
}
