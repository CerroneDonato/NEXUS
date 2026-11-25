---
type: "Playbook"
title: "Build dell'APK"
description: "Compilare l'APK firmato, con i test Android, sul PC di sviluppo con build_apk.ps1."
tags: ["android", "build", "apk"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: build-apk
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/build_apk.ps1"
  - id: gradle
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/build.gradle.kts"
  - id: gitignore
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/.gitignore"
---

# Build dell'APK

Dalla radice del repo: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\windows\build_apk.ps1`

- La prima volta scarica JDK 17, Gradle 8.11.1 e Android SDK 35 (circa 1,5 GB) in `%LOCALAPPDATA%\nexus-build`.[^build-apk]
- Aggiunge al JDK di build il certificato di Zscaler, se c'è, e dopo la build ripristina `gradle/gradle-daemon-jvm.properties`.[^build-apk]
- Esegue `testDebugUnitTest` (`-SkipTests` li salta) e produce `NEXUS-<versione>.apk` nella radice, fuori da git.[^build-apk][^gitignore]

Firma: la chiave debug di questo PC (`%USERPROFILE%\.android\debug.keystore`).[^gradle] Con un'altra firma il telefono non aggiorna l'app: compila qui e non cancellare la chiave.
