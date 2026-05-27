# ── Hyperion Control – ProGuard / R8 Regeln ──────────────────────────────────

# Stack Traces mit Zeilennummern (erleichtert Fehlersuche in Release-Builds)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────────────
# Kotlin-Metadaten erhalten (u. a. für Reflection und Coroutines benötigt)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Jetpack Compose ───────────────────────────────────────────────────────────
# Compose generiert zur Laufzeit Lambda-Klassen; R8 darf deren Namen nicht ändern
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── AndroidViewModel / ViewModel ─────────────────────────────────────────────
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── org.json (auf Android eingebaut, kein separates Jar) ─────────────────────
-dontwarn org.json.**

# ── NsdManager / Android-Klassen ─────────────────────────────────────────────
# Android-Systemklassen sind im SDK, nicht im APK → Warnung unterdrücken
-dontwarn android.net.nsd.**
