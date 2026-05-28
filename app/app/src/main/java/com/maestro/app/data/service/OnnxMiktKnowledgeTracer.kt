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
import com.maestro.app.domain.service.MiktKnowledgeTracer
import com.maestro.app.domain.service.MiktTraceInput
import java.io.File
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.abs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OnnxMiktKnowledgeTracer(
    private val context: Context,
    private val modelArtifacts: ModelArtifactLocalDataSource,
    private val monitoringLogs: MonitoringLogLocalDataSource,
    private val resourceSampler: DeviceResourceSampler,
    private val fallback: MiktKnowledgeTracer = HeuristicKnowledgeTracer()
) : MiktKnowledgeTracer {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun trace(inputs: List<MiktTraceInput>): Map<String, KnowledgeTraceResult> {
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
                "raw_sequence_window" to model.contract
                    .rawSequenceLength.toString(),
                "input_sequence_length" to model.contract
                    .inputSequenceLength.toString(),
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
        return null
    }

    private fun loadStatics2011Contract(): KtInputContract {
        val base = modelArtifacts.getModelFile(
            ModelArtifactType.MIKT_CONTRACT
        )?.let { file ->
            try {
                json.decodeFromString<KtInputContract>(
                    file.readText()
                )
            } catch (_: Throwable) {
                null
            }
        } ?: modelArtifacts.getModelFile(
            ModelArtifactType.MIKT_STATICS2011_MAPPING
        )?.let { file ->
            try {
                json.decodeFromString<KtInputContract>(
                    file.readText()
                )
            } catch (_: Throwable) {
                null
            }
        } ?: KtInputContract.statics2011()
        return try {
            base.copy(
                questionIdMap =
                loadIdMap(ModelArtifactType.MIKT_QUESTION_ID_MAP)
                    .ifEmpty { base.questionIdMap },
                conceptIdMap =
                loadIdMap(ModelArtifactType.MIKT_CONCEPT_ID_MAP)
                    .ifEmpty { base.conceptIdMap },
                questionToConcept =
                loadQuestionToConcept()
                    .ifEmpty { base.questionToConcept },
                questionDifficulty =
                loadFloatMap(ModelArtifactType.MIKT_QUESTION_DIFFICULTY)
                    .ifEmpty { base.questionDifficulty }
            ).normalizedForStatics2011()
        } catch (_: Throwable) {
            KtInputContract.statics2011()
        }
    }

    private fun loadIdMap(
        type: ModelArtifactType
    ): Map<String, Int> {
        val file = modelArtifacts.getModelFile(type)
            ?: return emptyMap()
        return try {
            val root = json.parseToJsonElement(
                file.readText()
            ).jsonObject
            root.mapNotNull { (key, value) ->
                val mapped = when (value) {
                    is JsonPrimitive -> value.intOrNull
                    is JsonObject -> value["id"]
                        ?.jsonPrimitive
                        ?.intOrNull
                    else -> null
                }
                mapped?.let { key to it }
            }.toMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun loadFloatMap(
        type: ModelArtifactType
    ): Map<String, Float> {
        val file = modelArtifacts.getModelFile(type)
            ?: return emptyMap()
        return try {
            val root = json.parseToJsonElement(
                file.readText()
            ).jsonObject
            root.mapNotNull { (key, value) ->
                val mapped = when (value) {
                    is JsonPrimitive -> value.floatOrNull
                    is JsonObject -> value["difficulty"]
                        ?.jsonPrimitive
                        ?.floatOrNull
                        ?: value["diff"]
                            ?.jsonPrimitive
                            ?.floatOrNull
                    else -> null
                }
                mapped?.let { key to it }
            }.toMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun loadQuestionToConcept(): Map<String, List<Int>> {
        val file = modelArtifacts.getModelFile(
            ModelArtifactType.MIKT_QUESTION_TO_CONCEPT
        ) ?: return emptyMap()
        return try {
            val root = json.parseToJsonElement(
                file.readText()
            ).jsonObject
            root.mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> listOfNotNull(value.intOrNull)
                    is kotlinx.serialization.json.JsonArray ->
                        value.mapNotNull {
                            it.jsonPrimitive.intOrNull
                        }
                    is JsonObject -> {
                        val ids = value["concept_ids"]
                            ?: value["concepts"]
                            ?: value["skills"]
                        when (ids) {
                            is kotlinx.serialization.json.JsonArray ->
                                ids.mapNotNull {
                                    it.jsonPrimitive.intOrNull
                                }
                            is JsonPrimitive ->
                                listOfNotNull(ids.intOrNull)
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }.filterValues { it.isNotEmpty() }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun runOnnx(
        model: OnnxKtModel,
        inputs: List<MiktTraceInput>
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
        input: MiktTraceInput,
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
                val mastery = extractPrediction(
                    result = result,
                    session = session,
                    sequences = sequences,
                    contract = model.contract
                )
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
        input: MiktTraceInput,
        events: List<StudyEvent>,
        contract: KtInputContract
    ): KtSequences {
        val ktEvents = events
            .filter { event ->
                event.type == StudyEventType.QUIZ_ANSWERED &&
                    event.correctness != null
            }
            .sortedBy { it.timestamp }
            .takeLast(contract.rawSequenceLength)
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
        val shifted = buildMobileMiktShiftedInputs(
            problemIds = problemIds,
            answers = answers,
            contract = contract
        )
        return KtSequences(
            problemIds = problemIds,
            skillIds = skillIds,
            answers = answers,
            interactions = interactions,
            domainIds = domainIds,
            masks = masks,
            lastProblems = shifted.lastProblems,
            lastAnswers = shifted.lastAnswers,
            nextProblems = shifted.nextProblems,
            nextAnswers = shifted.nextAnswers,
            lastValidIndex = shifted.lastValidIndex,
            confidence = confidence
        )
    }

    private fun buildMobileMiktShiftedInputs(
        problemIds: LongArray,
        answers: LongArray,
        contract: KtInputContract
    ): ShiftedMiktInputs {
        val inputLength = contract.inputSequenceLength
            .coerceAtLeast(1)
        val rawLength = maxOf(
            contract.rawSequenceLength,
            inputLength + 1
        )
        val useProblem = LongArray(rawLength)
        val useAnswer = LongArray(rawLength)
        val count = minOf(problemIds.size, rawLength)
        val start = rawLength - count
        for (i in 0 until count) {
            useProblem[start + i] =
                problemIds[problemIds.size - count + i]
            useAnswer[start + i] =
                answers[answers.size - count + i]
        }
        val lastStart = (rawLength - 1 - inputLength)
            .coerceAtLeast(0)
        val nextStart = (rawLength - inputLength)
            .coerceAtLeast(0)
        val lastProblems = useProblem
            .copyOfRange(lastStart, rawLength - 1)
            .fitLength(inputLength)
        val lastAnswers = useAnswer
            .copyOfRange(lastStart, rawLength - 1)
            .fitLength(inputLength)
        val nextProblems = useProblem
            .copyOfRange(nextStart, rawLength)
            .fitLength(inputLength)
        val nextAnswers = useAnswer
            .copyOfRange(nextStart, rawLength)
            .fitLength(inputLength)
        return ShiftedMiktInputs(
            lastProblems = lastProblems,
            lastAnswers = lastAnswers,
            nextProblems = nextProblems,
            nextAnswers = nextAnswers,
            lastValidIndex = inputLength - 1
        )
    }

    private fun LongArray.fitLength(length: Int): LongArray {
        if (size == length) return this
        val result = LongArray(length)
        val copyCount = minOf(size, length)
        copyInto(
            destination = result,
            destinationOffset = length - copyCount,
            startIndex = size - copyCount,
            endIndex = size
        )
        return result
    }

    private fun conceptIdFor(
        event: StudyEvent,
        input: MiktTraceInput,
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
        input: MiktTraceInput,
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
        input: MiktTraceInput,
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
            normalized == "lastproblem" ||
                normalized == "lastq" ||
                normalized == "lastquestion" ->
                sequences.lastProblems
            normalized == "lastans" ||
                normalized == "lastanswer" ||
                normalized == "lasta" ->
                sequences.lastAnswers
            normalized == "nextproblem" ||
                normalized == "nextq" ||
                normalized == "nextquestion" ->
                sequences.nextProblems
            normalized == "nextans" ||
                normalized == "nextanswer" ||
                normalized == "nexta" ->
                sequences.nextAnswers
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

    private fun extractPrediction(
        result: OrtSession.Result,
        session: OrtSession,
        sequences: KtSequences,
        contract: KtInputContract
    ): Float {
        val outputNames = session.outputNames.toList()
        val lastIndex = outputNames.indexOf(
            contract.lastPredictionOutput
        )
        if (lastIndex >= 0) {
            return extractMastery(result.get(lastIndex).value)
        }
        val sequenceIndex = outputNames.indexOf(
            contract.predictionSequenceOutput
        )
        if (sequenceIndex >= 0) {
            return extractAtSequenceIndex(
                result.get(sequenceIndex).value,
                sequences.lastValidIndex
            )
        }
        return extractAtSequenceIndex(
            result.get(0).value,
            sequences.lastValidIndex
        )
    }

    private fun extractAtSequenceIndex(
        value: Any?,
        index: Int
    ): Float {
        return when (value) {
            is Array<*> -> {
                val first = value.firstOrNull()
                when (first) {
                    is FloatArray -> first
                        .getOrNull(index) ?: first.lastOrNull() ?: 0f
                    is DoubleArray -> first
                        .getOrNull(index)
                        ?.toFloat()
                        ?: first.lastOrNull()?.toFloat()
                        ?: 0f
                    is Array<*> -> extractAtSequenceIndex(first, index)
                    else -> extractMastery(value)
                }
            }
            is FloatArray -> value
                .getOrNull(index) ?: value.lastOrNull() ?: 0f
            is DoubleArray -> value
                .getOrNull(index)
                ?.toFloat()
                ?: value.lastOrNull()?.toFloat()
                ?: 0f
            else -> extractMastery(value)
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
    MIKT_STATICS2011("mikt_statics2011_onnx", "MIKT Statics2011 ONNX")
}

private data class KtSequences(
    val problemIds: LongArray,
    val skillIds: LongArray,
    val answers: LongArray,
    val interactions: LongArray,
    val domainIds: LongArray,
    val masks: LongArray,
    val lastProblems: LongArray,
    val lastAnswers: LongArray,
    val nextProblems: LongArray,
    val nextAnswers: LongArray,
    val lastValidIndex: Int,
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
            lastProblems = LongArray(199),
            lastAnswers = LongArray(199),
            nextProblems = LongArray(199),
            nextAnswers = LongArray(199),
            lastValidIndex = 198,
            confidence = 0f
        )
    }
}

private data class ShiftedMiktInputs(
    val lastProblems: LongArray,
    val lastAnswers: LongArray,
    val nextProblems: LongArray,
    val nextAnswers: LongArray,
    val lastValidIndex: Int
)

@Serializable
private data class KtInputContract(
    @SerialName("model_name")
    val modelName: String = "MobileMIKT",
    val name: String = "statics2011",
    val dataset: String = "statics2011",
    @SerialName("kc_model")
    val kcModel: String = "F2011",
    @SerialName("concept_count")
    val conceptCount: Int = 85,
    @SerialName("question_count")
    val questionCount: Int = 85,
    @SerialName("domain_count")
    val domainCount: Int = 1,
    @SerialName("max_raw_sequence_length")
    val maxRawSequenceLength: Int? = null,
    @SerialName("max_sequence_length")
    val maxSequenceLength: Int = 200,
    @SerialName("input_sequence_length")
    val inputSequenceLength: Int = 199,
    @SerialName("question_as_concept")
    val questionAsConcept: Boolean = true,
    @SerialName("index_base")
    val indexBase: Int = 1,
    @SerialName("answer_offset")
    val answerOffset: Int? = null,
    @SerialName("prediction_sequence_output")
    val predictionSequenceOutput: String = "prediction_sequence",
    @SerialName("last_prediction_output")
    val lastPredictionOutput: String = "last_prediction",
    @SerialName("concept_id_map")
    val conceptIdMap: Map<String, Int> = emptyMap(),
    @SerialName("question_id_map")
    val questionIdMap: Map<String, Int> = emptyMap(),
    @SerialName("domain_id_map")
    val domainIdMap: Map<String, Int> = emptyMap(),
    @SerialName("question_to_concept")
    val questionToConcept: Map<String, List<Int>> = emptyMap(),
    @SerialName("question_difficulty")
    val questionDifficulty: Map<String, Float> = emptyMap()
) {
    val rawSequenceLength: Int
        get() = (maxRawSequenceLength ?: maxSequenceLength)
            .coerceAtLeast(inputSequenceLength + 1)

    fun normalizedForStatics2011(): KtInputContract =
        copy(
            name = name.ifBlank { "statics2011" },
            dataset = dataset.ifBlank { "statics2011" },
            kcModel = kcModel.ifBlank { "F2011" },
            conceptCount = conceptCount.coerceAtLeast(1),
            questionCount = questionCount.coerceAtLeast(1),
            domainCount = domainCount.coerceAtLeast(1),
            maxSequenceLength = maxSequenceLength.coerceAtLeast(1),
            maxRawSequenceLength = rawSequenceLength,
            inputSequenceLength = inputSequenceLength.coerceAtLeast(1),
            predictionSequenceOutput =
            predictionSequenceOutput.ifBlank {
                "prediction_sequence"
            },
            lastPredictionOutput =
            lastPredictionOutput.ifBlank {
                "last_prediction"
            },
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
                maxRawSequenceLength = 200,
                maxSequenceLength = 200,
                inputSequenceLength = 199,
                questionAsConcept = true,
                indexBase = 1,
                answerOffset = 86,
                predictionSequenceOutput = "prediction_sequence",
                lastPredictionOutput = "last_prediction"
            )
    }
}
