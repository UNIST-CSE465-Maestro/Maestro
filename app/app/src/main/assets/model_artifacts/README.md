# Maestro Internal Model Artifacts

Files here are bundled into the APK and copied into `files/models` on startup.

## MobileKT v4 (MIKT + TAP) stateful knowledge-tracing engine

The on-device knowledge-state engine is now **internally bundled** (no user
upload). It needs the three ONNX models exported from the MobileKT repo
(`export/`). Because `*.onnx` is git-ignored upstream, the binaries are **not**
checked in — drop them into this folder once:

- `mobile_mikt_update.onnx`   (required — updates MIKT state after an answer)
- `mobile_tap_readout.onnx`   (required — reads concept mastery from state)
- `mobile_mikt_predict.onnx`  (optional — pre-answer prediction, not used for KS)

The following support files are already bundled (initial state converted to raw
little-endian float32 `.bin` for cheap parsing on device):

- `skill_state.bin`, `all_state.bin`, `last_skill_time.bin` — initial MIKT state
  (`mobile_mikt_initial_state.npz`)
- `tap_seen_count.bin`, `tap_recent_correct_rate.bin` — initial TAP stats
  (`mobile_tap_initial_stats.npz`)
- `mikt_predict_contract.json`, `mikt_state_contract.json`,
  `tap_readout_contract.json`, `tap_backbone_compatibility.json`
- `concept_catalog.json`, `concept_id_map.json`, `qe_mikt_compatibility.json`

When `mobile_mikt_update.onnx` and `mobile_tap_readout.onnx` are present, the
app derives knowledge state purely from the TAP readout output. Without them the
knowledge dashboard shows a "추적 불가 (모델 필요)" state.

The app copies a bundled file over the stored file when the file size changes.
