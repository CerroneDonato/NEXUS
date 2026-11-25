---
type: "Policy"
title: "Stato dell'hub e segreti"
description: "Cosa non va mai nel repo, nello zip o nei log, e cosa non si sovrascrive sull'hub."
tags: ["sicurezza", "segreti"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: gitignore
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/.gitignore"
  - id: applica
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/applica_modifiche.ps1"
  - id: env
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/.env.example"
  - id: main
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/main.py"
---

# Stato dell'hub e segreti

| Solo sull'hub | Contenuto |
| --- | --- |
| `hub-server/.env` | Token, chiave Gemini, configurazione reale. |
| `hub-server/secrets/` | Login Google. |
| `hub-server/data/` | Database, profilo del browser. |
| `hub-server/.venv/`, `hub-server/logs/` | Ambiente Python e log. |

`.gitignore` esclude questi percorsi e `*.apk`, `*.keystore`, `*.jks`, `credentials*.json`, `client_secret*.json`.[^gitignore] L'hub rifiuta uno zip che li contiene, con `.git` o con percorsi fuori dal repo.[^applica]

- Una chiave nuova va in `hub-server/.env.example` con un valore finto.[^env]
- Niente token, chiavi, email o percorsi personali in codice, log, test, audit o bundle.
- Le impostazioni dell'utente vanno nel database, non in `.env`; `/api/info` mostra solo le ultime quattro cifre del token.[^main]
