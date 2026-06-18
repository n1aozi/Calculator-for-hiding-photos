# Vault Calculator

[English](README.md)

一款带隐藏隐私相册的计算器应用，基于 [LineageOS/android_packages_apps_ExactCalculator](https://github.com/LineageOS/android_packages_apps_ExactCalculator) 开发。

## 功能特性

- **精确计算器** — 支持任意精度的全功能科学计算器
- **隐藏隐私相册** — 在计算器中输入密码即可进入私密图片/视频库
  - 从系统相册导入图片和视频（导入后原图从相册中移除）
  - 需要时可导出回系统相册
  - 使用隐藏目录（`.vault`）和 `.nomedia` 文件防止媒体扫描
  - 密码保护访问，支持自定义密码

## 如何进入隐私相册

1. 打开计算器
2. 输入默认密码：`1234`
3. 按 `=` 键进入隐私相册
4. 可在隐私相册的设置菜单中修改密码

## 构建

```bash
./gradlew assembleDebug
```

Debug APK 位于 `build/outputs/apk/debug/ExactCalculator-debug.apk`。

## 技术栈

- Android SDK 35（minSdk 31）
- Kotlin + Java
- Material Design 3（Material3Expressive）
- MediaStore API 处理媒体导入/导出
- `MediaStore.createDeleteRequest()` 适配 Android 11+ 分区存储

## 项目结构

```
src/com/android/calculator2/
├── Calculator.java          # 主界面，含密码检测逻辑
├── CalculatorExpr.java      # 表达式解析器，含 toRawString() 用于密码匹配
├── PasswordManager.kt       # 密码存储（SharedPreferences）
└── vault/
    ├── VaultActivity.kt     # 隐私相册界面，支持导入/导出/删除
    ├── VaultRepository.kt   # 媒体文件管理与 MediaStore 操作
    ├── MediaAdapter.kt      # 媒体网格 RecyclerView 适配器
    ├── MediaItem.kt         # 隐私条目数据模型
    └── MediaViewerActivity.kt # 全屏媒体查看器
```

## 开源协议

```
SPDX-FileCopyrightText: The LineageOS Project
SPDX-FileCopyrightText: 2014-2016 The Android Open Source Project
SPDX-License-Identifier: Apache-2.0
```

本项目基于 Apache License 2.0 协议开源。原始计算器代码来自 Android Open Source Project 和 LineageOS，隐私相册功能在原有 Apache-2.0 协议代码基础上开发。

`com.hp.crcalc` 库版权所有 (c) 1999 Silicon Graphics, Inc. — 详见 `assets/licenses.html`。