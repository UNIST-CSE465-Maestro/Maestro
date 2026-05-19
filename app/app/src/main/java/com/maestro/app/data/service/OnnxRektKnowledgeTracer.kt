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
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.domain.service.KnowledgeTraceResult
import com.maestro.app.domain.service.RektKnowledgeTracer
import com.maestro.app.domain.service.RektTraceInput
import java.io.File
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.abs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OnnxRektKnowledgeTracer(
    private val context: Context,
    private val modelArtifacts: ModelArtifactLocalDataSource,
    private val monitoringLogs: MonitoringLogLocalDataSource,
    private val resourceSampler: DeviceResourceSampler,
    private val fallback: RektKnowledgeTracer = HeuristicKnowledgeTracer()
) : RektKnowledgeTracer {
    private val json = Json {
        ignoreUnknownKeys = true
    }

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
                "dataset" to model.contract.dataset,
                "contract" to model.contract.name,
                "sequence_window" to model.contract.maxSequenceLength.toString(),
                "question_as_concept" to model.contract.questionAsConcept.toString(),
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
            return OnnxKtModel(
                file = mikt,
                kind = KtModelKind.MIKT_STATICS2011,
                contract = loadStatics2011Contract()
            )
        }
        val generic = modelArtifacts.getModelFile(
            ModelArtifactType.KT_ONNX
        )
        if (generic != null) {
            return OnnxKtModel(
                file = generic,
                kind = KtModelKind.GENERIC_KT,
                contract = KtInputContract.generic()
            )
        }
        return null
    }

    private fun loadStatics2011Contract(): KtInputContract {
        val mapping = modelArtifacts.getModelFile(
            ModelArtifactType.MIKT_STATICS2011_MAPPING
        ) ?: return KtInputContract.statics2011()
        return try {
            json.decodeFromString<KtInputContract>(
                mapping.readText()
            ).normalizedForStatics2011()
        } catch (_: Throwable) {
            KtInputContract.statics2011()
        }
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
                    model = model
                )
            }
        }
    }

    private fun runTrace(
        env: OrtEnvironment,
        session: OrtSession,
        input: RektTraceInput,
        model: OnnxKtModel
    ): KnowledgeTraceResult {
        val events = input.events.ifEmpty {
            return KnowledgeTraceResult(
                mastery = 0f,
                confidence = 0f,
                usingModel = true,
                modelName = model.kind.displayName
            )
        }
        val sequences = buildSequences(
            input = input,
            events = events,
            contract = model.contract
        )
        if (sequences.confidence <= 0f) {
            return KnowledgeTraceResult(
                mastery = 0f,
                confidence = 0f,
                usingModel = true,
                modelName = model.kind.displayName
            )
        }
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
                    contract = model.contract,
                    kind = model.kind
                )
                val tensor = createTensor(
                    env = env,
                    session = session,
                    name = name,
                    values = values,
                    maskValues = sequences.masks
                )
                tensors += tensor
                inputMap[name] = tensor
            }
            session.run(inputMap).use { result ->
                val value = result.get(0).value
                val mastery = extractMastery(value)
                return KnowledgeTraceResult(
                    mastery = mastery.coerceIn(0f, 1f),
                    confidence = sequences.confidence,
                    usingModel = true,
                    modelName = model.kind.displayName
                )
            }
        }
    }

    private fun buildSequences(
        input: RektTraceInput,
        events: List<StudyEvent>,
        contract: KtInputContract
    ): KtSequences {
        val ktEvents = events
            .filter { event ->
                event.type == StudyEventType.QUIZ_ANSWERED &&
                    event.correctness != null
            }
            .sortedBy { it.timestamp }
            .takeLast(contract.maxSequenceLength)
        if (ktEvents.isEmpty()) {
            return KtSequences.empty()
        }
        val skillIds = ktEvents.map { event ->
            conceptIdFor(event, input, contract)
        }.toLongArray()
        val problemIds = ktEvents.mapIndexed { index, event ->
            problemIdFor(
                event = event,
                input = input,
                contract = contract,
                fallbackSkillId = skillIds[index]
            )
        }.toLongArray()
        val answers = ktEvents.map {
            if (it.correctness == true) 1L else 0L
        }.toLongArray()
        val answerOffset = contract.answerOffset
            ?: (contract.questionCount + contract.indexBase)
        val interactions = problemIds.mapIndexed { index, problemId ->
            problemId + answers[index] * answerOffset
        }.toLongArray()
        val domainIds = ktEvents.map { event ->
            domainIdFor(event, input, contract)
        }.toLongArray()
        val masks = LongArray(ktEvents.size) { 1L }
        val confidence = (ktEvents.size / 8f)
            .coerceIn(0.2f, 1f)
        return KtSequences(
            problemIds = problemIds,
            skillIds = skillIds,
            answers = answers,
            interactions = interactions,
            domainIds = domainIds,
            masks = masks,
            confidence = confidence
        )
    }

    private fun conceptIdFor(
        event: StudyEvent,
        input: RektTraceInput,
        contract: KtInputContract
    ): Long {
        val raw = event.conceptIds.firstOrNull()
            ?: event.metadata.firstValue(
                "concept_id",
                "skill_id",
                "kc_id",
                "knowledge_component_id"
            )
            ?: input.keyId
        return normalizedId(
            raw = raw,
            explicitMap = contract.conceptIdMap,
            size = contract.conceptCount,
            indexBase = contract.indexBase
        )
    }

    private fun problemIdFor(
        event: StudyEvent,
        input: RektTraceInput,
        contract: KtInputContract,
        fallbackSkillId: Long
    ): Long {
        if (contract.questionAsConcept) {
            return fallbackSkillId
        }
        val raw = event.metadata.firstValue(
            "question_id",
            "problem_id",
            "item_id",
            "exercise_id"
        ) ?: event.id.ifBlank { input.keyId }
        return normalizedId(
            raw = raw,
            explicitMap = contract.questionIdMap,
            size = contract.questionCount,
            indexBase = contract.indexBase
        )
    }

    private fun domainIdFor(
        event: StudyEvent,
        input: RektTraceInput,
        contract: KtInputContract
    ): Long {
        val raw = event.metadata.firstValue(
            "domain_id",
            "coarse_id",
            "subject_id"
        ) ?: input.keyId
        return normalizedId(
            raw = raw,
            explicitMap = contract.domainIdMap,
            size = contract.domainCount,
            indexBase = contract.indexBase
        )
    }

    private fun Map<String, String>.firstValue(
        vararg keys: String
    ): String? {
        keys.forEach { key ->
            val value = this[key]
            if (!value.isNullOrBlank()) return value
        }
        val normalized = entries.associate {
            it.key.lowercase() to it.value
        }
        keys.forEach { key ->
            val value = normalized[key.lowercase()]
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun normalizedId(
        raw: String,
        explicitMap: Map<String, Int>,
        size: Int,
        indexBase: Int
    ): Long {
        explicitMap[raw]?.let { return it.toLong() }
        explicitMap[raw.lowercase()]?.let { return it.toLong() }
        val numeric = raw.trim().toIntOrNull()
            ?: Regex("-?\\d+").find(raw)?.value?.toIntOrNull()
        if (numeric != null) {
            if (indexBase == 0) {
                return numeric
                    .coerceIn(0, (size - 1).coerceAtLeast(0))
                    .toLong()
            }
            if (numeric in 1..size) return numeric.toLong()
            if (numeric in 0 until size) return (numeric + 1).toLong()
        }
        return if (indexBase == 0) {
            stablePositiveId(raw, size.coerceAtLeast(1))
        } else {
            1L + stablePositiveId(raw, size.coerceAtLeast(1))
        }
    }

    private fun valuesForInput(
        name: String,
        index: Int,
        totalInputs: Int,
        sequences: KtSequences,
        contract: KtInputContract,
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
                if (contract.questionAsConcept) {
                    sequences.skillIds
                } else {
                    sequences.problemIds
                }
            kind == KtModelKind.MIKT_STATICS2011 &&
                index == 1 && totalInputs <= 3 ->
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
        values: LongArray,
        maskValues: LongArray
    ): OnnxTensor {
        val shape = shapeFor(session, name, values.size)
        val elementCount = shape.fold(1L) { acc, dim ->
            acc * dim.coerceAtLeast(1L)
        }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val tensorValues = LongArray(elementCount)
        val source = if (name.lowercase().contains("mask")) {
            maskValues
        } else {
            values
        }
        source.take(elementCount).forEachIndexed { index, value ->
            tensorValues[index] = value
        }
        val type = (session.inputInfo[name]?.info as? TensorInfo)
            ?.type
        return when (type) {
            OnnxJavaType.INT32 -> OnnxTensor.createTensor(
                env,
                IntBuffer.wrap(tensorValues.map { it.toInt() }.toIntArray()),
                shape
            )
            OnnxJavaType.FLOAT -> OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(tensorValues.map { it.toFloat() }.toFloatArray()),
                shape
            )
            OnnxJavaType.DOUBLE -> OnnxTensor.createTensor(
                env,
                DoubleBuffer.wrap(tensorValues.map { it.toDouble() }.toDoubleArray()),
                shape
            )
            else -> OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tensorValues),
                shape
            )
        }
    }

    private fun shapeFor(
        session: OrtSession,
        name: String,
        sequenceLength: Int
    ): LongArray {
        val declaredShape = (session.inputInfo[name]?.info as? TensorInfo)
            ?.shape
        val rank = declaredShape
            ?.size
            ?: 2
        return when (rank) {
            0 -> longArrayOf(1L)
            1 -> longArrayOf(
                declaredShape?.getOrNull(0)
                    ?.takeIf { it > 0 }
                    ?: sequenceLength.toLong()
            )
            2 -> longArrayOf(
                declaredShape?.getOrNull(0)
                    ?.takeIf { it > 0 } ?: 1L,
                declaredShape?.getOrNull(1)
                    ?.takeIf { it > 0 }
                    ?: sequenceLength.toLong()
            )
            3 -> longArrayOf(
                declaredShape?.getOrNull(0)
                    ?.takeIf { it > 0 } ?: 1L,
                declaredShape?.getOrNull(1)
                    ?.takeIf { it > 0 }
                    ?: sequenceLength.toLong(),
                declaredShape?.getOrNull(2)
                    ?.takeIf { it > 0 } ?: 1L
            )
            else -> LongArray(rank) { index ->
                declaredShape?.getOrNull(index)
                    ?.takeIf { it > 0 }
                    ?: when (index) {
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
    val kind: KtModelKind,
    val contract: KtInputContract
)

private enum class KtModelKind(
    val logName: String,
    val displayName: String
) {
    MIKT_STATICS2011("mikt_statics2011_onnx", "MIKT Statics2011 ONNX"),
    GENERIC_KT("kt_onnx", "KT ONNX")
}

private data class KtSequences(
    val problemIds: LongArray,
    val skillIds: LongArray,
    val answers: LongArray,
    val interactions: LongArray,
    val domainIds: LongArray,
    val masks: LongArray,
    val confidence: Float
) {
    companion object {
        fun empty(): KtSequences = KtSequences(
            problemIds = longArrayOf(0L),
            skillIds = longArrayOf(0L),
            answers = longArrayOf(0L),
            interactions = longArrayOf(0L),
            domainIds = longArrayOf(0L),
            masks = longArrayOf(0L),
            confidence = 0f
        )
    }
}

@Serializable
private data class KtInputContract(
    val name: String = "statics2011",
    val dataset: String = "statics2011",
    @SerialName("concept_count")
    val conceptCount: Int = 85,
    @SerialName("question_count")
    val questionCount: Int = 85,
    @SerialName("domain_count")
    val domainCount: Int = 1,
    @SerialName("max_sequence_length")
    val maxSequenceLength: Int = 200,
    @SerialName("question_as_concept")
    val questionAsConcept: Boolean = true,
    @SerialName("index_base")
    val indexBase: Int = 1,
    @SerialName("answer_offset")
    val answerOffset: Int? = null,
    @SerialName("concept_id_map")
    val conceptIdMap: Map<String, Int> = emptyMap(),
    @SerialName("question_id_map")
    val questionIdMap: Map<String, Int> = emptyMap(),
    @SerialName("domain_id_map")
    val domainIdMap: Map<String, Int> = emptyMap()
) {
    fun normalizedForStatics2011(): KtInputContract =
        copy(
            name = name.ifBlank { "statics2011" },
            dataset = dataset.ifBlank { "statics2011" },
            conceptCount = conceptCount.coerceAtLeast(1),
            questionCount = questionCount.coerceAtLeast(1),
            domainCount = domainCount.coerceAtLeast(1),
            maxSequenceLength = maxSequenceLength.coerceAtLeast(1),
            indexBase = if (indexBase == 0) 0 else 1
        )

    companion object {
        fun statics2011(): KtInputContract =
            KtInputContract(
                name = "statics2011_default",
                dataset = "statics2011",
                conceptCount = 85,
                questionCount = 85,
                domainCount = 1,
                maxSequenceLength = 200,
                questionAsConcept = true,
                indexBase = 1,
                answerOffset = 86
            )

        fun generic(): KtInputContract =
            KtInputContract(
                name = "generic",
                dataset = "generic",
                conceptCount = 199,
                questionCount = 199,
                domainCount = 32,
                maxSequenceLength = 200,
                questionAsConcept = false,
                indexBase = 0,
                answerOffset = 199
            )
    }
}
