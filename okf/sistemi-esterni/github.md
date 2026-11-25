---
type: "External System"
title: "GitHub"
description: "Il repo CerroneDonato/NEXUS: chi legge, chi fa push, modifiche arrivate da altre strade."
tags: ["github", "git"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:30:00Z" }
resource: "https://github.com/CerroneDonato/NEXUS"
sources:
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
  - id: build-zip
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/build_zip.ps1"
  - id: applica
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/applica_modifiche.ps1"
---

# GitHub

- Repo `CerroneDonato/NEXUS`, ramo `main`.
- Dal PC di sviluppo `git fetch` funziona, il push no: la rete aziendale risponde HTTP 403. L'utente ha scelto lo zip: niente tunnel, VPN, caricamenti da browser o API.[^audit]
- Il push lo fa l'hub con `applica_modifiche.ps1`, con commit `NEXUS <versione>`.[^applica] Ogni altro commit (un altro PC, una pull request) va portato sul PC di sviluppo prima del prossimo zip (o unito a mano, poi `-Merged`): `build_zip.ps1` e l'hub lo controllano ([Consegna con lo zip](../procedure/consegna-zip.md)).[^build-zip]
