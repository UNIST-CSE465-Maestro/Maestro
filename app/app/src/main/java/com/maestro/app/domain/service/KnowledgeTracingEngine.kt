package com.maestro.app.domain.service

/**
 * Operational mastery for a single statics2011 concept, read out from the
 * bundled MobileKT v4 TAP head.
 */
data class ConceptMastery(
    val conceptId: Int,
    val conceptKey: String,
    val name: String,
    val mastery: Float,
    val seenCount: Int
)

/**
 * On-device knowledge-tracing engine backed by the bundled MobileKT v4
 * stateful ONNX runtime (MIKT update + TAP readout).
 *
 * Knowledge state is derived **purely** from the model output: after each
 * answered question the MIKT state is updated and the TAP head reads calibrated
 * per-concept mastery. No event/prediction blending is applied.
 */
interface KnowledgeTracingEngine {
    /**
     * True when the required ONNX models (`mobile_mikt_update.onnx` and
     * `mobile_tap_readout.onnx`) are bundled and loadable. When false the
     * knowledge dashboard should show a "추적 불가 (모델 필요)" state.
     */
    fun isReady(): Boolean

    /**
     * Runs `mobile_mikt_update.onnx` with the answered question's QE
     * representation, updates TAP profile statistics for every related concept,
     * and persists the new state. No-op when [isReady] is false.
     */
    fun recordAnswer(
        questionEmbedding: FloatArray,
        difficulty: Float,
        conceptIds: List<Int>,
        correct: Boolean
    )

    /**
     * Runs `mobile_tap_readout.onnx` against the persisted state and returns
     * operational mastery keyed by statics2011 concept key (snake id). Empty
     * when [isReady] is false.
     */
    fun masteryByConceptKey(): Map<String, ConceptMastery>

    /** Resets local knowledge state to the bundled initial state. */
    fun reset()
}
