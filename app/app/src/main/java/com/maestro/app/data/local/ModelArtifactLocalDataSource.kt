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
    val label: String
) {
    KT_ONNX("kt_model.onnx", "KT ONNX"),
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
    private val _states = MutableStateFlow(loadStates())
    val states: StateFlow<List<ModelArtifactState>> =
        _states.asStateFlow()

    fun getState(type: ModelArtifactType): ModelArtifactState =
        stateFor(type)

    fun getModelFile(type: ModelArtifactType): File? {
        val file = File(modelDir, type.fileName)
        return file.takeIf { it.exists() && it.length() > 0L }
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
        File(modelDir, type.fileName).delete()
        _states.value = loadStates()
    }

    private fun loadStates(): List<ModelArtifactState> =
        ModelArtifactType.entries.map { stateFor(it) }

    private fun stateFor(
        type: ModelArtifactType
    ): ModelArtifactState {
        val file = File(modelDir, type.fileName)
        return ModelArtifactState(
            type = type,
            label = type.label,
            filePath = file.takeIf {
                it.exists() && it.length() > 0L
            }?.absolutePath,
            fileSizeBytes = file.takeIf { it.exists() }
                ?.length() ?: 0L,
            updatedAt = file.takeIf {
                it.exists() && it.length() > 0L
            }?.lastModified()
        )
    }
}
