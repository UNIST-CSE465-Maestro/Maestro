package com.maestro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.domain.model.DrawingTool
import com.maestro.app.domain.model.EraserMode
import com.maestro.app.ui.drawing.*
import com.maestro.app.ui.theme.*

private enum class PopupType { NONE, PEN, ERASER }

@Composable
fun FloatingToolbar(state: DrawingState, modifier: Modifier = Modifier) {
    var openPopup by remember { mutableStateOf(PopupType.NONE) }

    LaunchedEffect(state.activeTool) {
        when (state.activeTool) {
            DrawingTool.PEN -> {
                if (openPopup == PopupType.ERASER) {
                    openPopup = PopupType.NONE
                }
                state.clearLasso()
            }
            DrawingTool.ERASER -> {
                if (openPopup == PopupType.PEN) {
                    openPopup = PopupType.NONE
                }
                state.clearLasso()
            }
            DrawingTool.LASSO -> openPopup = PopupType.NONE
        }
    }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ToolButton(
                icon = Icons.Default.Edit,
                label = "Pen",
                isActive = state.activeTool == DrawingTool.PEN &&
                    !state.sPenButtonPressed,
                activeColor = state.activeColor,
                onClick = {
                    if (state.activeTool == DrawingTool.PEN) {
                        openPopup = if (openPopup == PopupType.PEN) {
                            PopupType.NONE
                        } else {
                            PopupType.PEN
                        }
                    } else {
                        state.activeTool = DrawingTool.PEN
                        openPopup = PopupType.NONE
                    }
                }
            )

            ToolButton(
                icon = Icons.Outlined.AutoFixHigh,
                label = "Eraser",
                isActive = state.activeTool == DrawingTool.ERASER ||
                    state.sPenButtonPressed,
                activeColor = Maestro600,
                onClick = {
                    if (state.activeTool == DrawingTool.ERASER) {
                        openPopup = if (openPopup == PopupType.ERASER) {
                            PopupType.NONE
                        } else {
                            PopupType.ERASER
                        }
                    } else {
                        state.activeTool = DrawingTool.ERASER
                        openPopup = PopupType.NONE
                    }
                }
            )

            ToolButton(
                icon = Icons.Default.Gesture,
                label = "Select",
                isActive = state.activeTool == DrawingTool.LASSO,
                activeColor = Maestro600,
                onClick = {
                    state.activeTool = DrawingTool.LASSO
                    openPopup = PopupType.NONE
                }
            )
        }

        DropdownMenu(
            expanded = openPopup == PopupType.PEN,
            onDismissRequest = { openPopup = PopupType.NONE },
            offset = DpOffset(0.dp, 4.dp),
            modifier = Modifier.background(
                MaestroSurfaceContainerLowest,
                RoundedCornerShape(16.dp)
            )
        ) {
            PenPopupContent(state)
        }

        DropdownMenu(
            expanded = openPopup == PopupType.ERASER,
            onDismissRequest = { openPopup = PopupType.NONE },
            offset = DpOffset(0.dp, 4.dp),
            modifier = Modifier.background(
                MaestroSurfaceContainerLowest,
                RoundedCornerShape(16.dp)
            )
        ) {
            EraserPopupContent(state)
        }
    }
}

@Composable
private fun PenPopupContent(state: DrawingState) {
    Column(
        modifier = Modifier.padding(16.dp).widthIn(min = 240.dp)
    ) {
        if (state.isStylusActive) {
            StylusIndicator(state)
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "색상",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkColors.forEach { color ->
                val isSelected = state.activeColor == color
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    3.dp,
                                    MaestroOnSurface,
                                    CircleShape
                                )
                            } else {
                                Modifier.border(
                                    1.dp,
                                    MaestroOutlineVariant.copy(0.4f),
                                    CircleShape
                                )
                            }
                        )
                        .clickable { state.activeColor = color }
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "두께",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PenWidths.forEach { width ->
                val sel = state.activeWidth == width
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (sel) Maestro50 else Color.Transparent
                        )
                        .then(
                            if (sel) {
                                Modifier.border(
                                    2.dp,
                                    MaestroPrimary,
                                    CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { state.activeWidth = width },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size((width * 2.5f).dp)
                            .clip(CircleShape)
                            .background(state.activeColor)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(state.activeWidth.dp)
                    .background(
                        state.activeColor,
                        RoundedCornerShape(9999.dp)
                    )
            )
        }
    }
}

@Composable
private fun EraserPopupContent(state: DrawingState) {
    Column(
        modifier = Modifier.padding(16.dp).widthIn(min = 240.dp)
    ) {
        Text(
            "지우개 종류",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EraserModeChip(
                "획 지우개",
                state.eraserMode == EraserMode.STROKE
            ) { state.eraserMode = EraserMode.STROKE }
            EraserModeChip(
                "부분 지우개",
                state.eraserMode == EraserMode.PARTIAL
            ) { state.eraserMode = EraserMode.PARTIAL }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "지우개 크기",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EraserWidths.forEach { width ->
                val sel = state.eraserWidth == width
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (sel) Maestro50 else Color.Transparent
                        )
                        .then(
                            if (sel) {
                                Modifier.border(
                                    2.dp,
                                    MaestroPrimary,
                                    CircleShape
                                )
                            } else {
                                Modifier.border(
                                    1.dp,
                                    MaestroOutlineVariant.copy(0.3f),
                                    CircleShape
                                )
                            }
                        )
                        .clickable { state.eraserWidth = width },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size((width * 0.45f).dp)
                            .clip(CircleShape)
                            .background(Slate400)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val desc = when (state.eraserMode) {
            EraserMode.STROKE -> "닿은 획 전체를 지웁니다"
            EraserMode.PARTIAL -> "닿은 부분만 잘라서 지웁니다"
        }
        Text(desc, fontSize = 10.sp, color = Slate500)
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (isActive) activeColor else Color.Transparent,
        label = "bg"
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).background(bg).size(40.dp)
    ) {
        Icon(
            icon,
            label,
            tint = if (isActive) Color.White else Slate500
        )
    }
}

@Composable
private fun EraserModeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(
                if (isSelected) {
                    MaestroPrimary
                } else {
                    MaestroSurfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else MaestroOnSurface
        )
    }
}

@Composable
private fun StylusIndicator(state: DrawingState) {
    val label = buildString {
        append("S Pen")
        if (state.sPenButtonPressed) append(" · Btn")
    }
    Row(
        Modifier
            .background(MaestroPrimary, RoundedCornerShape(9999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(5.dp).clip(CircleShape)
                .background(Emerald500)
        )
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
