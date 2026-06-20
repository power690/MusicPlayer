# ═══════════════════════════════════════════════════════════════
# 极限压缩 + R8 混淆配置 — 鼠音 v1.2.2
# ═══════════════════════════════════════════════════════════════

# --- 极限压缩优化 ---
# R8 全模式优化（比 ProGuard 更激进的优化）
-optimizationpasses 7
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
-overloadaggressively

# 保留源文件名和行号（崩溃堆栈可读性），但移除其他调试信息
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 隐藏原始类名（安全加固）
-renamesourcefileattribute SourceFile

# --- 应用核心类保留 ---
-keep class com.xiaowei.player.data.model.** { *; }

# Keep Application class (referenced in AndroidManifest.xml)
-keep class com.xiaowei.player.MusicApplication { *; }

# Keep all Application subclasses
-keep class * extends android.app.Application { *; }

# Keep Service classes (referenced in AndroidManifest.xml)
-keep class com.xiaowei.player.service.** { *; }

# Keep ViewModel classes
-keep class com.xiaowei.player.MainViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep Composable functions (R8 may strip seemingly unused composables)
-keepclassmembers class com.xiaowei.player.** {
    public static ** Composable(...);
}

# Keep enum classes used in state flows
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- 第三方库保留 ---

# Coil image loading — 只保留必要入口，允许内部类被优化
-dontwarn coil.**
-keep class coil.Coil { *; }
-keep class coil.ImageLoader { *; }
-keep class coil.request.** { *; }
-keep class coil.decode.** { *; }
-keepclassmembers class coil.** {
    public <methods>;
}

# Media3 / ExoPlayer — 保留公开API
-dontwarn androidx.media3.**
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }
-keepclassmembers class androidx.media3.** {
    public <methods>;
    public <fields>;
}

# Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.Dao { *; }
-keepclassmembers class * extends androidx.room.Dao {
    <methods>;
}

# Gson — 只保留必要部分
-dontwarn sun.misc.**
-keep class com.google.gson.Gson { *; }
-keep class com.google.gson.GsonBuilder { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Haze blur library
-dontwarn dev.chrisbanes.haze.**
-keep class dev.chrisbanes.haze.** { *; }

# DataStore Preferences
-dontwarn androidx.datastore.**
-keep class androidx.datastore.** { *; }

# Navigation Compose — 允许内部优化但保留路由
-keepnames class androidx.navigation.**
-keep class androidx.navigation.NavController { *; }
-keep class androidx.navigation.NavHostController { *; }

# Compose — 保留运行时必要的类
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keepclassmembers class androidx.compose.** {
    public <methods>;
}

# Lifecycle
-keepnames class androidx.lifecycle.**
-keep class androidx.lifecycle.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    public <methods>;
}

# AndroidX Core
-dontwarn androidx.core.**
-keepclassmembers class androidx.core.** {
    public <methods>;
}

# Keep Serializable/Parcelable classes in data model
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# --- 日志移除（Release 版不输出日志）---
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# --- 极限资源压缩 ---
# 移除未使用的资源（已在 build.gradle.kts 中 isShrinkResources = true）
# 以下确保压缩更激进
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.StringConcatFactory
