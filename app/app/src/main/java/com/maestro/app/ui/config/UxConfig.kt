package com.maestro.app.ui.config

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central configuration for all UX-affecting values.
 * Changing a value here updates it everywhere in the app.
 */
object UxConfig {

    // ── Gesture thresholds ───────────────────────────

    object Gesture {
        /** Home grid long-press detection (ms) */
        const val LONG_PRESS_HOME_MS = 200L

        /** Stylus canvas long-press for paste (ms) */
        const val LONG_PRESS_CANVAS_MS = 500L

        /** Minimum drag distance to count as "moved" (px) */
        const val DRAG_THRESHOLD_PX = 20f

        /** Long-press cancel distance squared (px^2) */
        const val LONG_PRESS_CANCEL_DIST_SQ = 30f * 30f
    }

    // ── Drawing tools ────────────────────────────────

    object Drawing {
        val PEN_WIDTHS = listOf(1.5f, 3f, 5f, 8f)
        val ERASER_WIDTHS = listOf(10f, 20f, 35f, 50f)

        /** Default pen width index into PEN_WIDTHS */
        const val DEFAULT_PEN_WIDTH_INDEX = 1

        /** Default eraser width index into ERASER_WIDTHS */
        const val DEFAULT_ERASER_WIDTH_INDEX = 1

        /** Pressure coercion range */
        const val PRESSURE_MIN = 0.05f
        const val PRESSURE_MAX = 1f

        /** Stroke width = baseWidth * (PRESSURE_BASE + PRESSURE_MULT * pressure) */
        const val PRESSURE_BASE = 0.4f
        const val PRESSURE_MULT = 1.2f

        /** Lasso path stroke width */
        const val LASSO_STROKE_WIDTH = 2.5f

        /** Lasso dash pattern */
        const val LASSO_DASH = 12f
        const val LASSO_GAP = 8f

        /** Lasso closing line */
        const val LASSO_CLOSE_STROKE_WIDTH = 1.5f
        const val LASSO_CLOSE_DASH = 6f
        const val LASSO_CLOSE_GAP = 6f

        /** Selection box */
        const val SELECTION_STROKE_WIDTH = 2f
        const val SELECTION_DASH = 10f
        const val SELECTION_GAP = 6f
        const val SELECTION_FILL_ALPHA = 0.05f
        const val SELECTION_BOUNDS_PADDING = 20f

        /** Eraser indicator */
        const val ERASER_FILL_ALPHA = 0.3f
        const val ERASER_OUTLINE_ALPHA = 0.6f
        const val ERASER_OUTLINE_STROKE_WIDTH = 1.5f
    }

    // ── Canvas & zoom ────────────────────────────────

    object Canvas {
        const val ZOOM_MIN = 1f
        const val ZOOM_MAX = 5f

        /** Threshold for showing zoom indicator */
        const val ZOOM_INDICATOR_THRESHOLD = 1.01f

        /** Pan detection */
        const val PAN_THRESHOLD_MULT = 1.5f
        const val PAN_MIN_DISTANCE = 3f

        /** PDF rendering max bitmap dimension (px) */
        const val PDF_MAX_RENDER_DIM = 2000

        /** Default scale for pages smaller than max dim */
        const val PDF_DEFAULT_SCALE = 2f

        /** Page layout */
        val PAGE_VERTICAL_PADDING = 8.dp
        val PAGE_VERTICAL_SPACING = 8.dp
        val PAGE_MIN_HORIZONTAL_PADDING = 4.dp
        val PAGE_DEFAULT_HORIZONTAL_PADDING = 4.dp
        val PAGE_SHADOW_ELEVATION = 6.dp
        val PAGE_CORNER_RADIUS = 2.dp
        val PLACEHOLDER_PADDING = 32.dp

        /** Zoom indicator style */
        const val ZOOM_INDICATOR_BG_ALPHA = 0.6f
        val ZOOM_INDICATOR_CORNER = 6.dp
        val ZOOM_INDICATOR_PADDING_H = 8.dp
        val ZOOM_INDICATOR_PADDING_V = 4.dp

        /** A4 portrait aspect ratio */
        const val A4_ASPECT_RATIO = 0.707f
    }

    // ── Crop mode ────────────────────────────────────

    object Crop {
        const val INITIAL_TOP_LEFT_X = 100f
        const val INITIAL_TOP_LEFT_Y = 100f
        const val INITIAL_BOTTOM_RIGHT_X = 400f
        const val INITIAL_BOTTOM_RIGHT_Y = 400f
        const val INITIAL_WIDTH = 150f
        const val INITIAL_HEIGHT = 100f

        /** Corner drag activation distance (px) */
        const val CORNER_DRAG_DISTANCE = 60f
        const val MIN_CORNER_GAP = 30f
        const val CORNER_HANDLE_RADIUS = 10f
        const val CORNER_HANDLE_STROKE = 2.5f
        const val BORDER_STROKE_WIDTH = 3f
        const val OVERLAY_DIM_ALPHA = 0.4f

        /** Crop button */
        const val BUTTON_SIZE = 44f
        const val BUTTON_OFFSET_Y = 10f
        const val BUTTON_CORNER_RADIUS = 10f

        /** Copy icon */
        const val COPY_ICON_SIZE = 8f
        const val COPY_ICON_SCALE = 1.6f
        const val COPY_ICON_STROKE = 2f

        /** Cancel button */
        const val CANCEL_OFFSET_X = 8f
        const val CANCEL_BG_ALPHA = 0.8f
        const val CANCEL_ICON_SIZE = 9f
        const val CANCEL_ICON_STROKE = 2.5f
    }

    // ── Image overlay ────────────────────────────────

    object ImageOverlay {
        const val RESIZE_HANDLE_RADIUS = 12f
        const val RESIZE_HANDLE_HIT_DIST_SQ = 50f * 50f
        const val MIN_SIZE = 50f

        /** Buttons above overlay */
        const val BUTTON_SIZE = 44f
        const val BUTTON_GAP = 8f
        const val BUTTON_OFFSET_Y = 10f
        const val BUTTON_CORNER_RADIUS = 10f

        /** Delete icon */
        const val DELETE_ICON_SIZE = 9f
        const val DELETE_ICON_STROKE = 2.5f

        /** Copy icon */
        const val COPY_ICON_SIZE = 7f
        const val COPY_ICON_SCALE = 1.6f
        const val COPY_ICON_STROKE = 2f

        /** Cut icon */
        const val CUT_CIRCLE_RADIUS = 4f
        const val CUT_CIRCLE_STROKE = 1.8f
        const val CUT_STROKE_WIDTH = 2.5f
    }

    // ── Selection buttons ────────────────────────────

    object Selection {
        const val BUTTON_SIZE = 48f
        const val BUTTON_GAP = 10f
        const val BUTTON_OFFSET_Y = 10f
        const val BUTTON_CORNER_RADIUS = 10f

        /** Delete icon */
        const val DELETE_ICON_SIZE = 10f
        const val DELETE_ICON_STROKE = 3f

        /** Copy icon */
        const val COPY_ICON_SIZE = 8f
        const val COPY_ICON_SCALE = 1.7f
        const val COPY_ICON_STROKE = 2.2f

        /** Cut icon */
        const val CUT_CIRCLE_RADIUS = 4.5f
        const val CUT_CIRCLE_STROKE = 2f
        const val CUT_STROKE_WIDTH = 2.5f

        /** LLM / AI button */
        const val LLM_BUTTON_SIZE = 48f
        const val LLM_TEXT_SIZE = 16f
    }

    // ── Save & timing ────────────────────────────────

    object Timing {
        /** Annotation auto-save debounce (ms) */
        const val AUTOSAVE_DEBOUNCE_MS = 400L
    }

    // ── Animation ────────────────────────────────────

    object Animation {
        /** Loading dots */
        const val LOADING_DOT_INITIAL_ALPHA = 0.3f
        const val LOADING_DOT_TARGET_ALPHA = 1f
        const val LOADING_DOT_DURATION_MS = 600
        const val LOADING_DOT_DELAY_MS = 200
        val LOADING_DOT_SIZE = 8.dp
        val LOADING_DOT_SPACING = 6.dp
    }

    // ── Top app bar ──────────────────────────────────

    object TopBar {
        val HEIGHT = 56.dp
        val HORIZONTAL_PADDING = 4.dp
        val ICON_BUTTON_SIZE = 40.dp
        val DIVIDER_WIDTH = 1.dp
        val DIVIDER_HEIGHT = 28.dp
        val SEARCH_ICON_SIZE = 14.dp
        val SEARCH_FIELD_HEIGHT = 36.dp
        val SEARCH_FIELD_CORNER = 8.dp
        const val SEARCH_PLACEHOLDER_ALPHA = 0.7f
        val QUIZ_BUTTON_CORNER = 8.dp
        val QUIZ_BUTTON_ELEVATION = 4.dp
        val QUIZ_BUTTON_PADDING_H = 16.dp
        val QUIZ_BUTTON_PADDING_V = 8.dp
        const val GRADIENT_END = 200f
    }

    // ── Floating toolbar ─────────────────────────────

    object Toolbar {
        val BUTTON_SIZE = 40.dp
        val BUTTON_SPACING = 2.dp
        val POPUP_OFFSET_Y = 4.dp
        val POPUP_CORNER = 16.dp
        val POPUP_PADDING = 16.dp
        val POPUP_MIN_WIDTH = 240.dp

        /** Color swatch */
        val COLOR_SWATCH_SIZE = 28.dp
        val COLOR_SWATCH_SPACING = 10.dp
        val COLOR_BORDER_SELECTED = 3.dp
        val COLOR_BORDER_UNSELECTED = 1.dp
        const val COLOR_BORDER_UNSELECTED_ALPHA = 0.4f

        /** Width preview */
        val WIDTH_CIRCLE_SIZE = 36.dp
        val WIDTH_CIRCLE_SPACING = 10.dp
        val WIDTH_BORDER_SELECTED = 2.dp

        /** Pen width preview scale (circle diameter = width * this) */
        const val PEN_PREVIEW_SCALE = 2.5f

        /** Preview bar */
        val PREVIEW_BAR_HEIGHT = 20.dp
        const val PREVIEW_BAR_WIDTH_FRACTION = 0.7f

        /** Eraser */
        val ERASER_CIRCLE_SIZE = 40.dp
        val ERASER_BORDER_SELECTED = 2.dp
        val ERASER_BORDER_UNSELECTED = 1.dp
        const val ERASER_BORDER_UNSELECTED_ALPHA = 0.3f

        /** Eraser size preview scale */
        const val ERASER_PREVIEW_SCALE = 0.45f

        /** Eraser mode chips */
        val CHIP_CORNER = 9999.dp
        val CHIP_PADDING_H = 14.dp
        val CHIP_PADDING_V = 6.dp
        val CHIP_SPACING = 8.dp

        /** Stylus indicator */
        val STYLUS_DOT_SIZE = 5.dp
        val STYLUS_PADDING_H = 8.dp
        val STYLUS_PADDING_V = 3.dp
        val STYLUS_SPACING = 4.dp
        val STYLUS_FONT_SIZE: TextUnit = 9.sp
    }

    // ── Home screen ──────────────────────────────────

    object Home {
        /** Top bar */
        val TOP_BAR_HEIGHT = 72.dp
        val TOP_BAR_PADDING_H = 12.dp
        val LOGO_FONT_SIZE: TextUnit = 24.sp
        val LOGO_LETTER_SPACING: TextUnit = (-0.5).sp
        val FOLDER_NAME_FONT_SIZE: TextUnit = 20.sp
        val SUBTITLE_FONT_SIZE: TextUnit = 13.sp

        /** Grid */
        val GRID_MIN_SIZE = 160.dp
        val GRID_PADDING_H = 20.dp
        val GRID_PADDING_TOP = 16.dp
        val GRID_PADDING_BOTTOM = 100.dp
        val GRID_SPACING_H = 16.dp
        val GRID_SPACING_V = 16.dp

        /** Grid items */
        const val ITEM_ASPECT_RATIO = 0.707f
        val ITEM_CORNER = 12.dp
        val ITEM_SHADOW = 4.dp
        val ITEM_SHADOW_DROP_TARGET = 8.dp
        val ITEM_BORDER_DROP_TARGET = 3.dp
        val ITEM_PADDING = 12.dp
        val ITEM_NAME_FONT_SIZE: TextUnit = 13.sp
        val ITEM_NAME_LINE_HEIGHT: TextUnit = 18.sp
        val ITEM_META_FONT_SIZE: TextUnit = 11.sp
        val FOLDER_ICON_SIZE = 56.dp
        const val FOLDER_ICON_ALPHA = 0.7f
        const val FOLDER_ICON_ALPHA_DROP = 1f
        const val DROP_TARGET_BG_ALPHA = 0.1f
        val PDF_PLACEHOLDER_ICON_SIZE = 48.dp

        /** Drag indicator */
        val DRAG_SHADOW = 12.dp
        val DRAG_CORNER = 12.dp
        val DRAG_PADDING_H = 14.dp
        val DRAG_PADDING_V = 10.dp
        val DRAG_ICON_SPACING = 8.dp
        val DRAG_ICON_SIZE = 20.dp
        val DRAG_TEXT_SIZE: TextUnit = 12.sp

        /** FAB */
        val FAB_PADDING = 24.dp
        val FAB_ELEVATION = 8.dp
        val FAB_ICON_SIZE = 28.dp
        val FAB_MENU_CORNER = 12.dp
        val FAB_MENU_FONT_SIZE: TextUnit = 14.sp

        /** Empty state */
        val EMPTY_ICON_SIZE = 72.dp
        val EMPTY_TITLE_FONT_SIZE: TextUnit = 18.sp
        val EMPTY_DESC_FONT_SIZE: TextUnit = 14.sp
        val EMPTY_DESC_LINE_HEIGHT: TextUnit = 22.sp

        /** Dialogs */
        val DIALOG_TEXT_FIELD_CORNER = 12.dp
        val DIALOG_LOG_MAX_HEIGHT = 400.dp
        val DIALOG_LOG_FONT_SIZE: TextUnit = 9.sp
        val DIALOG_LOG_LINE_HEIGHT: TextUnit = 13.sp

        /** Move dialog */
        val MOVE_DIALOG_MIN_HEIGHT = 300.dp
        val MOVE_DIALOG_MAX_HEIGHT = 500.dp
        val MOVE_DIALOG_TITLE_FONT_SIZE: TextUnit = 16.sp
        val MOVE_DIALOG_BREADCRUMB_FONT_SIZE: TextUnit = 12.sp
        val MOVE_DIALOG_EMPTY_HEIGHT = 120.dp
        val MOVE_DIALOG_EMPTY_ICON_SIZE = 32.dp
        val MOVE_DIALOG_EMPTY_TITLE_FONT_SIZE: TextUnit = 13.sp
        val MOVE_DIALOG_EMPTY_DESC_FONT_SIZE: TextUnit = 11.sp
        val MOVE_DIALOG_ROW_CORNER = 8.dp
        val MOVE_DIALOG_ROW_PADDING_H = 12.dp
        val MOVE_DIALOG_ROW_PADDING_V = 12.dp
        val MOVE_DIALOG_ROW_SPACING = 12.dp
        val MOVE_DIALOG_ICON_SIZE = 24.dp
        const val MOVE_DIALOG_ICON_ALPHA = 0.7f
        val MOVE_DIALOG_FONT_SIZE: TextUnit = 14.sp
        val MOVE_DIALOG_CHEVRON_SIZE = 20.dp
        val MOVE_DIALOG_LIST_SPACING = 2.dp
        const val MOVE_DIALOG_ROW_BG_ALPHA = 0.5f

        /** Multi-select / merge bar */
        val MERGE_BAR_HEIGHT = 56.dp
        val MERGE_BAR_PADDING_H = 16.dp
        val MERGE_BAR_CORNER = 16.dp
        val MERGE_BAR_MARGIN = 12.dp
        val MERGE_BUTTON_CORNER = 8.dp
        val MERGE_BUTTON_PADDING_H = 20.dp
        val MERGE_BUTTON_PADDING_V = 8.dp
        val MERGE_FONT_SIZE: TextUnit = 14.sp
        val CHECKBOX_SIZE = 24.dp
        val CHECKBOX_OFFSET = 8.dp
    }

    // ── Viewer screen ────────────────────────────────

    object Viewer {
        val SIDEBAR_MIN_WIDTH = 300.dp
        val SIDEBAR_MAX_WIDTH = 600.dp
        val SIDEBAR_DEFAULT_WIDTH = 420.dp
    }
}
