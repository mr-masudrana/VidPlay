# ---- NewPipeExtractor ----
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn com.google.re2j.**

# ---- protobuf (extractor-এর ভেতরে ব্যবহৃত) ----
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ---- OkHttp / Okio ----
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Chromecast ----
-keep class com.tubex.app.CastOptionsProvider { *; }

# ---- স্ট্যাক ট্রেস পড়ার সুবিধা ----
-keepattributes SourceFile,LineNumberTable
