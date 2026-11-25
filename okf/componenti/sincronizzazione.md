---
type: "Component"
title: "Sincronizzazione telefono-PC"
description: "Come telefono e PC si allineano con POST /api/sync: quando, cosa si scambia, chi vince."
tags: ["sync", "android", "hub"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-25T14:45:00Z" }
sources:
  - id: sync
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/sync.py"
  - id: hub-today
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/main.py"
  - id: local-today
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/src/main/java/it/cerrone/dailyhub/LocalToday.kt"
  - id: requisiti-sync
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/inception/requirements/standalone-sync-requirements.md"
  - id: worker
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/src/main/java/it/cerrone/dailyhub/SyncWorker.kt"
  - id: trigger
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/src/main/java/it/cerrone/dailyhub/SyncTrigger.kt"
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# Sincronizzazione telefono-PC

PC e telefono lavorano separati e si allineano sulla stessa rete con `POST /api/sync`, che parte sempre dal telefono.[^requisiti-sync][^audit]

- **Quando**: ogni 15 minuti in background, solo su Wi-Fi, con attese crescenti dopo un errore;[^worker] ogni 15 secondi con l'app aperta e circa un secondo dopo una modifica;[^trigger] all'apertura e con il pulsante di sync.[^requisiti-sync]
- **Il PC**: trovato via mDNS (`_nexus._tcp`) o con l'indirizzo delle impostazioni.[^requisiti-sync]
- **Scambio**: il telefono manda le sue modifiche; il PC le applica e risponde con quelle successive all'ultima revisione vista dal telefono.[^sync]

| Dati | Regola |
| --- | --- |
| Task, sveglia, impostazioni (città, coordinate, nome e modello dell'assistente) | Vince la modifica più recente; a parità il PC. |
| Messaggi di chat | Si aggiungono soltanto. |
| Conversazioni cancellate | Restano cancellate. |
| Chiave Gemini e versione | Solo dal PC al telefono. |

Il calendario non passa da questo scambio: il telefono chiede al PC `GET /api/today` e ne prende la settimana degli impegni. Se il PC non risponde, tiene l'ultima settimana ricevuta, da oggi in poi, con un avviso.[^hub-today][^local-today]

`SyncRequest` e `SyncResponse` (`hub-server/app/sync.py`) cambiano insieme a `SyncClient.kt`.[^sync] Esclusi: sync via cloud, sync avviata dal PC, più telefoni.[^requisiti-sync]
