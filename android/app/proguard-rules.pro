# ONNX Runtime's native bridge resolves these classes and members by their
# compiled Java names. The 1.28.0 Android AAR does not ship consumer rules.
-keep class ai.onnxruntime.** { *; }

# AppWidgetProvider/Glance resolve the receiver and widget implementation from
# manifest and persisted Glance state when the launcher adds or restores a widget.
# Keep these classes and their members stable in release builds.
-keep class org.rhythmeta.maimaid.widget.** { *; }
-keep class org.rhythmeta.maimaid.MaimaidApplication { *; }

# WorkManager restores this input merger by its persisted class name. R8 can
# remove its public constructor, which prevents Glance's update worker from
# starting in a minified build.
-keep class androidx.work.OverwritingInputMerger { *; }
