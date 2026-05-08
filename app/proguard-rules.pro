# TrueBackup ProGuard rules

# Preserve stack traces
-keepattributes SourceFile,LineNumberTable

# Keep all engine classes (crypto, shell, archive)
-keep class dev.truebackup.app.engine.** { *; }

# Keep data models for JSON serialization
-keep class dev.truebackup.app.engine.BackupMetadata { *; }
-keep class dev.truebackup.app.engine.BackupParts { *; }
-keep class dev.truebackup.app.engine.PackageInfoSnapshot { *; }
-keep class dev.truebackup.app.engine.BackupConfigSection { *; }
-keep class dev.truebackup.app.engine.DataStats { *; }
-keep class dev.truebackup.app.engine.SecurityInfo { *; }
-keep class dev.truebackup.app.engine.PermissionEntry { *; }
-keep class dev.truebackup.app.engine.AppOpEntry { *; }

# Keep data repositories
-keep class dev.truebackup.app.data.** { *; }

# Keep service classes
-keep class dev.truebackup.app.service.** { *; }

# Jetpack Compose — keep all composable entry points
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Security crypto (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }

# Coil
-keep class coil.** { *; }

# JVM crypto — keep PBKDF2 and AES provider classes
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }
-keep class java.security.** { *; }