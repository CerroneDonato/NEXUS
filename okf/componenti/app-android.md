---
type: "Component"
title: "App Android e widget"
description: "L'app Kotlin «Daily Hub»: funziona senza PC, tre widget, notifiche, modalità sport."
tags: ["android", "kotlin", "widget"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-25T11:20:00Z" }
sources:
  - id: gradle
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/build.gradle.kts"
  - id: strings
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/src/main/res/values/strings.xml"
  - id: requisiti-sync
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/inception/requirements/standalone-sync-requirements.md"
  - id: sport
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/docs/sport-mode.md"
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# App Android e widget

Progetto Gradle in `android-app/`: Kotlin, Compose, widget Glance, package `it.cerrone.dailyhub`, minSdk 26, target 35.[^gradle] Guida per l'utente: `docs/android-setup.md`.

- **Da sola**: meteo da Open-Meteo, eventi dei calendari dell'account Google scelto sul telefono, task e chat in locale, Lilith con la chiave Gemini ricevuta dal PC. Il PC serve per la [sincronizzazione](sincronizzazione.md).[^requisiti-sync]
- **Widget**: «Agenda e task», «Meteo», «Lilith e sport».[^strings] Più essenziali dell'app, ma come nei mockup.[^audit]
- **Notifiche**: 15 minuti prima di ogni evento; «Buongiorno» silenzioso 90 secondi dopo la sveglia.[^audit]
- **Modalità sport**: ascolta «Lilith» con Vosk offline (circa 50 MB, scaricato una volta) o con il riconoscimento di Google, e si spegne dopo 4 ore; qui Lilith risponde a voce.[^sport][^audit]

Test unitari in `android-app/app/src/test/`, lanciati da [Build dell'APK](../procedure/build-apk.md).
