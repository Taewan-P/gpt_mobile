# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep reactive streams dependencies
-keep class reactor.** { *; }
-keep class io.micrometer.** { *; }
-dontwarn io.micrometer.**
-dontwarn reactor.**

# AppAuth (Hugging Face OAuth redirect / token exchange)
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# LiteRT-LM (on-device inference)
# The litertlm-android AAR ships no consumer ProGuard rules, but its native layer
# resolves Java members by name through JNI. LiteRtLmJni.nativeCreateConversation
# calls SamplerConfig.getTopK()/getTopP()/getTemperature() via CallIntMethod, and
# the streaming path calls MessageCallback.onMessage/onDone/onError. R8 full mode
# strips these members because nothing in Java references them, GetMethodID
# returns null, and ART aborts the process with
# "JNI DETECTED ERROR IN APPLICATION: mid == null" on the first local message.
# Keeping the package also preserves the names of this app's MessageCallback and
# OpenApiTool implementations, which must match the interfaces they override.
-keep class com.google.ai.edge.litertlm.** { *; }

# Ignore missing optional logging dependencies used by Netty
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**

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