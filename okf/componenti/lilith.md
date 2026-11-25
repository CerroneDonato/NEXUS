---
type: "Component"
title: "Lilith"
description: "L'assistente Gemini su PC e telefono: strumenti, risposte senza Gemini, memoria, voce."
tags: ["assistente", "gemini"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:10:00Z" }
sources:
  - id: assistant
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/assistant.py"
  - id: quick
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/quick.py"
  - id: env
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/.env.example"
  - id: requisiti-sync
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/inception/requirements/standalone-sync-requirements.md"
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# Lilith

Assistente in chat, in italiano o in inglese. Nome (`ASSISTANT_NAME`) e modello (`GEMINI_MODEL`) partono da `.env` e si cambiano dalle impostazioni.[^env]

- **Sul PC**: `hub-server/app/assistant.py` chiama [Gemini](../sistemi-esterni/gemini.md) con gli strumenti `get_weather`, `list_events`, `list_tasks`, `create_task`, `complete_task`, `update_task`, `get_alarm`. Non cancella task e non scrive nel calendario.[^assistant]
- **Risposte rapide**: `hub-server/app/quick.py` risponde senza Gemini su meteo, ombrello, agenda, task e sveglia.[^quick]
- **Sul telefono**: chiama Gemini con la chiave ricevuta dal PC; le chat si sincronizzano.[^requisiti-sync]
- **Memoria**: una conversazione riparte da zero dopo 2 ore senza messaggi.[^assistant]
- **Voce**: parla solo quando le si parla (microfono, modalità sport).[^audit] Vedi [Design](../regole/design.md).
