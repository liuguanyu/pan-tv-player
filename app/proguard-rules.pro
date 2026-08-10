# ============================================================================
# ProGuard / R8 rules for pan-tv-player-kt
# Phase 7 (tasks.md 8.2) — R8 full mode keep rules
# 覆盖：Gson / Room / Retrofit / Hilt / Media3 / Kotlin Metadata /
#       Coroutines / ExifInterface / Geocoding JSON 反射
# 原则：仅保留反射/序列化/DI/生成代码所需，避免过度 keep。
# ============================================================================

# ----------------------------------------------------------------------------
# 1. Gson 序列化模型（反射读写字段名，必须保留 @SerializedName 与字段）
#    model 包下所有 data class：FileInfo / FileListResponse / TokenResponse /
#    DeviceCodeResponse / UserInfoResponse / AuthInfo 等。
# ----------------------------------------------------------------------------
-keep class com.baidu.tv.player.kt.model.** { *; }
# 保留 @SerializedName 注解（Gson 读取注解 value 作为 JSON key）
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod, InnerClasses

# Gson 通用：带 @SerializedName 的字段不被混淆/移除
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson TypeToken / 泛型类型签名（PlaylistRepository 解析 List<String>）
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# Gson 自身（R8 full mode 下 Gson 内部反射）
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# ----------------------------------------------------------------------------
# 2. Retrofit（运行时反射读取接口方法注解 @GET/@Query 等）
#    BaiduPanService 为 suspend 接口，Retrofit 通过 Proxy + 注解读取端点。
# ----------------------------------------------------------------------------
-keep,allowobfuscation interface com.baidu.tv.player.kt.network.BaiduPanService { *; }
-keep,allowobfuscation,allowshrinking class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# Retrofit 通用规则（官方推荐）
-keepclasseswithmembers,allowshrinking class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# OkHttp / Okio 平台相关（TLS / Conscrypt 反射）
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.bouncycastle.jsse.provider.**

# ----------------------------------------------------------------------------
# 3. Room（KSP 生成 _Impl，运行时反射调用 DAO 抽象方法）
#    实体在 model 包（PlaybackHistory / Playlist / PlaylistItem），
#    DAO 在 database 包。Room 通过列名反射读写实体字段。
# ----------------------------------------------------------------------------
-keep class com.baidu.tv.player.kt.model.PlaybackHistory { *; }
-keep class com.baidu.tv.player.kt.model.Playlist { *; }
-keep class com.baidu.tv.player.kt.model.PlaylistItem { *; }
-keep class com.baidu.tv.player.kt.database.** { *; }
# Room 生成代码
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ----------------------------------------------------------------------------
# 4. Hilt / Dagger（生成的组件类，运行时反射注入）
#    Hilt 生成的 Hilt_* / Dagger* / *_HiltComponents 类需保留。
# ----------------------------------------------------------------------------
-keep class com.baidu.tv.player.kt.BaiduTVApplication { *; }
-keep class com.baidu.tv.player.kt.BaiduTVApplication$* { *; }
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.** { *; }
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.lifecycle.** { *; }
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.managers.** { *; }
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelFactoriesEntryPoint { *; }
# Hilt 生成的注入器入口（@HiltAndroidApp / @AndroidEntryPoint）
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.AndroidEntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.lifecycle.HiltViewModel class *

# ----------------------------------------------------------------------------
# 5. Kotlin Metadata（协程 suspend 状态机、反射、Kotlin 反射 API）
#    R8 full mode 下需保留 Kotlin Metadata 以支持 suspend 续传与反射。
# ----------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepattributes KotlinMetadata
# Kotlin 协程（suspend 函数状态机）
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ----------------------------------------------------------------------------
# 6. Media3（ExoPlayer，运行时反射解码器/渲染器）
# ----------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
# Media3 通过反射加载扩展解码器（ExtensionLoader）
-keep class * extends androidx.media3.common.Player { *; }

# ----------------------------------------------------------------------------
# 7. Glide（图片加载，注解生成 + 反射）
# ----------------------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ----------------------------------------------------------------------------
# 8. ExifInterface / MediaMetadataRetriever / Geocoder（Android 系统 API）
#    这些是 Android 平台类，R8 默认保留；此处仅确保调用方不被误删。
#    geocoding 用 org.json.JSONObject（非反射，无需 keep）。
#    LocationExtractor / VideoMetadataReader 为项目类，由 Hilt 注入，无需额外 keep。
# ----------------------------------------------------------------------------
# 无需额外规则——系统 API 由 android.jar 提供，R8 不裁剪平台类。

# ----------------------------------------------------------------------------
# 9. EncryptedSharedPreferences / AndroidX Security
# ----------------------------------------------------------------------------
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ----------------------------------------------------------------------------
# 10. ZXing（QR 码生成，纯计算，无反射；保留以防 R8 内联问题）
# ----------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ----------------------------------------------------------------------------
# 11. 通用：保留枚举、Parcelable Creator、JSR250 注解
# ----------------------------------------------------------------------------
# 枚举（MediaType / PlayMode / ImageEffect）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# Parcelable Creator（@Parcelize 生成）
-keepclassmembers,allowshrinking,allowobfuscation class * {
    public static *** CREATOR;
}
-keep class * implements android.os.Parcelable {
    public static android.os.Parcelable$Creator CREATOR;
}
# JSR250 / Nullable 等运行时注解
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault

# ----------------------------------------------------------------------------
# 12. R8 full mode 优化选项
# ----------------------------------------------------------------------------
# 优化次数与重试（full mode 默认已优化，显式声明更稳定）
-optimizationpasses 5
-allowaccessmodification
# 合并接口（full mode 启用）
-overloadaggressively
# 输出混淆映射（release mapping.txt，用于 crash 符号化）
-printconfiguration
