package com.maestro.app.domain.model

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val isStylusInput: Boolean = false
)
