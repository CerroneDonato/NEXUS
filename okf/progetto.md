---
type: "Overview"
title: "Il progetto NEXUS"
description: "Cos'è NEXUS, dove gira ogni parte e dove sta cosa nel repo."
tags: ["panoramica", "architettura"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
  - id: readme
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/README.md"
  - id: requisiti-sync
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/inception/requirements/standalone-sync-requirements.md"
---

# Il progetto NEXUS

Hub personale: un PC Windows di casa mostra meteo, agenda Google e task a schermo intero, si accende alla sveglia del telefono e ospita l'assistente Lilith. Un'app Android con tre widget fa lo stesso lontano dal PC.[^readme][^requisiti-sync] Nel codice si chiama ancora «Daily Hub» (`it.cerrone.dailyhub`).

| Dove | Ruolo |
| --- | --- |
| PC di sviluppo (aziendale) | Gli agenti AI. Zscaler lascia leggere GitHub ma blocca il push. |
| PC hub (casa) | Server, dashboard, segreti e dati; applica gli zip e pubblica su GitHub. |
| Telefono | App autonoma, sincronizzata con l'hub sulla stessa rete. |

I PC sono due perché l'app gira a casa, ma i modelli AI senza limiti ci sono solo sul PC di sviluppo.[^audit]

## Mappa del repo

| Percorso | Contenuto |
| --- | --- |
| `hub-server/` | [Server](componenti/hub-server.md) e [dashboard](componenti/dashboard.md) (`static/`). |
| `android-app/` | [App Android](componenti/app-android.md). |
| `scripts/windows/` | [Script](componenti/script-windows.md). |
| `docs/` | Guide per l'utente. |
| `aidlc-docs/` | Requisiti, design e `audit.md`, il registro delle richieste. |
| `VERSION` | [Versione](regole/versioni.md) unica di server e app. |
