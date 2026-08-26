# Model publishing sources

The Android ONNX/OCR files and iOS CoreML packages live here so the APK and IPA
can ship without bundled model assets. `scripts/build-model-assets.mjs`
packages them for the models Worker.
