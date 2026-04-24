package com.maestro.app.domain.model

enum class DrawingTool { PEN, ERASER, LASSO, CROP_CAPTURE }
enum class EraserMode { STROKE, PARTIAL }
enum class LassoPhase { IDLE, DRAWING, SELECTED, DRAGGING }
enum class CropCapturePhase { IDLE, DRAWING, ADJUSTING }
