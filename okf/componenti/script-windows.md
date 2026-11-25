---
type: "Component"
title: "Script Windows"
description: "Gli script in scripts/windows: cosa fanno, su quale PC girano, vincoli."
tags: ["windows", "powershell"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-25T11:20:00Z" }
sources:
  - id: common
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/common.ps1"
  - id: install
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/install.ps1"
  - id: update
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/update.ps1"
  - id: build-zip
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/build_zip.ps1"
  - id: bat
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/applica_modifiche.bat"
---

# Script Windows

| Script | PC | Cosa fa |
| --- | --- | --- |
| `install.ps1` | hub | Prima installazione: Python, dipendenze, `.env` con token; toglie il vecchio task «DailyHub Server».[^install] |
| `common.ps1` | hub | Funzioni comuni; il server avviato dalla dashboard resta acceso.[^common] |
| `show_dashboard.ps1`, `wake_and_show.ps1` | hub | Dashboard in Edge kiosk; il secondo dal task «DailyHub Wake». |
| `schedule_wake.ps1` | hub | Gestisce «DailyHub Wake» per il server. |
| `riavvia_nexus.ps1`, `.bat` | hub | Riavviano server e dashboard. |
| `update.ps1` | hub | `git pull --ff-only`, dipendenze, riavvio.[^update] |
| `applica_modifiche.bat`, `.ps1` | hub | Applicano lo zip ([consegna](../procedure/consegna-zip.md)). |
| `build_zip.ps1` | sviluppo | Crea lo zip con l'APK della versione; si ferma se GitHub ha modifiche che qui mancano, `-Merged` dopo averle unite a mano.[^build-zip] |
| `build_apk.ps1`, `.bat` | sviluppo | APK e test Android. |
| `sync_to_hub.ps1` | sviluppo | Vecchio metodo, prima dello zip. |

## Vincoli

- Windows PowerShell 5.1 (`powershell -ExecutionPolicy Bypass`), testi solo ASCII: legge come ANSI gli script senza BOM.[^bat]
- `applica_modifiche.bat` tiene comando, `pause` ed `exit /b` su una riga: lo zip può sostituirlo mentre gira.[^bat]
