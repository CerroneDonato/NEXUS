---
type: "External System"
title: "Google Gemini"
description: "Il modello di Lilith: endpoint, chiave gratuita, modello predefinito."
tags: ["google", "gemini"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
resource: "https://ai.google.dev/gemini-api/docs"
sources:
  - id: env
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/.env.example"
  - id: assistant
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/assistant.py"
  - id: sync
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/sync.py"
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# Google Gemini

- Endpoint `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, chiamato dall'hub e direttamente dal telefono.[^assistant]
- La chiave è quella gratuita dell'utente: `GEMINI_API_KEY` nel `.env` dell'hub, che la passa al telefono con la sync.[^env][^sync][^audit]
- Modello predefinito `gemini-3.5-flash-lite` con ragionamento `minimal` (`GEMINI_MODEL`, `GEMINI_THINKING`); il modello si cambia anche dalle impostazioni.[^env]
- Tutto deve restare gratuito: le domande frequenti hanno [risposte rapide](../componenti/lilith.md) senza Gemini.[^audit]
