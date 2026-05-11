package com.maestro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import com.maestro.app.ui.theme.*

@Composable
fun TopAppBarSection(
    drawingState: DrawingState? = null,
    isPinned: Boolean = false,
    isBookmarked: Boolean = false,
    onBack: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onInsertImage: () -> Unit = {},
    searchQuery: String = "",
    searchResultCount: Int = 0,
    activeSearchResultIndex: Int = -1,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchPrevious: () -> Unit = {},
    onSearchNext: () -> Unit = {},
    onQuiz: () -> Unit = {},
    onToggleSidebar: () -> Unit = {}
) {
    val canNavigateSearch =
        searchQuery.isNotBlank() && searchResultCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UxConfig.TopBar.HEIGHT)
            .background(Slate50)
            .padding(horizontal = UxConfig.TopBar.HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(UxConfig.TopBar.ICON_BUTTON_SIZE)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "나가기",
                tint = Slate500
            )
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(UxConfig.TopBar.ICON_BUTTON_SIZE)
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = "PDF 순서",
                tint = Slate500
            )
        }

        IconButton(
            onClick = onUndo,
            modifier = Modifier.size(UxConfig.TopBar.ICON_BUTTON_SIZE)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "실행 취소",
                tint = Slate500
            )
        }
        IconButton(
            onClick = onRedo,
            modifier = Modifier.size(UxConfig.TopBar.ICON_BUTTON_SIZE)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = "다시 실행",
                tint = Slate500
            )
        }

        IconButton(
            onClick = onTogglePin,
            modifier = Modifier.size(UxConfig.TopBar.ICON_BUTTON_SIZE)
        ) {
            Icon(
                if (isPinned) {
                    Icons.Filled.PushPin
                } else {
                    Icons.Outlined.PushPin
                },
                contentDescription = "핀",
                tint = if (isPinned) {
                    MaestroPrimary
                } else {
                    Slate500
                }
            )
        }

        IconButton(
            onClick = onToggleBookmark,
            modifier = Modifier.size(UxConfig.TopBar.ICON_BUTTON_SIZE)
        ) {
            Icon(
                if (isBookmarked) {
                    Icons.Filled.Bookmark
                } else {
                    Icons.Default.BookmarkBorder
                },
                contentDescription = "북마크",
                tint = if (isBookmarked) {
                    MaestroPrimary
                } else {
                    Slate500
                }
            )
        }

        IconButton(
            onClick = onInsertImage,
            modifier = Modifier.size(
                UxConfig.TopBar.ICON_BUTTON_SIZE
            )
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = "이미지 삽입",
                tint = Slate500
            )
        }

        Box(
            Modifier.width(
                UxConfig.TopBar.DIVIDER_WIDTH
            ).height(UxConfig.TopBar.DIVIDER_HEIGHT).background(Slate200)
        )

        Spacer(Modifier.width(4.dp))

        if (drawingState != null) {
            FloatingToolbar(state = drawingState)
        }

        Spacer(Modifier.width(4.dp))

        Box(
            Modifier.width(
                UxConfig.TopBar.DIVIDER_WIDTH
            ).height(UxConfig.TopBar.DIVIDER_HEIGHT).background(Slate200)
        )

        Spacer(Modifier.width(4.dp))

        Row(
            modifier = Modifier.weight(0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(UxConfig.TopBar.SEARCH_FIELD_HEIGHT)
                    .background(
                        color = MaestroSurfaceContainerHigh,
                        shape = RoundedCornerShape(
                            UxConfig.TopBar.SEARCH_FIELD_CORNER
                        )
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "검색",
                    tint = MaestroOutline,
                    modifier = Modifier.size(UxConfig.TopBar.SEARCH_ICON_SIZE)
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaestroOnSurface,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(MaestroPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isBlank()) {
                                Text(
                                    "검색",
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    color = MaestroOnSurfaceVariant.copy(
                                        alpha =
                                        UxConfig.TopBar.SEARCH_PLACEHOLDER_ALPHA
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "검색어 지우기",
                            tint = MaestroOutline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            SearchMoveButton(
                enabled = canNavigateSearch,
                onClick = onSearchPrevious,
                direction = SearchMoveDirection.Previous
            )
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (canNavigateSearch) {
                        "${activeSearchResultIndex + 1}/$searchResultCount"
                    } else {
                        "0/0"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canNavigateSearch) {
                        MaestroOnSurfaceVariant
                    } else {
                        MaestroOutline
                    }
                )
            }
            SearchMoveButton(
                enabled = canNavigateSearch,
                onClick = onSearchNext,
                direction = SearchMoveDirection.Next
            )
        }

        Spacer(Modifier.width(4.dp))

        Button(
            onClick = onQuiz,
            shape = RoundedCornerShape(UxConfig.TopBar.QUIZ_BUTTON_CORNER),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = UxConfig.TopBar.QUIZ_BUTTON_ELEVATION
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
                            end = Offset(UxConfig.TopBar.GRADIENT_END, UxConfig.TopBar.GRADIENT_END)
                        ),
                        shape = RoundedCornerShape(UxConfig.TopBar.QUIZ_BUTTON_CORNER)
                    )
                    .padding(
                        horizontal = UxConfig.TopBar.QUIZ_BUTTON_PADDING_H,
                        vertical = UxConfig.TopBar.QUIZ_BUTTON_PADDING_V
                    )
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
            modifier = Modifier.size(UxConfig.TopBar.ICON_BUTTON_SIZE)
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = "LLM 패널",
                tint = MaestroPrimary
            )
        }
    }
}

private enum class SearchMoveDirection {
    Previous,
    Next
}

@Composable
private fun SearchMoveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    direction: SearchMoveDirection
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (enabled) {
                MaestroSurfaceContainerLowest
            } else {
                MaestroSurfaceContainerHigh
            },
            contentColor = if (enabled) {
                MaestroPrimary
            } else {
                MaestroOutline
            },
            disabledContainerColor = MaestroSurfaceContainerHigh,
            disabledContentColor = MaestroOutline
        )
    ) {
        Icon(
            imageVector = if (direction == SearchMoveDirection.Previous) {
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            },
            contentDescription = if (direction == SearchMoveDirection.Previous) {
                "이전 검색 결과"
            } else {
                "다음 검색 결과"
            },
            modifier = Modifier.size(21.dp)
        )
    }
}
