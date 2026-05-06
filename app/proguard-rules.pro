# =============================================================================
# Filament
# JNI 経由でアクセスされるため、リフレクション対象を全て保持。
# また native-method を持つクラスはシュリンク対象から除外する。
# =============================================================================
-keep class com.google.android.filament.** { *; }
-keep class com.google.android.filament.utils.** { *; }
-keepclassmembers class com.google.android.filament.** {
    native <methods>;
}
-dontwarn com.google.android.filament.**

# =============================================================================
# Kotlin / Compose
# Compose runtime は LiveLiterals 等を反射で参照する場合があるため
# androidx.compose.* はそのまま残す。
# =============================================================================
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**
-keep class kotlin.Metadata { *; }

# =============================================================================
# Kotlinx coroutines（StateFlow / collectAsState で使用）
# =============================================================================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# =============================================================================
# Misattitude 自身のドメイン層
# Quaternion 等は将来シリアライズで使う可能性があるため一旦 keep。
# 不要になればコメントアウトしてサイズ削減可。
# =============================================================================
-keep class io.github.takarakasai.misattitude.domain.** { *; }
