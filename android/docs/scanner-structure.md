# Android Scanner Structure

The scanner module is organized by responsibility under
`app/src/main/kotlin/net/krtl/maimaid/scanner`.

- `analysis`: scanner orchestration. `ScannerAnalyzer` owns classification, detector selection, OCR, parsing, and final recognition selection.
- `camera`: CameraX frame conversion utilities used by the scanner UI.
- `matching`: recognition-to-song matching and sheet resolution.
- `ml`: TensorFlow Lite model wrappers for image classification and object detection.
- `model`: shared scanner data models passed between analysis, matching, and UI.
- `text`: OCR text cleanup, fallback parsing, title normalization, and fuzzy text helpers.

Model files live in one location:

- `app/src/main/assets/scanner`: packaged runtime assets loaded by Android through `AssetManager`.

When updating a model, copy the exported `.tflite` into `app/src/main/assets/scanner` using the filename
referenced by `ScannerAnalyzer`.

The former `scanner/models` provenance copy was dropped when this project moved into the maimaid monorepo —
it was byte-identical to the runtime assets, so it only doubled repository size. Keep model provenance
(training run, export settings, version) in the model release notes rather than a duplicate binary.
