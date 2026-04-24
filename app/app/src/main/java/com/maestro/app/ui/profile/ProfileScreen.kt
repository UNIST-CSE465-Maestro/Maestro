package com.maestro.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maestro.app.domain.model.ConceptKnowledge
import com.maestro.app.domain.model.DocumentKnowledge
import com.maestro.app.domain.model.ProfileSummary
import com.maestro.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateAvatar(uri)
        }
    }
    var editingName by remember {
        mutableStateOf(false)
    }
    var draftName by remember(
        state.profile.displayName,
        state.username
    ) {
        mutableStateOf(
            state.profile.displayName
                ?: state.username
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaestroBackground)
    ) {
        ProfileTopBar(onBack = onBack)
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaestroPrimary)
            }
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ProfileHeader(
                        avatarPath = state.profile.avatarPath,
                        displayName = state.profile.displayName
                            ?: state.username.ifBlank { "Maestro User" },
                        username = state.username,
                        editingName = editingName,
                        draftName = draftName,
                        onDraftNameChange = { draftName = it },
                        onEditName = { editingName = true },
                        onSaveName = {
                            viewModel.updateDisplayName(draftName)
                            editingName = false
                        },
                        onChangeAvatar = {
                            imagePicker.launch("image/*")
                        }
                    )
                }
                item {
                    SummaryPanel(state.dashboard.summary)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    KnowledgeOverview(
                        summary = state.dashboard.summary,
                        strongConcepts = state.dashboard.strongConcepts,
                        weakConcepts = state.dashboard.weakConcepts
                    )
                }
                item {
                    SectionTitle("문서별 Knowledge")
                }
                if (state.dashboard.documents.isEmpty()) {
                    item {
                        EmptyPanel("아직 학습 활동이 없습니다")
                    }
                } else {
                    items(
                        state.dashboard.documents,
                        key = { it.documentId }
                    ) { document ->
                        DocumentKnowledgeRow(document)
                    }
                }
                item {
                    SectionTitle("개념별 Knowledge")
                }
                if (state.dashboard.concepts.isEmpty()) {
                    item {
                        EmptyPanel("PDF를 업로드하면 개념이 표시됩니다")
                    }
                } else {
                    items(
                        state.dashboard.concepts,
                        key = { it.id }
                    ) { concept ->
                        ConceptKnowledgeRow(concept)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Slate50)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로",
                tint = Maestro900
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Profile",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Maestro900
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Knowledge State",
            fontSize = 13.sp,
            color = Slate500
        )
    }
}

@Composable
private fun ProfileHeader(
    avatarPath: String?,
    displayName: String,
    username: String,
    editingName: Boolean,
    draftName: String,
    onDraftNameChange: (String) -> Unit,
    onEditName: () -> Unit,
    onSaveName: () -> Unit,
    onChangeAvatar: () -> Unit
) {
    Surface(
        color = MaestroSurfaceContainerLowest,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AvatarImage(
                    avatarPath = avatarPath,
                    size = 104
                )
                FilledIconButton(
                    onClick = onChangeAvatar,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaestroPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "프로필 사진 변경",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (editingName) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = onDraftNameChange,
                    singleLine = true,
                    label = { Text("표시 이름") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSaveName,
                    enabled = draftName.isNotBlank()
                ) {
                    Text("저장")
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        displayName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaestroOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onEditName) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "이름 변경",
                            tint = Slate500
                        )
                    }
                }
            }
            Text(
                username.ifBlank { "local profile" },
                fontSize = 13.sp,
                color = Slate500
            )
        }
    }
}

@Composable
private fun AvatarImage(avatarPath: String?, size: Int) {
    val modifier = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(MaestroSurfaceContainer)
    if (avatarPath != null) {
        AsyncImage(
            model = File(avatarPath),
            contentDescription = "프로필 사진",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = "프로필",
            modifier = modifier.padding(6.dp),
            tint = Slate500
        )
    }
}

@Composable
private fun SummaryPanel(summary: ProfileSummary) {
    Surface(
        color = MaestroSurfaceContainerLowest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("학습 요약")
            SummaryMetric(
                icon = Icons.Default.Description,
                label = "전체 PDF",
                value = "${summary.totalPdfCount}"
            )
            SummaryMetric(
                icon = Icons.Default.Insights,
                label = "학습한 문서",
                value = "${summary.studiedDocumentCount}"
            )
            SummaryMetric(
                icon = Icons.Default.SmartToy,
                label = "LLM requests",
                value = "${summary.totalLlmRequests}"
            )
            SummaryMetric(
                icon = Icons.Default.Quiz,
                label = "Quiz events",
                value = "${summary.totalQuizEvents}"
            )
            SummaryMetric(
                icon = Icons.Default.AutoGraph,
                label = "최근 7일 활동",
                value = "${summary.recentActivityCount}"
            )
            SummaryMetric(
                icon = Icons.Default.Psychology,
                label = "최근 학습",
                value = formatDate(summary.lastStudiedAt)
            )
        }
    }
}

@Composable
private fun KnowledgeOverview(
    summary: ProfileSummary,
    strongConcepts: List<ConceptKnowledge>,
    weakConcepts: List<ConceptKnowledge>
) {
    Surface(
        color = MaestroSurfaceContainerLowest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Knowledge State",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaestroOnSurface
                    )
                    Text(
                        summary.rektStatus,
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
                MasteryBadge(summary.averageMastery)
            }
            LinearProgressIndicator(
                progress = { summary.averageMastery },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaestroPrimary,
                trackColor = MaestroSurfaceContainerHigh
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConceptList(
                    title = "강한 개념",
                    concepts = strongConcepts,
                    modifier = Modifier.weight(1f)
                )
                ConceptList(
                    title = "취약 개념",
                    concepts = weakConcepts,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConceptList(
    title: String,
    concepts: List<ConceptKnowledge>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaestroSurfaceContainerLow,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaestroOnSurface
        )
        if (concepts.isEmpty()) {
            Text(
                "데이터 없음",
                fontSize = 12.sp,
                color = Slate500
            )
        } else {
            concepts.forEach { concept ->
                Text(
                    "${concept.name} ${percent(concept.mastery)}",
                    fontSize = 12.sp,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DocumentKnowledgeRow(document: DocumentKnowledge) {
    KnowledgeRow(
        title = document.title,
        subtitle = "${document.pageCount} pages · ${document.activityCount} activities · ${formatDate(document.lastStudiedAt)}",
        mastery = document.mastery,
        confidence = document.confidence
    )
}

@Composable
private fun ConceptKnowledgeRow(concept: ConceptKnowledge) {
    KnowledgeRow(
        title = concept.name,
        subtitle = "${concept.documentIds.size} linked document(s)",
        mastery = concept.mastery,
        confidence = concept.confidence
    )
}

@Composable
private fun KnowledgeRow(
    title: String,
    subtitle: String,
    mastery: Float,
    confidence: Float
) {
    Surface(
        color = MaestroSurfaceContainerLowest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = MaestroOnSurface
                    )
                    Text(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
                Text(
                    percent(mastery),
                    fontWeight = FontWeight.Bold,
                    color = MaestroPrimary
                )
            }
            LinearProgressIndicator(
                progress = { mastery },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaestroPrimary,
                trackColor = MaestroSurfaceContainerHigh
            )
            Text(
                "confidence ${percent(confidence)}",
                fontSize = 11.sp,
                color = Slate500
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaestroPrimary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Slate500,
            fontSize = 13.sp
        )
        Text(
            value,
            color = MaestroOnSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaestroOnSurface
    )
}

@Composable
private fun EmptyPanel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaestroSurfaceContainerLowest,
                RoundedCornerShape(8.dp)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Slate500)
    }
}

@Composable
private fun MasteryBadge(mastery: Float) {
    Box(
        modifier = Modifier
            .background(
                Maestro50,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            percent(mastery),
            color = MaestroPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun percent(value: Float): String =
    "${(value.coerceIn(0f, 1f) * 100).toInt()}%"

private fun formatDate(value: Long?): String {
    if (value == null || value <= 0L) return "-"
    return SimpleDateFormat(
        "MM/dd HH:mm",
        Locale.US
    ).format(Date(value))
}
