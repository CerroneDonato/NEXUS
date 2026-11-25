---
type: "External System"
title: "Google Calendar"
description: "Come l'hub legge Google Calendar: sola lettura, login, scadenza del token."
tags: ["google", "calendario", "oauth"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-25T14:45:00Z" }
resource: "https://developers.google.com/calendar"
sources:
  - id: calendar-client
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/calendar_client.py"
  - id: env
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/.env.example"
  - id: guida
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/docs/google-calendar-setup.md"
---

# Google Calendar

- Lo usa solo l'hub, con `calendar.readonly`: NEXUS non crea né modifica eventi. Il telefono non legge Google Calendar: riceve dal PC la settimana degli impegni in `/api/today`.[^calendar-client]
- Login in `hub-server/secrets/` (`GOOGLE_CLIENT_SECRET_FILE`, `GOOGLE_TOKEN_FILE`); calendari in `GOOGLE_CALENDAR_IDS`, se vuoto `primary`.[^env]
- Il login si fa sull'hub, da `hub-server`: `.\.venv\Scripts\python.exe -m app.google_auth`.[^guida]
- Con l'app OAuth in modalità Test il token può scadere dopo 7 giorni: la dashboard mostra «Token Google non valido».[^guida]
