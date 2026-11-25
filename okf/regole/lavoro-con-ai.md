---
type: "Policy"
title: "Lavorare con un agente AI"
description: "Come l'utente vuole che un agente lavori su NEXUS: lingua, conferma, prove, registro, limiti."
tags: ["regole", "agenti"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# Lavorare con un agente AI

- L'utente scrive in italiano: rispondi in italiano, semplice. Guide, interfaccia e bundle in italiano; codice, commenti e risposte nell'audit in inglese.[^audit]
- Per le decisioni proponi poche opzioni chiare e indica quella consigliata.[^audit]
- Prima del codice elenca i file da toccare, cosa è dentro e fuori, come si verifica; aspetta la conferma. Se poi serve altro, chiedi prima.[^audit]
- Non dire che funziona senza test: cita i risultati e cosa resta da provare, per esempio sull'hub.[^audit]
- `aidlc-docs/audit.md` registra ogni richiesta parola per parola, con ora UTC, risposta breve e contesto; si aggiunge solo in fondo.[^audit]
- Nessun file fuori dal progetto: le cartelle di prova stanno nel repo e si cancellano prima dello zip.[^audit]
- Tutto gratuito, tranne i token di Cursor. Niente schede di tracciamento (`.scaffolding/tracking`).[^audit]
