package com.maestro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.ui.drawing.DrawingState
import com.maestro.app.ui.theme.*

@Composable
fun TopAppBarSection(
    drawingState: DrawingState? = null,
    onBack: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleSidebar: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Slate50)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "나가기",
                tint = Slate500
            )
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = "PDF 순서",
                tint = Slate500
            )
        }

        IconButton(
            onClick = onUndo,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "실행 취소",
                tint = Slate500
            )
        }
        IconButton(
            onClick = onRedo,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = "다시 실행",
                tint = Slate500
            )
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = "핀",
                tint = Slate500
            )
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = "북마크",
                tint = Slate500
            )
        }

        Box(
            Modifier.width(1.dp).height(28.dp).background(Slate200)
        )

        Spacer(Modifier.width(4.dp))

        if (drawingState != null) {
            FloatingToolbar(state = drawingState)
        }

        Spacer(Modifier.width(4.dp))

        Box(
            Modifier.width(1.dp).height(28.dp).background(Slate200)
        )

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier.weight(0.5f),
            contentAlignment = Alignment.Center
        ) {
            TextField(
                value = "",
                onValueChange = {},
                placeholder = {
                    Text(
                        "검색",
                        fontSize = 11.sp,
                        color = MaestroOnSurfaceVariant.copy(
                            alpha = 0.7f
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "검색",
                        tint = MaestroOutline,
                        modifier = Modifier.size(14.dp)
                    )
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp
                ),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor =
                    MaestroSurfaceContainerHigh,
                    focusedContainerColor =
                    MaestroSurfaceContainer,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        Button(
            onClick = {},
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaestroPrimary,
                                MaestroPrimaryContainer
                            ),
                            start = Offset.Zero,
                            end = Offset(200f, 200f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "QUIZ!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        IconButton(
            onClick = onToggleSidebar,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = "LLM 패널",
                tint = MaestroPrimary
            )
        }
    }
}
