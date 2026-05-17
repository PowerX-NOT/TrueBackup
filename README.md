# TrueBackup

**TrueBackup** is a standalone, root-privileged Android application that lets you **back up and restore installed apps** in an encrypted archive format — all without relying on any third-party binaries or cloud services.

Backups are stored as encrypted archives inside a folder of your choice, making it safe to reset your device, flash a new ROM, or migrate to a new phone without losing your apps.

---

## Screenshots

<div align="center">
	<img src="https://github.com/PowerX-NOT/TrueBackup/raw/main/screenshot/1.png" width="275px" alt="TrueBackup screenshot 1">
	<img src="https://github.com/PowerX-NOT/TrueBackup/raw/main/screenshot/2.png" width="275px" alt="TrueBackup screenshot 2">
	<img src="https://github.com/PowerX-NOT/TrueBackup/raw/main/screenshot/3.png" width="275px" alt="TrueBackup screenshot 3">
	<img src="https://github.com/PowerX-NOT/TrueBackup/raw/main/screenshot/4.png" width="275px" alt="TrueBackup screenshot 4">
	<img src="https://github.com/PowerX-NOT/TrueBackup/raw/main/screenshot/5.png" width="275px" alt="TrueBackup screenshot 5">
	<img src="https://github.com/PowerX-NOT/TrueBackup/raw/main/screenshot/6.png" width="275px" alt="TrueBackup screenshot 6">
</div>

---

## Features

### Backup Apps
- Browse all installed user apps (and optionally system apps)
- Fast in-line search across the app list
- Select one or multiple apps with a single tap
- Real-time progress UI during backup operations
- App list auto-updates when packages are installed, removed, or updated

### Restore Apps
- View and search all existing backup entries
- Restore selected apps from encrypted archives
- Backup list stays synchronized with device changes
- Detailed restore progress screen per app

### Re-encryption
- Change your backup password at any time
- Seamlessly re-encrypt all existing backups with the new password using a dedicated Re-encrypt workflow

### Settings
- Pick a custom backup destination folder (via SAF / Storage Access Framework)
- Set or update your backup password (securely stored via RegistrationPasswordStore)
- Monitor root access status with a live readiness probe
- Re-keying old backups after a password change

---

## Getting Started

1. Grant **root access** when prompted
2. Open **Settings** → choose a **backup folder** using the folder picker
3. Set a **backup password**
4. Open **Backup**, select the apps you want, and tap **Start Backup**
5. Open **Restore** to recover apps from any existing backup

> If you change your password later, use the **Change Password** option in Settings so all older backups are automatically re-encrypted to match.

---

## Tech Stack

### Language & Platform
| Component | Detail |
|-----------|--------|
| **Language** | Kotlin (`2.0.21`) |
| **Platform** | Android (minSdk **24** · targetSdk / compileSdk **36**) |
| **JVM Target** | Java 11 |

### Build System
| Component | Detail |
|-----------|--------|
| **Build Tool** | Gradle with Kotlin DSL (`build.gradle.kts`) |
| **AGP** | Android Gradle Plugin `8.13.0` |
| **Version Catalog** | `gradle/libs.versions.toml` |
| **Plugin Portal** | Google Maven · Maven Central · JitPack |

### UI Framework
| Library | Version |
|---------|---------|
| **Jetpack Compose BOM** | `2024.10.01` |
| `androidx.compose.ui` | via BOM |
| `androidx.compose.material3` | via BOM (Material You / Material 3) |
| `androidx.compose.material:material-icons-extended` | via BOM |
| `androidx.activity:activity-compose` | `1.9.3` |
| `androidx.navigation:navigation-compose` | `2.8.3` |

### Lifecycle & State Management
| Library | Version |
|---------|---------|
| `androidx.lifecycle:lifecycle-runtime-ktx` | `2.8.7` |
| `androidx.lifecycle:lifecycle-runtime-compose` | `2.8.7` |
| `androidx.core:core-ktx` | `1.10.1` |

### Data Persistence
| Library | Version |
|---------|---------|
| `androidx.datastore:datastore-preferences` | `1.1.1` |

Used for persisting backup folder URI, password hash, and app-level preferences.

### Root Access & IPC
| Library | Version |
|---------|---------|
| **libsu core** | `6.0.0` |
| **libsu service** | `6.0.0` |
| **AIDL** | Custom `IRootCommandService.aidl` + `ShellResultParcelable.aidl` |

TrueBackup uses [libsu](https://github.com/topjohnwu/libsu) by topjohnwu to spawn a persistent root shell process. An AIDL-bound `TrueBackupRootService` runs in the root context to perform all privileged filesystem operations.

### Cryptography & Archive Engine
All crypto and archive operations are implemented in pure Kotlin without any external binary dependencies:

| Component | Implementation |
|-----------|---------------|
| **Encryption** | AES/GCM via OpenSSL-compatible routines (`BackupOpenSslTarEncTree`, `OpenSslEncCompat`) |
| **Archive Format** | `tar`-based archive wrapped in `.zip` extension (`TarArchive`) |
| **Key Derivation** | PBKDF2-backed password registration and change flow |
| **Re-keying** | `PasswordChangeRekeySession` for seamless re-encryption of existing backups |

### Key Source Packages
```
dev.truebackup.app/
├── backup/         # Core backup/restore engine
│   ├── BackupInteropLayout.kt
│   ├── BackupOpenSslTarEncTree.kt
│   ├── InteropBackupIndex.kt
│   ├── LocalBackupDeletion.kt
│   ├── OpenSslEncCompat.kt
│   ├── PackageBackupConfigWriter.kt
│   ├── RootBackupInteropManager.kt
│   ├── RootRestoreInteropManager.kt
│   └── TarArchive.kt
├── crypto/         # Password & key management
│   ├── AppSettingsRepository.kt
│   ├── PasswordChangeRekeySession.kt
│   ├── RegistrationPasswordStore.kt
│   └── RootAccessRepository.kt
├── root/           # Root shell & IPC layer
│   ├── PrivilegedOperations.kt
│   ├── RootAccessProbe.kt
│   ├── RootPreflight.kt
│   ├── RootShellClient.kt
│   ├── ShellResultParcelable.kt
│   └── TrueBackupRootService.kt
├── settings/       # Settings persistence
├── ui/             # Jetpack Compose UI
│   ├── app/        # App-level composables & ViewModel
│   ├── navigation/ # NavGraph & route definitions
│   ├── screens/    # Feature screens
│   │   ├── BackupScreen.kt
│   │   ├── BackupProcessScreen.kt
│   │   ├── RestoreScreen.kt
│   │   ├── RestoreBackupDetailsScreen.kt
│   │   ├── RestoreProcessScreen.kt
│   │   ├── SettingsScreen.kt
│   │   └── ReencryptProcessScreen.kt
│   ├── theme/      # Color, typography & shape tokens
│   └── util/       # Shared UI utilities
├── MainActivity.kt
└── TrueBackupApplication.kt
```

### AIDL Interfaces
```
app/src/main/aidl/dev/truebackup/app/root/
├── IRootCommandService.aidl   # Binder interface for privileged commands
└── ShellResultParcelable.aidl # Parcelable result wrapper
```

### Testing
| Library | Version |
|---------|---------|
| `junit` | `4.13.2` |
| `androidx.test.ext:junit` | `1.1.5` |
| `androidx.test.espresso:espresso-core` | `3.5.1` |
| `androidx.compose.ui:ui-test-junit4` | via BOM |

---

## Permissions

| Permission | Reason |
|------------|--------|
| `READ_EXTERNAL_STORAGE` (≤ API 32) | Read backup archives from storage |
| `WRITE_EXTERNAL_STORAGE` (≤ API 28) | Write backup archives to storage |
| `MANAGE_EXTERNAL_STORAGE` | All-files access for backup folder management |
| `POST_NOTIFICATIONS` | Show backup/restore progress notifications |
| `QUERY_ALL_PACKAGES` | Enumerate installed apps for the backup list (API 30+) |

---

## Build Instructions

**Debug APK:**
```bash
./gradlew :app:assembleDebug
```

**Install on a connected device:**
```bash
./gradlew :app:installDebug
```

**Release APK:**
```bash
./gradlew :app:assembleRelease
```

> Requires Android Studio Hedgehog or later, or a JDK 11+ environment with the Android SDK configured.

---

## Requirements

- Android **7.0 (API 24)** or higher
- **Rooted device** with Magisk / KernelSU (root shell required for backup/restore operations)
- Storage permission or all-files access granted

---

## Package Info

| Field | Value |
|-------|-------|
| **Package name** | `dev.truebackup.app` |
| **Version** | `1.0` (versionCode 1) |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |
| **Compile SDK** | 36 |