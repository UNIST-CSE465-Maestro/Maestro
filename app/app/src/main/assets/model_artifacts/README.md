# Maestro Internal Model Artifacts

Files placed in this directory are bundled into the APK and copied into
`files/models` on app startup when their `ModelArtifactType` has
`bundledUpdateEligible = true`.

User-visible Profile uploads are intentionally limited to:

- `mobile_mikt_predict.onnx`
- `mikt_predict_contract.json`

Internal app-update artifacts can be added here later:

- `concept_id_map.json`
- `kc_mapping_contract.json`
- `qe_server_api_contract.json`
- `qe_mikt_compatibility.json`
- `export_validation.json`
- `evaluation_report.json`

Legacy compatibility artifacts can also be bundled here if the current
runtime still needs them:

- `question_id_map.json`
- `question_to_concept.json`
- `question_difficulty.json`
- `mikt_statics2011_mapping.json`

The app copies a bundled file over the stored file when the file size changes.
