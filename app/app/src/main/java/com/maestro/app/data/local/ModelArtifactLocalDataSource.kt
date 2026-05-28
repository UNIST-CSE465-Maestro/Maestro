package com.maestro.app.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class ModelArtifactType(
    val fileName: String,
    val label: String,
    val legacyFileNames: List<String> = emptyList(),
    val profileUploadVisible: Boolean = false,
    val bundledUpdateEligible: Boolean = false
) {
    MIKT_ONNX(
        "mobile_mikt_predict.onnx",
        "mobile_mikt_predict.onnx",
        legacyFileNames = listOf("mikt_mobile.onnx"),
        profileUploadVisible = true
    ),
    MIKT_CONTRACT(
        "mikt_predict_contract.json",
        "mikt_predict_contract.json",
        legacyFileNames = listOf("mikt_contract.json"),
        profileUploadVisible = true
    ),
    MIKT_QUESTION_ID_MAP(
        "question_id_map.json",
        "Question ID Map JSON",
        bundledUpdateEligible = true
    ),
    MIKT_CONCEPT_ID_MAP(
        "concept_id_map.json",
        "concept_id_map.json",
        bundledUpdateEligible = true
    ),
    MIKT_KC_MAPPING_CONTRACT(
        "kc_mapping_contract.json",
        "kc_mapping_contract.json",
        legacyFileNames = listOf("concept_catalog.json"),
        bundledUpdateEligible = true
    ),
    MIKT_QUESTION_TO_CONCEPT(
        "question_to_concept.json",
        "Question-to-Concept JSON",
        bundledUpdateEligible = true
    ),
    MIKT_QUESTION_DIFFICULTY(
        "question_difficulty.json",
        "Question Difficulty JSON",
        bundledUpdateEligible = true
    ),
    MIKT_STATICS2011_MAPPING(
        "mikt_statics2011_mapping.json",
        "Legacy Statics2011 KT Mapping JSON",
        bundledUpdateEligible = true
    ),
    MIKT_EXPORT_VALIDATION(
        "export_validation.json",
        "export_validation.json",
        bundledUpdateEligible = true
    ),
    MIKT_EVALUATION_REPORT(
        "evaluation_report.json",
        "evaluation_report.json",
        bundledUpdateEligible = true
    ),
    QE_SERVER_API_CONTRACT(
        "qe_server_api_contract.json",
        "qe_server_api_contract.json",
        bundledUpdateEligible = true
    ),
    QE_MIKT_COMPATIBILITY(
        "qe_mikt_compatibility.json",
        "qe_mikt_compatibility.json",
        bundledUpdateEligible = true
    ),
    CONCEPT_ONNX("concept_model.onnx", "Engineering Mechanics Concept ONNX")
}

@Serializable
data class ModelArtifactState(
    val type: ModelArtifactType,
    val label: String,
    val filePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val updatedAt: Long? = null
) {
    val isReady: Boolean = !filePath.isNullOrBlank() &&
        fileSizeBytes > 0L
}

class ModelArtifactLocalDataSource(
    private val context: Context
) {
    private val modelDir =
        File(context.filesDir, "models").also { it.mkdirs() }
    init {
        syncBundledArtifacts()
    }

    private val _states = MutableStateFlow(loadStates())
    val states: StateFlow<List<ModelArtifactState>> =
        _states.asStateFlow()

    fun getState(type: ModelArtifactType): ModelArtifactState =
        stateFor(type)

    fun getModelFile(type: ModelArtifactType): File? {
        return candidateFiles(type)
            .firstOrNull { it.exists() && it.length() > 0L }
    }

    fun saveModel(
        type: ModelArtifactType,
        sourceUri: Uri
    ): ModelArtifactState {
        val out = File(modelDir, type.fileName)
        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) {
                "Unable to open selected model file"
            }
            out.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val state = stateFor(type)
        _states.value = loadStates()
        return state
    }

    fun deleteModel(type: ModelArtifactType) {
        candidateFiles(type).forEach { it.delete() }
        _states.value = loadStates()
    }

    private fun loadStates(): List<ModelArtifactState> =
        ModelArtifactType.entries.map { stateFor(it) }

    private fun stateFor(
        type: ModelArtifactType
    ): ModelArtifactState {
        val file = candidateFiles(type)
            .firstOrNull { it.exists() && it.length() > 0L }
        return ModelArtifactState(
            type = type,
            label = type.label,
            filePath = file?.absolutePath,
            fileSizeBytes = file?.length() ?: 0L,
            updatedAt = file?.lastModified()
        )
    }

    private fun candidateFiles(type: ModelArtifactType): List<File> =
        (listOf(type.fileName) + type.legacyFileNames)
            .map { File(modelDir, it) }

    private fun syncBundledArtifacts() {
        ModelArtifactType.entries
            .filter { it.bundledUpdateEligible }
            .forEach { syncBundledArtifact(it) }
    }

    private fun syncBundledArtifact(type: ModelArtifactType) {
        val assetPath = "$BUNDLED_ARTIFACT_DIR/${type.fileName}"
        val out = File(modelDir, type.fileName)
        val tmp = File(modelDir, "${type.fileName}.tmp")
        try {
            context.assets.open(assetPath).use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!out.exists() || out.length() != tmp.length()) {
                if (out.exists()) out.delete()
                tmp.renameTo(out)
            } else {
                tmp.delete()
            }
        } catch (_: Throwable) {
            tmp.delete()
        }
    }

    companion object {
        const val BUNDLED_ARTIFACT_DIR = "model_artifacts"
    }
}
