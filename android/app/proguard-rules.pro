# ONNX Runtime's native bridge resolves these classes and members by their
# compiled Java names. The 1.28.0 Android AAR does not ship consumer rules.
-keep class ai.onnxruntime.** { *; }
