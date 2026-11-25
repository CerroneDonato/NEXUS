---
type: "External System"
title: "Google Calendar"
description: "Come l'hub legge Google Calendar: sola lettura, login, scadenza del token."
tags: ["google", "calendario", "oauth"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
resource: "https://developers.google.com/calendar"
sources:
  - id: calendar-client
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/calendar_client.py"
  - id: env
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/.env.example"
  - id: guida
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/docs/google-calendar-setup.md"
  - id: requisiti-sync
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/inception/requirements/standalone-sync-requirements.md"
---

# Google Calendar

- Lo usa solo l'hub, con `calendar.readonly`: NEXUS non crea né modifica eventi. Il telefono legge il proprio calendario.[^calendar-client][^requisiti-sync]
- Login in `hub-server/secrets/` (`GOOGLE_CLIENT_SECRET_FILE`, `GOOGLE_TOKEN_FILE`); calendari in `GOOGLE_CALENDAR_IDS`, se vuoto `primary`.[^env]
- Il login si fa sull'hub, da `hub-server`: `.\.venv\Scripts\python.exe -m app.google_auth`.[^guida]
- Con l'app OAuth in modalità Test il token può scadere dopo 7 giorni: la dashboard mostra «Token Google non valido».[^guida]
