# Maestro Experiment Logs

This document describes the logs currently collected by the Maestro Android app for KT integration and domain-specific experiments.

The app currently writes two local JSON log stores:

- `filesDir/study_events/events.json`
- `filesDir/monitoring/logs.json`

`StudyEvent` is the learning-state event stream used by the knowledge dashboard and heuristic/KT tracing. `MonitoringLogEntry` is the experiment monitoring stream shown in the Profile page under `Experiment Monitoring`.

## 1. Monitoring Log Schema

Stored by `MonitoringLogLocalDataSource`.

```json
{
  "id": "uuid",
  "category": "LEARNING_BEHAVIOR",
  "eventType": "quiz_answered",
  "timestamp": 1777561234567,
  "documentId": "pdf_id",
  "conceptId": "em_statics_equilibrium",
  "metadata": {
    "key": "value"
  }
}
```

Common fields:

- `id`: unique UUID for deleting specific log rows.
- `category`: one of `DEVICE_RESOURCE`, `KT_RUNTIME`, `LEARNING_BEHAVIOR`, `DOMAIN_EVALUATION`, `UX_RELIABILITY`.
- `eventType`: detailed event name.
- `timestamp`: Unix timestamp in milliseconds.
- `documentId`: related PDF id when available.
- `conceptId`: related engineering mechanics concept id when available.
- `metadata`: event-specific string key/value data.

The monitoring store keeps at most the latest 1000 entries.

## 2. Study Event Schema

Stored by `StudyEventLocalDataSource`.

```json
{
  "id": "uuid",
  "type": "QUIZ_ANSWERED",
  "timestamp": 1777561234567,
  "documentId": "pdf_id",
  "pageIndex": 3,
  "conceptIds": ["em_beam_shear_moment"],
  "correctness": true,
  "promptLength": 15320,
  "metadata": {
    "bloomLevel": "3"
  }
}
```

Study event types:

- `DOCUMENT_OPENED`
- `PAGE_VIEWED`
- `BOOKMARK_TOGGLED`
- `ANNOTATION_SAVED`
- `LLM_REQUESTED`
- `QUIZ_REQUESTED`
- `QUIZ_ANSWERED`

These events are used for:

- document activity counts
- studied document count
- recent activity count
- LLM/quiz counters
- heuristic mastery estimation
- KT input sequences when a KT ONNX model is uploaded

## 3. Learning Behavior Logs

Category: `LEARNING_BEHAVIOR`

These are written to `monitoring/logs.json` from `ViewerViewModel`.

### `document_opened`

Triggered when a PDF viewer is opened.

Metadata:

- `page_count`: total page count passed to the viewer.

Related `StudyEvent`:

- `DOCUMENT_OPENED`

### `page_viewed`

Triggered when the current page changes.

Metadata:

- `page_index`: zero-based page index.

Related `StudyEvent`:

- `PAGE_VIEWED`

### `annotation_saved`

Triggered when the drawing/annotation state has changed and autosave is scheduled.

Metadata:

- `page_index`: active page index at save time.

Related `StudyEvent`:

- `ANNOTATION_SAVED`

### `bookmark_toggled`

Triggered when a page bookmark is toggled.

Metadata:

- `page_index`: bookmarked/unbookmarked page index.
- `bookmarked`: `true` or `false`.

Related `StudyEvent`:

- `BOOKMARK_TOGGLED`

### `llm_requested`

Triggered when the user sends an LLM request from the sidebar.

Metadata:

- `page_index`: current viewer page.
- `prompt_length`: prompt character length.
- `has_image`: whether the request included an image crop.

Related `StudyEvent`:

- `LLM_REQUESTED`

### `quiz_generated`

Triggered when quiz generation is requested.

Metadata:

- `bloom_level`: selected/generated Bloom level.
- `mastery_before`: current local quiz mastery estimate before generation.
- `content_length`: extracted document text length used for quiz generation.

Related `StudyEvent`:

- `QUIZ_REQUESTED`

### `quiz_answered`

Triggered when the user selects an answer for a generated quiz.

Metadata:

- `bloom_level`
- `is_correct`
- `response_time_ms`
- `question_hash`
- `mastery_before`
- `mastery_after`

Related `StudyEvent`:

- `QUIZ_ANSWERED`

## 4. KT Runtime Logs

Category: `KT_RUNTIME`

These are written by `OnnxRektKnowledgeTracer`.

Important: these logs are generated only when a KT ONNX file has been uploaded through the Profile page and the knowledge tracer runs with that model. If no KT ONNX exists, the app falls back to `HeuristicKnowledgeTracer` and KT runtime/device resource logs are not produced.

The KT ONNX file is stored under:

- `filesDir/models/kt_model.onnx`

### `kt_inference_requested`

Triggered immediately before ONNX Runtime inference.

Metadata:

- `model_type`: currently `kt_onnx`.
- `input_count`: number of trace inputs.
- `sequence_event_count`: total number of study events across all sequences.
- `model_path`: local model file path.
- `before_battery_percent`
- `before_heap_used_mb`
- `before_heap_max_mb`
- `before_system_avail_mb`
- `before_system_low_memory`
- `before_app_cpu_time_ms`

### `kt_inference_completed`

Triggered after successful ONNX Runtime inference.

Metadata:

- `model_type`: currently `kt_onnx`.
- `latency_ms`: wall-clock inference duration.
- `output_count`: number of returned mastery results.
- `average_mastery`: average mastery across returned results.
- `after_battery_percent`
- `after_heap_used_mb`
- `after_heap_max_mb`
- `after_system_avail_mb`
- `after_system_low_memory`
- `after_app_cpu_time_ms`

### `kt_inference_failed`

Category: `UX_RELIABILITY`

Triggered if ONNX inference fails and the app falls back to the heuristic tracer.

Metadata:

- `model_type`: currently `kt_onnx`.
- `fallback`: currently `heuristic`.
- `error`: exception message or exception class name.

## 5. Device Resource Logs

Category: `DEVICE_RESOURCE`

### `kt_resource_sample`

Triggered after successful KT ONNX inference.

This log is intentionally collected only for KT ONNX inference, not for every user action.

Metadata:

- `latency_ms`
- `battery_delta_percent`
- `heap_delta_mb`
- `app_cpu_delta_ms`
- `before_battery_percent`
- `before_heap_used_mb`
- `before_heap_max_mb`
- `before_system_avail_mb`
- `before_system_low_memory`
- `before_app_cpu_time_ms`
- `after_battery_percent`
- `after_heap_used_mb`
- `after_heap_max_mb`
- `after_system_avail_mb`
- `after_system_low_memory`
- `after_app_cpu_time_ms`

Resource sampler details:

- Battery: `BatteryManager.BATTERY_PROPERTY_CAPACITY`
- App heap: `Runtime.totalMemory() - Runtime.freeMemory()`
- Max heap: `Runtime.maxMemory()`
- System available memory: `ActivityManager.MemoryInfo.availMem`
- Low memory flag: `ActivityManager.MemoryInfo.lowMemory`
- App CPU time: `Process.getElapsedCpuTime()`

## 6. Domain-Specific Evaluation Logs

Category: `DOMAIN_EVALUATION`

### `kt_prediction_observed`

Triggered when a quiz is answered.

Current purpose: capture a simple prediction-vs-outcome signal before a full pre/post-test protocol exists.

Metadata:

- `predicted_mastery_before`: mastery estimate before the answer.
- `actual_correctness`: quiz correctness as `true` or `false`.
- `prediction_error`: absolute error between `predicted_mastery_before` and observed correctness encoded as 1.0 or 0.0.
- `bloom_level`: Bloom level of the answered question.

This is not yet a full domain evaluation protocol. Later experiments should add explicit pre-test/post-test events and concept-level held-out test items.

## 7. UX / Reliability Logs

Category: `UX_RELIABILITY`

### `model_uploaded`

Triggered when an ONNX file is uploaded from the Profile page.

Metadata:

- `model_type`: `KT_ONNX` or `CONCEPT_ONNX`.
- `file_size_bytes`
- `file_path`

### `model_upload_failed`

Triggered when model upload fails.

Metadata:

- `model_type`
- `error`

### `kt_inference_failed`

Triggered when uploaded KT ONNX inference fails.

Metadata:

- `model_type`
- `fallback`
- `error`

## 8. Engineering Mechanics Concept Set

The current app uses a fixed temporary concept catalog for engineering mechanics.

Concept ids:

- `em_statics_equilibrium`: Statics and equilibrium
- `em_truss_frame`: Trusses and frames
- `em_friction`: Friction
- `em_centroid_inertia`: Centroids and moments of inertia
- `em_stress_strain`: Stress and strain
- `em_axial_torsion`: Axial loading and torsion
- `em_beam_shear_moment`: Beam shear and bending moment
- `em_beam_stress_deflection`: Beam stress and deflection
- `em_kinematics`: Kinematics
- `em_kinetics`: Kinetics, work, energy, and momentum

Current document-to-concept assignment:

- The app scans the document title and extracted `content.md`.
- Concepts are scored by keyword matches.
- Up to 4 matched concepts are assigned per document.
- If no keyword matches, the app assigns the best-match concept from the catalog.

This is a placeholder until the uploaded concept ONNX model is wired into the document concept classifier pipeline.

## 9. Knowledge Dashboard Derivations

The Profile dashboard derives:

- document activity count from `StudyEvent`s
- studied document count from unique `documentId`s in `StudyEvent`s
- LLM request count from `LLM_REQUESTED`
- quiz count from `QUIZ_REQUESTED` and `QUIZ_ANSWERED`
- recent activity count from events in the last 7 days
- document-level knowledge from assigned document concepts
- concept-level knowledge from the engineering mechanics concept catalog and related study events

Strong and weak concepts are not random placeholders.

- Strong concepts: concepts with `confidence > 0`, sorted by highest mastery.
- Weak concepts: concepts with `confidence > 0`, sorted by lowest mastery.

When no meaningful study/quiz events exist, these sections may be empty or low-confidence.

## 10. Current Limitations

- Device resource logging currently samples only around KT ONNX inference.
- The uploaded `CONCEPT_ONNX` model is stored but not yet used for inference.
- Domain evaluation currently uses quiz correctness as the observed outcome; explicit pre/post-test events are not yet implemented.
- Logs are local-only JSON files and are not exported to a server.
- Monitoring logs store only string metadata, so numeric analysis requires parsing values from strings.
