# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Structure

Monorepo with two projects:

- **`app/`** — Android tablet app "Maestro". PDF annotation tool with stylus handwriting, LLM chat sidebar, and document management.
- **`server/`** — Django REST API backend. PDF text extraction via MinerU, JWT auth, Celery task queue.

Supporting directories: `deploy/` (Nginx, systemd, test scripts), `ref/` (research docs).

## Build & Run

### Android App

```bash
cd ~/maestro/app

./gradlew assembleDebug          # Build debug APK
./gradlew testDebugUnitTest      # Run unit tests
./gradlew ktlintCheck            # Lint check
./gradlew ktlintFormat           # Auto-fix lint
./gradlew assembleDebug ktlintCheck testDebugUnitTest  # All at once

# Run a single test class
./gradlew testDebugUnitTest --tests "*.HomeViewModelTest"

# Run a single test method
./gradlew testDebugUnitTest --tests "*.HomeViewModelTest.init loads documents and folders"
```

APK output: `app/app/build/outputs/apk/debug/app-debug.apk`

ANDROID_HOME is at `~/android-sdk` (set in `app/local.properties`).

### Django Server

```bash
cd ~/maestro/server

uv run python manage.py runserver              # Dev server
uv run python manage.py test                   # Tests
uv run python manage.py makemigrations         # Create migrations
uv run python manage.py migrate                # Apply migrations
uv run python manage.py cleanup_expired        # Delete expired analysis results
```

**Always use `uv run` — never `pip` or `python` directly.** Python dependencies are managed via `uv` with `pyproject.toml` at the repo root.

### Celery Worker

```bash
cd ~/maestro/server
uv run celery -A config worker --loglevel=info
```

Requires Redis running on localhost:6379.

### Production Deployment

```bash
sudo systemctl restart maestro-gunicorn maestro-celery
```

End-to-end test: `./deploy/test.sh "document.pdf"`

### Git

```bash
./commit.sh "feat: short description"   # Single-line only, max 120 chars
```

## Android App Architecture

**Stack**: Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Material3, Koin 4.0.1, Retrofit 2.11.0, OkHttp 4.12.0, kotlinx-serialization 1.7.3.

**SDK**: minSdk 26, compileSdk/targetSdk 35, AGP 8.7.3, JVM 17.

### Layers (Clean Architecture)

**`domain/`** — Pure Kotlin models and interfaces. No Android dependencies except `Uri` in `DocumentRepository`.
- `model/`: `PdfDocument` (with `ExtractionStatus` enum: NONE/EXTRACTING/DONE/FAILED, `isPinned`, `bookmarkedPages`), `Folder`, `InkStroke`, `StrokePoint`, `DrawingTool` (PEN/ERASER/LASSO/CROP_CAPTURE), `CropCapturePhase` (IDLE/DRAWING/ADJUSTING), `ChatMessage`, `LlmProvider` (GEMINI/OPENAI/CLAUDE)
- `repository/`: `DocumentRepository` (includes `updateDocument()`, `duplicateDocument()`), `AnnotationRepository`, `SettingsRepository` (per-provider API keys, provider/model selection)
- `service/`: `LlmService` (general-purpose: chat, lasso→LLM, crop→LLM share one interface; includes `fetchModels()`)

**`data/`** — Implementations.
- `repository/`: JSON file persistence (no Room). `DocumentRepositoryImpl` (with `updateDocument()`, `duplicateDocument()`), `AnnotationRepositoryImpl` (kotlinx.serialization), `SettingsRepositoryImpl` (SharedPreferences, per-provider API keys + legacy migration).
- `remote/`: Multi-provider LLM clients — `LlmClient` (Gemini SSE), `OpenAiClient` (OpenAI-compatible SSE), `ClaudeClient` (Anthropic SSE). `MaestroServerApi` (Retrofit for Django server), `TokenManager` (JWT interceptor with auto-refresh), `MaterialAnalyzerClient` (upload/poll/download, 5s poll interval).
- `local/`: `ConversationLocalDataSource` (chat history), `PdfMerger`.
- `service/`: `LlmServiceImpl` (routes to provider-specific client based on `LlmProvider` selection), `QuizServiceImpl`.
- `model/`: `LlmRequestBuilder` (JSON DSL for Gemini API, handles text + vision multipart), DTOs (`DocumentDto` with extraction/pin/bookmark fields, `FolderDto`).

**`ui/`** — Compose screens and components.
- `auth/`: `AuthScreen` + `AuthViewModel` — standalone login/register with tab UI. Shown as start screen when not logged in.
- `home/`: `HomeScreen` + `HomeViewModel` — document library, folder management, drag-and-drop (long-press shows tooltip immediately while finger is down; drag transitions to move mode), multi-select PDF merge, document duplicate, PDF import with MinerU extraction mode selection dialog, extraction status indicator on document cards, pinned documents sorted first. Non-empty folder deletion is blocked.
- `viewer/`: `ViewerScreen` + `ViewerViewModel` — PDF viewer with annotation canvas, LLM sidebar with document context injection, quiz generation via sidebar prompt, auto-save, pin toggle, per-page bookmark toggle.
- `settings/`: `SettingsScreen` + `SettingsViewModel` — per-provider LLM API key management (Gemini, OpenAI; save/validate/delete), account display, logout. No login/register (moved to AuthScreen).
- `drawing/`: `DrawingState` — central state machine for pen/eraser/lasso/crop/crop_capture/image overlay. Holds per-page strokes, undo/redo, zoom, selection state, crop capture state.
- `components/`: `StylusDrawingCanvas` (low-level MotionEvent handling, ~2400 lines — most complex file), `CanvasSection` (PDF rendering + zoom/pan), `LlmSidebar` (native Compose chat with document context, lasso/crop image support, image attachment from gallery, clipboard paste), `FloatingToolbar` (with Crop Capture tool), `TopAppBarSection` (pin/bookmark buttons functional), `PdfPageRenderer`.
- `config/UxConfig.kt` — **All hardcoded UX values are centralized here.** Gesture thresholds, drawing parameters, layout dimensions, animation timings. Change values in one place.
- `theme/`: `Color.kt`, `Type.kt`, `Theme.kt`. Primary: Indigo #24389C.

**`di/`** — Koin modules: `AppModule` (ViewModels: `AuthViewModel`, `HomeViewModel`, `ViewerViewModel`, `SettingsViewModel`), `DataModule` (repositories, services, three LLM clients), `NetworkModule` (two OkHttpClients: "llmApi" for LLM API calls, "maestroServer" with TokenManager).

**`navigation/`** — Jetpack Navigation Compose. Routes: `Auth`, `Home`, `Viewer` (with pdfId/pageCount/uri args), `Settings`.

### App Startup Flow

1. Splash screen with logo + spinner.
2. Silent `GET /health` check against Maestro server. If server unreachable, show error dialog (non-blocking).
3. Check `SettingsRepository.getAccessToken()` — if token exists → `Home`, otherwise → `Auth`.
4. After login success in AuthScreen → navigate to Home (pop Auth).
5. Logout from Settings → navigate to Auth (pop Home).

### PDF Import & Extraction Flow

1. User taps "PDF 파일 업로드" in HomeScreen FAB menu.
2. File picker opens → user selects PDF.
3. Mode selection dialog appears: "일반 추출" (standard/CPU) or "AI 추출" (ai/GPU).
4. `HomeViewModel.importAndExtract(uri, mode)`:
   - Imports PDF via `DocumentRepository.importPdf()`.
   - Sets `extractionStatus = EXTRACTING` in document metadata.
   - Background: computes SHA256 hash → uploads to MinerU server → polls until complete → downloads `.md` and `.json` results → saves to `documents/{docId}/content.md` and `content.json`.
   - Sets `extractionStatus = DONE` (or `FAILED` on error).
5. Extraction progress shown as small spinner on document card in HomeScreen.
6. When user opens PDF in Viewer, extracted text is loaded from local cache and injected into LLM sidebar system prompt.

### LLM Integration

- **Multi-provider architecture.** Supports Gemini, OpenAI, Claude. Provider selection via `SettingsRepository.getLlmProvider()`/`setLlmProvider()`. Model selection via `getLlmModel()`/`setLlmModel()`. Per-provider API keys stored separately.
- **Provider clients**:
  - `LlmClient` (Gemini): SSE via `streamGenerateContent?alt=sse`, non-streaming via `generateContent`. Default model: `gemini-2.0-flash`.
  - `OpenAiClient`: OpenAI-compatible chat completions API with SSE streaming. Default model: `gpt-4o-mini`.
  - `ClaudeClient`: Anthropic Messages API with SSE streaming. Default model: `claude-sonnet-4-20250514`.
- **`LlmServiceImpl`**: Routes `stream()`/`complete()`/`validateApiKey()`/`fetchModels()` to the currently selected provider's client.
- **`LlmRequestBuilder`**: Builds Gemini-compatible JSON (`contents`/`parts` format). Supports `systemInstruction`, `generationConfig`, multimodal (text + Base64 image).
- **API key validation**: Provider-specific validation endpoints. Save and validate in one action.
- **`LlmSidebar`**: Native Compose chat panel. Features:
  - Document context injection — extracted PDF text included in system prompt (up to 30,000 chars).
  - Lasso/Crop→LLM — captures selection as PNG bitmap, auto-sends to LLM with image when sidebar receives `pendingImage`/`pendingPrompt`.
  - Image attachment — pick images from gallery to include in messages.
  - Clipboard paste — paste images from clipboard into chat.
  - Quiz — QUIZ button sends "이 문서의 내용을 바탕으로 객관식 퀴즈 5개를 만들어줘" as pending prompt. LLM generates quiz in-context with document content.
  - Conversation persistence via `ConversationLocalDataSource`.
  - Resizable drag handle (min 300dp, max 500dp).
- **Error messages**: User-facing only (no stack traces or developer errors). All catch blocks show generic Korean messages.

### Key Design Decisions

- **LlmService is general-purpose**, not chat-specific. Same `stream()`/`complete()` interface serves chat, lasso→LLM, and crop→LLM. System prompt injected per call site. `fetchModels()` lists available models for the active provider.
- **Multi-provider routing**: `LlmServiceImpl` inspects current provider setting and delegates to the corresponding client (`LlmClient`/`OpenAiClient`/`ClaudeClient`).
- **JSON file persistence** over Room. Documents stored as `{doc-id}/metadata.json`, annotations as `page_N.json`. Repository interfaces allow future Room migration.
- **UxConfig centralization**: 180+ magic numbers extracted. Avoids hardcoded dp/px/ms scattered across files.
- **Dual OkHttpClient**: LLM API calls use a plain client (named "llmApi"). Server calls use a client with `TokenManager` interceptor for JWT auto-refresh (named "maestroServer").
- **SHA256 hash formula**: `SHA256(SHA256(file) + mode)` — used for client-side caching and server-side deduplication.
- **Default server URL**: `https://maestro.jwchae.com/`.

### Not Yet Implemented (UI exists but noop)

These buttons exist in `TopAppBarSection.kt` with `onClick = {}`:
- **검색 (Search)**: TextField with hardcoded empty value, no search logic.
- **페이지 순서 (GridView)**: No page reordering logic.

### Testing

- **Fakes over Mocks** for ViewModel tests: `FakeDocumentRepository`, `FakeSettingsRepository` (in-memory, stateful).
- **MockK** only where Fakes aren't practical (e.g., `PdfMerger` needs Android `Context`, `MaterialAnalyzerClient`).
- `MainCoroutineRule` — JUnit 4 Rule for Dispatchers.Main replacement.
- `TestFixtures` — factory methods for domain models.

## Django Server Architecture

**Stack**: Django 6.0.4, DRF 3.17.1, Celery 5.6.3, Redis, MinerU 3.0.9, PyTorch 2.6.0+cu124.

### Apps

**`accounts/`** — JWT authentication.
- `POST /api/v1/auth/register` — `{username, email, password}`
- `POST /api/v1/auth/login` — returns `{access, refresh}`. Access: 30min, Refresh: 7 days with rotation.
- `POST /api/v1/auth/refresh` — rotates refresh token, old one blacklisted.

**`analyzer/`** — PDF extraction via MinerU.
- `POST /api/v1/material-analyzer/` — Upload PDF (multipart: file, sha256, mode). Mode: `standard` (CPU pipeline) or `ai` (GPU vlm-auto-engine). Returns task ID (202).
- `GET /api/v1/material-analyzer/{id}` — Status: 200 (done), 202 (processing), 404 (expired/missing), 500 (failed).
- `GET /api/v1/material-analyzer/{id}.md` — Markdown result.
- `GET /api/v1/material-analyzer/{id}.json` — JSON result.
- `GET /api/v1/health` — Server + MinerU availability. Used by app on startup for silent health check.

### Processing Flow

1. Client computes `SHA256(SHA256(file) + mode)`, sends with upload.
2. Server re-computes hash (chunked, no full-file memory load), verifies match.
3. Server checks cache: existing completed task with same hash+mode → return immediately.
4. Cache miss → save file, create `AnalysisTask`, dispatch Celery task.
5. Celery runs `mineru -b {pipeline|vlm-auto-engine}` via subprocess (no timeout).
6. Results stored in DB (`result_md`, `result_json`). Auto-expire after 24 hours.

Gunicorn worker timeout is disabled (`timeout = 0`). App polls server every 5 seconds until completion.

### Configuration

Server settings load from `server/.env`:
- `DJANGO_SECRET_KEY`, `DJANGO_DEBUG`, `DJANGO_ALLOWED_HOSTS`
- MinerU mode is determined per-request via `mode` parameter (not a server-wide setting).

## Language

The project owner writes in Korean. UI strings, comments, and task descriptions are in Korean.

## Code Style

- **Kotlin**: ktlint (android_studio style). Max 100 chars per line. `wildcard-imports`, `package-name`, `function-naming` rules disabled. Run `./gradlew ktlintFormat` before committing.
- **Python**: Use `uv` for all package operations. Never `pip` or `python` directly.
