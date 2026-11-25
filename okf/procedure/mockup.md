---
type: "Playbook"
title: "Mockup"
description: "Le tre immagini PNG da rifare nella radice del repo dopo ogni modifica all'interfaccia."
tags: ["mockup", "design"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
  - id: build-zip
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/build_zip.ps1"
---

# Mockup

Dopo ogni modifica all'interfaccia l'utente vuole tre immagini nella radice:[^audit] `NEXUS-mockup-pc.png` (dashboard), `NEXUS-mockup-app.png` (app) e `NEXUS-mockup-widget.png` (widget).

- Si sovrascrivono, `NEXUS-premium-*.png` no.[^audit] Se l'interfaccia non cambia valgono quelle esistenti: dillo all'utente.
- I PNG nella radice non entrano nello zip.[^build-zip]
- Finora la dashboard è stata catturata con Edge headless da un hub di prova con dati finti.[^audit] App e widget si ricostruiscono in HTML dal codice Compose e Glance, in una cartella temporanea nel repo da cancellare alla fine.
