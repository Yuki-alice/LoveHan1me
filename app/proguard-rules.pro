# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

-keepattributes SourceFile, LineNumberTable

-keepnames class * extends android.app.Activity
-keepnames class * extends androidx.fragment.app.Fragment

-keep class androidx.appcompat.view.** { *; }
-keep class androidx.window.extensions.embedding.** { *; }
# Gate3-P6 删掉三处过期 keep（都是已删引擎的遗留）：
#   -keep class is.xyz.mpv.** / -keep class lis.xyz.mpv.**   ← mpv-android，随 Android mpv 内核移除
#   -keepclasseswithmembernames class me.lovehan1me.ui.screen.video.VideoRouteHostScreenKt
#       { native <methods>; }                                ← 旧包名，且本仓该文件已无 native 方法
