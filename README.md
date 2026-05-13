# TrueBackup

**TrueBackup** is an Android app that helps you **back up installed apps** and **restore them later** whenever needed.  
It stores backups in an encrypted format inside a folder you choose, making it easy to keep your apps safe before resetting your device, switching phones, or trying a new ROM.

---

## Features

### Backup Apps
- Browse installed apps
- Search apps quickly
- Optionally show system apps
- Select one or multiple apps
- Start backup with a single tap
- App list updates automatically when apps are installed, removed, or updated

### Restore Apps
- View apps that already have backups
- Search through backup entries
- Restore selected apps easily
- Backup list stays updated with device changes

### Settings
- Choose where backups are stored
- Set or change your backup password
- Re-encrypt old backups after changing passwords
- Check root status for advanced backup and restore support

---

## Getting Started

1. Open **Settings**
2. Choose a **backup folder**
3. Set a **backup password**
4. Open **Backup** and select apps to back up
5. Open **Restore** to recover apps from existing backups

If you change your password later, use the **Change password** option in Settings so older backups continue working with the new password.

---

## Permissions

TrueBackup may request:

- Storage access
- All-files access
- Notification permission

These permissions are required for managing backup files and showing backup or restore progress safely.

---

## Build Instructions

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

Install debug APK on a connected device:

```bash
./gradlew :app:installDebug
```

---

## Project Structure

Main app module:

```text
app/
```

Package name:

```text
dev.truebackup.app
```

Backup and restore related code:

```text
app/src/main/java/.../backup/
```

UI is built using **Jetpack Compose**.

---