---
type: "Policy"
title: "Versioni"
description: "VERSION è l'unica versione di server e app: chi la legge, quando sale, cosa controlla l'hub."
tags: ["versioni", "rilascio"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: config
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/config.py"
  - id: main
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/main.py"
  - id: gradle
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/build.gradle.kts"
  - id: settings
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/src/main/java/it/cerrone/dailyhub/SettingsScreen.kt"
  - id: applica
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/applica_modifiche.ps1"
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# Versioni

- `VERSION`, nella radice, è la sola versione di server, dashboard e app (`maggiore.minore.patch`).[^config][^gradle]
- Il server la mostra in `/api/info` e `/api/sync`.[^config][^main] L'app ne fa `versionName` e `versionCode` = maggiore × 1.000.000 + minore × 1.000 + patch.[^gradle]
- Se app e PC hanno versioni diverse, le impostazioni dell'app lo segnalano.[^settings]
- A ogni zip la patch sale di uno, salvo diversa indicazione.[^audit] Con una versione nuova si ricompila l'[APK](../procedure/build-apk.md).
- L'hub rifiuta uno zip più vecchio e applica quello di pari versione; il commit è `NEXUS <versione>`.[^applica]
