# Vault Calculator

[中文文档](README_zh.md)

A privacy-focused calculator app with a built-in hidden vault for photos and videos, based on [LineageOS/android_packages_apps_ExactCalculator](https://github.com/LineageOS/android_packages_apps_ExactCalculator).

## Features

- **Exact Calculator** — Full-featured scientific calculator with arbitrary precision
- **Hidden Vault** — Enter a secret password on the calculator to access a private photo/video gallery
  - Import photos and videos from system gallery (originals are removed from gallery)
  - Export back to gallery when needed
  - Hidden directory (`.vault`) with `.nomedia` to prevent media scanning
  - Password-protected access with configurable password

## How to Access the Vault

1. Open the calculator
2. Type the default password: `1234`
3. Press `=` to enter the vault
4. Change the password via the settings menu in the vault

## Build

```bash
./gradlew assembleDebug
```

The debug APK will be at `build/outputs/apk/debug/ExactCalculator-debug.apk`.

## Tech Stack

- Android SDK 35 (minSdk 31)
- Kotlin + Java
- Material Design 3 (Material3Expressive)
- MediaStore API for media import/export
- `MediaStore.createDeleteRequest()` for Android 11+ scoped storage compliance

## Project Structure

```
src/com/android/calculator2/
├── Calculator.java          # Main activity with password detection
├── CalculatorExpr.java      # Expression parser with toRawString() for password matching
├── PasswordManager.kt       # Password storage with SharedPreferences
└── vault/
    ├── VaultActivity.kt     # Vault gallery UI with import/export/delete
    ├── VaultRepository.kt   # Media file management and MediaStore operations
    ├── MediaAdapter.kt      # RecyclerView adapter for media grid
    ├── MediaItem.kt         # Data model for vault items
    └── MediaViewerActivity.kt # Full-screen media viewer
```

## License

```
SPDX-FileCopyrightText: The LineageOS Project
SPDX-FileCopyrightText: 2014-2016 The Android Open Source Project
SPDX-License-Identifier: Apache-2.0
```

This project is licensed under the Apache License 2.0. The original calculator code is from the Android Open Source Project and LineageOS. The vault feature is added on top of the original Apache-2.0 licensed code.

The `com.hp.crcalc` library is Copyright (c) 1999 Silicon Graphics, Inc. — see `assets/licenses.html` for details.