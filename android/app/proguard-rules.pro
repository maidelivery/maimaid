# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# SnakeYAML uses java.beans which is not available on Android
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.introspector.MethodProperty

# SnakeYAML 2.x uses package-based logger initialization in static initializers.
# R8 minification/optimization can break this on Android and crash during YAML parsing.
# Keep the library intact because it is only used for Dan gallery sync.
-keep class org.yaml.snakeyaml.** { *; }

# ML Kit component registrars are discovered via reflection in startup ContentProvider.
# Keep constructors to prevent InvalidRegistrarException in minified builds.
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { public <init>(); }
-keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { public <init>(); }
-keep class com.google.mlkit.vision.text.internal.TextRegistrar { public <init>(); }
-keep class * implements com.google.firebase.components.ComponentRegistrar { public <init>(); }
