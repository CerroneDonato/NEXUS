---
type: "Playbook"
title: "Consegna con lo zip"
description: "Dal PC di sviluppo all'hub e a GitHub: build_zip.ps1, Google Drive, applica_modifiche.bat."
tags: ["consegna", "zip", "github"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: build-zip
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/build_zip.ps1"
  - id: applica
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/applica_modifiche.ps1"
  - id: audit
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/aidlc-docs/audit.md"
---

# Consegna con lo zip

Dal PC di sviluppo il push è bloccato: le modifiche viaggiano in uno zip che l'hub applica, committa e pubblica.[^audit]

## Sul PC di sviluppo

1. Alza la patch in `VERSION` ([Versioni](../regole/versioni.md)); se cambia l'interfaccia rifai i [mockup](mockup.md).
2. Cancella le cartelle temporanee nel repo: lo zip prende anche i file nuovi non ignorati.[^build-zip]
3. Esegui `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\windows\build_zip.ps1`: crea `NEXUS-modifiche.zip` con i file diversi da GitHub `main`, `VERSION`, `NEXUS-base.txt` (il commit di partenza) e, se serve, `NEXUS-cancellati.txt`.[^build-zip]
4. Con una versione nuova compila l'[APK](build-apk.md). L'utente porta lo zip sull'hub con Google Drive.[^audit]

Gli zip si fanno solo su questo PC: in `.git/nexus-zip-base` lo script ricorda la base dell'ultimo.[^build-zip]

## Se build_zip.ps1 si ferma

Lo zip porta file interi: un file vecchio di qui annullerebbe le modifiche arrivate su GitHub da altre strade. Se un commit GitHub successivo all'ultimo zip, e non chiamato `NEXUS <versione>`, ha cambiato un file che qui è diverso, lo script lo elenca ed esce con 1.[^build-zip]

- File non toccato qui: `git checkout origin/main -- <file>` e rilancia.
- File cambiato da entrambe le parti: unisci a mano e rilancia con `-Merged`.[^build-zip]

## Sull'hub

L'utente trascina lo zip su `scripts\windows\applica_modifiche.bat` (doppio clic: lo zip più recente in Download).[^applica] Lo script:

- non applica niente con modifiche locali, percorsi vietati, versione più bassa, base sconosciuta, o un file dello zip cambiato su GitHub dopo lo zip da un commit non `NEXUS` (allora lo zip va rifatto);
- fa il fast-forward da GitHub, estrae i file, cancella quelli elencati, fa il commit `NEXUS <versione>` e il push (senza rete ritenta la volta dopo);
- aggiorna le dipendenze se cambia `hub-server/requirements.txt` e riavvia NEXUS.

Esce con 0 se va tutto bene, 1 se rifiuta o fallisce, 2 se il push non è riuscito.[^applica]
