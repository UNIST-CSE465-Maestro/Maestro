package com.maestro.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
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
import com.maestro.app.data.local.ModelArtifactState
import com.maestro.app.data.local.ModelArtifactType
import com.maestro.app.data.local.MonitoringLogCategory
import com.maestro.app.data.local.MonitoringLogEntry
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
    val ktModelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadModel(ModelArtifactType.KT_ONNX, uri)
        }
    }
    val conceptModelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadModel(
                ModelArtifactType.CONCEPT_ONNX,
                uri
            )
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
        ProfileTopBar(
            onBack = onBack,
            onRefresh = { viewModel.refresh() }
        )
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
                    ModelUploadPanel(
                        artifacts = state.modelArtifacts,
                        onUploadKt = {
                            ktModelPicker.launch("*/*")
                        },
                        onUploadConcept = {
                            conceptModelPicker.launch("*/*")
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
                    MonitoringConsole(
                        logs = state.monitoringLogs,
                        onDeleteLogs = viewModel::deleteMonitoringLogs
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
private fun ProfileTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
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
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "새로고침",
                tint = Maestro900
            )
        }
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
private fun ModelUploadPanel(
    artifacts: List<ModelArtifactState>,
    onUploadKt: () -> Unit,
    onUploadConcept: () -> Unit
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
            SectionTitle("KT Experiment Models")
            Text(
                "공학역학 concept space 기준으로 ONNX 모델을 앱 내부에 저장합니다.",
                fontSize = 12.sp,
                color = Slate500,
                lineHeight = 17.sp
            )
            ModelArtifactRow(
                state = artifacts.firstOrNull {
                    it.type == ModelArtifactType.KT_ONNX
                },
                fallbackLabel = "KT ONNX",
                note = "업로드된 경우에만 KT 추론과 device resource logging이 활성화됩니다.",
                onUpload = onUploadKt
            )
            ModelArtifactRow(
                state = artifacts.firstOrNull {
                    it.type == ModelArtifactType.CONCEPT_ONNX
                },
                fallbackLabel = "Concept ONNX",
                note = "문서 -> 공학역학 concept 자동 지정 모델을 위한 슬롯입니다.",
                onUpload = onUploadConcept
            )
        }
    }
}

@Composable
private fun ModelArtifactRow(
    state: ModelArtifactState?,
    fallbackLabel: String,
    note: String,
    onUpload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaestroSurfaceContainerLow,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state?.label ?: fallbackLabel,
                    fontWeight = FontWeight.Bold,
                    color = MaestroOnSurface
                )
                Text(
                    if (state?.isReady == true) {
                        "${formatBytes(state.fileSizeBytes)} · ${formatDate(state.updatedAt)}"
                    } else {
                        "Not uploaded"
                    },
                    fontSize = 12.sp,
                    color = if (state?.isReady == true) {
                        MaestroPrimary
                    } else {
                        Slate500
                    }
                )
            }
            Button(onClick = onUpload) {
                Text("ONNX 업로드")
            }
        }
        Text(
            note,
            fontSize = 11.sp,
            color = Slate500,
            lineHeight = 16.sp
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
private fun MonitoringConsole(
    logs: List<MonitoringLogEntry>,
    onDeleteLogs: (Set<String>) -> Unit
) {
    var selectedCategory by remember {
        mutableStateOf(MonitoringLogCategory.KT_RUNTIME)
    }
    var deleteMode by remember {
        mutableStateOf(false)
    }
    var selectedLogIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    var showDeleteConfirm by remember {
        mutableStateOf(false)
    }
    val categoryLogs = logs
        .filter { it.category == selectedCategory }
        .sortedByDescending { it.timestamp }
        .take(12)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("로그 삭제") },
            text = {
                Text("선택한 로그 ${selectedLogIds.size}개를 삭제할까요?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLogs(selectedLogIds)
                        selectedLogIds = emptySet()
                        deleteMode = false
                        showDeleteConfirm = false
                    }
                ) {
                    Text(
                        "삭제",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
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
                        "Experiment Monitoring",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaestroOnSurface
                    )
                    Text(
                        "KT runtime, learning behavior, evaluation, device, reliability logs",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
                if (deleteMode) {
                    TextButton(
                        onClick = {
                            deleteMode = false
                            selectedLogIds = emptySet()
                        }
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = {
                            if (selectedLogIds.isNotEmpty()) {
                                showDeleteConfirm = true
                            }
                        },
                        enabled = selectedLogIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                            MaterialTheme.colorScheme.error,
                            contentColor =
                            MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("삭제")
                    }
                } else {
                    TextButton(
                        onClick = {
                            deleteMode = true
                            selectedLogIds = emptySet()
                        }
                    ) {
                        Text("로그 지우기")
                    }
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MonitoringLogCategory.entries) { category ->
                    val selected = category == selectedCategory
                    Text(
                        category.label(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) {
                                    MaestroPrimary
                                } else {
                                    MaestroSurfaceContainerLow
                                }
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 7.dp
                            )
                            .clickable {
                                selectedCategory = category
                                selectedLogIds = emptySet()
                            },
                        color = if (selected) {
                            Color.White
                        } else {
                            Slate500
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (categoryLogs.isEmpty()) {
                EmptyPanel("${selectedCategory.label()} 로그가 아직 없습니다")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryLogs.forEach { log ->
                        MonitoringLogRow(
                            log = log,
                            deleteMode = deleteMode,
                            selected = log.id in selectedLogIds,
                            onSelectionChange = { checked ->
                                selectedLogIds = if (checked) {
                                    selectedLogIds + log.id
                                } else {
                                    selectedLogIds - log.id
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoringLogRow(log: MonitoringLogEntry) {
    MonitoringLogRow(
        log = log,
        deleteMode = false,
        selected = false,
        onSelectionChange = {}
    )
}

@Composable
private fun MonitoringLogRow(
    log: MonitoringLogEntry,
    deleteMode: Boolean,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaestroSurfaceContainerLow,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = deleteMode) {
                onSelectionChange(!selected)
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                log.eventType,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                color = MaestroOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatDate(log.timestamp),
                fontSize = 11.sp,
                color = Slate500
            )
            if (deleteMode) {
                Spacer(Modifier.width(8.dp))
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectionChange
                )
            }
        }
        val contextLine = listOfNotNull(
            log.documentId?.let { "doc=$it" },
            log.conceptId?.let { "concept=$it" }
        ).joinToString(" · ")
        if (contextLine.isNotBlank()) {
            Text(
                contextLine,
                fontSize = 11.sp,
                color = Slate500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            log.metadata.entries
                .take(6)
                .joinToString(" · ") {
                    "${it.key}=${it.value}"
                }
                .ifBlank { "no metadata" },
            fontSize = 11.sp,
            color = Slate500,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DocumentKnowledgeRow(document: DocumentKnowledge) {
    Surface(
        color = MaestroSurfaceContainerLowest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        document.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = MaestroOnSurface
                    )
                    Text(
                        "${document.pageCount} pages · ${document.activityCount} activities · ${formatDate(document.lastStudiedAt)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
                Text(
                    "avg ${percent(document.mastery)}",
                    fontWeight = FontWeight.Bold,
                    color = MaestroPrimary
                )
            }
            if (document.concepts.isEmpty()) {
                Text(
                    "연결된 공학역학 concept이 없습니다",
                    fontSize = 12.sp,
                    color = Slate500
                )
            } else {
                document.concepts.forEach { concept ->
                    DocumentConceptRow(concept)
                }
            }
        }
    }
}

@Composable
private fun DocumentConceptRow(
    concept: com.maestro.app.domain.model.DocumentConceptKnowledge
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                concept.name,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaestroOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${percent(concept.mastery)} · conf ${percent(concept.confidence)}",
                fontSize = 11.sp,
                color = Slate500
            )
        }
        LinearProgressIndicator(
            progress = { concept.mastery },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = MaestroPrimary,
            trackColor = MaestroSurfaceContainerHigh
        )
    }
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

private fun MonitoringLogCategory.label(): String =
    when (this) {
        MonitoringLogCategory.DEVICE_RESOURCE -> "Device"
        MonitoringLogCategory.KT_RUNTIME -> "KT Runtime"
        MonitoringLogCategory.LEARNING_BEHAVIOR -> "Learning"
        MonitoringLogCategory.DOMAIN_EVALUATION -> "Evaluation"
        MonitoringLogCategory.UX_RELIABILITY -> "Reliability"
    }

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1f) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        "${bytes / 1024L} KB"
    }
}

private fun formatDate(value: Long?): String {
    if (value == null || value <= 0L) return "-"
    return SimpleDateFormat(
        "MM/dd HH:mm",
        Locale.US
    ).format(Date(value))
}
