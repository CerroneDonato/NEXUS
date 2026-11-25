---
type: "Component"
title: "Server dell'hub"
description: "Il server FastAPI del PC hub: avvio, configurazione, API, sveglia."
tags: ["hub", "fastapi", "api"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: main
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/main.py"
  - id: env
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/.env.example"
  - id: run
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/run.py"
  - id: common
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/common.ps1"
  - id: wake
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/wake.py"
  - id: announce
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/announce.py"
---

# Server dell'hub

App FastAPI in `hub-server/app/`, avviata da `hub-server/run.py`.[^run] La dashboard avvia il server se è spento; poi resta acceso per le sync del telefono.[^common]

- `hub-server/.env` esiste solo sull'hub; il modello è `.env.example` (porta 8765, fuso, meteo, Google, sveglia, Gemini).[^env]
- Le impostazioni cambiate da dashboard o app vanno nel database in `hub-server/data/`, mai in `.env`.[^main] Log in `hub-server/logs/`.[^run]

## API

Ogni `/api/*` tranne `/api/health` vuole `Authorization: Bearer <HUB_API_TOKEN>`; elenco completo su `/docs`.[^main]

| Rotta | Uso |
| --- | --- |
| `GET /api/today` | Meteo, eventi di 7 giorni, task, sveglia. |
| `GET`, `PUT /api/alarm` | Sveglia del telefono. |
| `POST /api/sync` | [Sincronizzazione](sincronizzazione.md). |
| `GET /api/revision` | Modifiche recenti, per la [dashboard](dashboard.md). |
| `GET /api/info`, `PUT /api/settings` | Versione, indirizzo, registro dei sync; impostazioni. |
| `/api/tasks`, `/api/assistant/...` | Task e [Lilith](lilith.md). |

## Sveglia e rete

- A ogni sveglia nuova programma il task «DailyHub Wake» `WAKE_LEAD_MINUTES` prima, con `scripts/windows/schedule_wake.ps1`; all'avvio lo ripristina.[^wake][^main]
- Si annuncia via mDNS come `_nexus._tcp`.[^announce]
