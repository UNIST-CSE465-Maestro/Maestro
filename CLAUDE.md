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
- `model/`: `PdfDocument`, `Folder`, `InkStroke`, `StrokePoint`, `DrawingTool`, `ChatMessage`
- `repository/`: `DocumentRepository`, `AnnotationRepository`, `SettingsRepository`
- `service/`: `LlmService` (general-purpose: chat, quiz, lasso→LLM share one interface), `QuizService`

**`data/`** — Implementations.
- `repository/`: JSON file persistence (no Room). `DocumentRepositoryImpl`, `AnnotationRepositoryImpl` (kotlinx.serialization), `SettingsRepositoryImpl` (SharedPreferences).
- `remote/`: `AnthropicSseClient` (OkHttp SSE streaming for Claude API), `MaestroServerApi` (Retrofit for Django server), `TokenManager` (JWT interceptor with auto-refresh), `MaterialAnalyzerClient` (upload/poll/download).
- `local/`: `ConversationLocalDataSource` (chat history), `PdfMerger`.
- `service/`: `LlmServiceImpl`, `QuizServiceImpl`.
- `model/`: `AnthropicRequestBuilder` (JSON DSL for Anthropic API, handles text + vision multipart), DTOs.

**`ui/`** — Compose screens and components.
- `home/`: `HomeScreen` + `HomeViewModel` — document library, folder management, drag-and-drop, multi-select PDF merge.
- `viewer/`: `ViewerScreen` + `ViewerViewModel` — PDF viewer with annotation canvas, LLM sidebar, quiz generation, auto-save.
- `settings/`: `SettingsScreen` + `SettingsViewModel` — API key, server URL, JWT login/register.
- `drawing/`: `DrawingState` — central state machine for pen/eraser/lasso/crop/image overlay. Holds per-page strokes, undo/redo, zoom, selection state.
- `components/`: `StylusDrawingCanvas` (low-level MotionEvent handling, ~1800 lines — most complex file), `CanvasSection` (PDF rendering + zoom/pan), `LlmSidebar` (native Compose chat), `FloatingToolbar`, `TopAppBarSection`, `PdfPageRenderer`.
- `config/UxConfig.kt` — **All hardcoded UX values are centralized here.** Gesture thresholds, drawing parameters, layout dimensions, animation timings. Change values in one place.
- `theme/`: `Color.kt`, `Type.kt`, `Theme.kt`. Primary: Indigo #24389C.

**`di/`** — Koin modules: `AppModule` (ViewModels), `DataModule` (repositories, services, clients), `NetworkModule` (two OkHttpClients: one for Anthropic, one for Maestro server with TokenManager).

**`navigation/`** — Jetpack Navigation Compose. Routes: `Home`, `Viewer` (with pdfId/pageCount/uri args), `Settings`.

### Key Design Decisions

- **LlmService is general-purpose**, not chat-specific. Same `stream()`/`complete()` interface serves chat, quiz generation, and lasso→LLM. System prompt injected per call site.
- **JSON file persistence** over Room. Documents stored as `{doc-id}/metadata.json`, annotations as `page_N.json`. Repository interfaces allow future Room migration.
- **UxConfig centralization**: 180+ magic numbers extracted. Avoids hardcoded dp/px/ms scattered across files.
- **Dual OkHttpClient**: Anthropic API calls use a plain client. Server calls use a client with `TokenManager` interceptor for JWT auto-refresh.
- **SHA256 hash formula**: `SHA256(SHA256(file) + mode)` — used for client-side caching and server-side deduplication.

### Testing

- **Fakes over Mocks** for ViewModel tests: `FakeDocumentRepository`, `FakeSettingsRepository` (in-memory, stateful).
- **MockWebServer** for `AnthropicSseClient` tests (SSE parsing, error handling, headers).
- **MockK** only where Fakes aren't practical (e.g., `PdfMerger` needs Android `Context`).
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
- `GET /api/v1/health` — Server + MinerU availability.

### Processing Flow

1. Client computes `SHA256(SHA256(file) + mode)`, sends with upload.
2. Server re-computes hash (chunked, no full-file memory load), verifies match.
3. Server checks cache: existing completed task with same hash+mode → return immediately.
4. Cache miss → save file, create `AnalysisTask`, dispatch Celery task.
5. Celery runs `mineru -b {pipeline|vlm-auto-engine}` via subprocess.
6. Results stored in DB (`result_md`, `result_json`). Auto-expire after 24 hours.

### Configuration

Server settings load from `server/.env`:
- `DJANGO_SECRET_KEY`, `DJANGO_DEBUG`, `DJANGO_ALLOWED_HOSTS`
- MinerU mode is determined per-request via `mode` parameter (not a server-wide setting).

## Language

The project owner writes in Korean. UI strings, comments, and task descriptions are in Korean.

## Code Style

- **Kotlin**: ktlint (android_studio style). Max 100 chars per line. `wildcard-imports`, `package-name`, `function-naming` rules disabled. Run `./gradlew ktlintFormat` before committing.
- **Python**: Use `uv` for all package operations. Never `pip` or `python` directly.
