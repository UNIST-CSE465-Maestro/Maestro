package com.maestro.app.domain.model

import androidx.compose.ui.graphics.Color

data class InkStroke(
    val points: List<StrokePoint>,
    val color: Color,
    val baseWidth: Float
)
