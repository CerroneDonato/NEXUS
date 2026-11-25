---
type: "Component"
title: "Dashboard"
description: "La pagina web a schermo intero servita dall'hub e aperta in Edge kiosk."
tags: ["dashboard", "web", "kiosk"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: app-js
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/static/app.js"
  - id: common
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/common.ps1"
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# Dashboard

HTML, CSS e JavaScript senza build in `hub-server/static/` (logica in `app.js`, testi in `i18n.js`), serviti dal [server](hub-server.md) su `/`.[^app-js]

- **Accesso**: il token dell'API, chiesto o letto da `#token=...`, resta salvato nel browser.[^app-js]
- **Contenuto**: meteo, previsioni, agenda della settimana, task, chat di [Lilith](lilith.md), impostazioni con sync e info.[^app-js][^audit]
- **Aggiornamento**: `/api/revision` ogni 4 secondi; tutto ricaricato ogni minuto o dopo una pausa.[^app-js]
- **Kiosk**: Edge a schermo intero, profilo in `hub-server/data/edge-kiosk`.[^common]
- **Buongiorno**: tra le 5 e le 11, una volta, una scheda solo visiva che si chiude dopo 60 secondi.[^audit]

Ha lo stesso dettaglio dell'app: una modifica a una va riportata sull'altra ([Design](../regole/design.md)).[^audit]
