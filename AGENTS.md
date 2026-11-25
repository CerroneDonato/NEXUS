# NEXUS: istruzioni per gli agenti AI

NEXUS (nel codice «Daily Hub») è un hub personale: server FastAPI e dashboard su un PC Windows di casa, app Android con tre widget, assistente Lilith su Google Gemini.

**Parti da [okf/index.md](okf/index.md)**, la mappa del progetto in formato OKF 0.3, e apri solo le pagine che servono.

## Regole

- Rispondi in italiano.
- Prima del codice proponi file, perimetro e verifica, e aspetta la conferma. Niente aggiunte non concordate.
- Non dire «fatto» o «funziona» senza test o controlli eseguiti e citati.
- Mai nel repo né nello zip: `hub-server/.env`, `hub-server/secrets/`, token, chiavi. Non toccare `hub-server/data/`, `hub-server/.venv/`, `hub-server/logs/`.
- Da qui il push è bloccato: si consegna con uno zip ([procedura](okf/procedure/consegna-zip.md)). Se `build_zip.ps1` si ferma, su GitHub ci sono modifiche che qui mancano.
- A ogni zip `VERSION` sale di una patch e l'APK va ricompilato.
- Dopo ogni modifica all'interfaccia: tre mockup ([procedura](okf/procedure/mockup.md)).
- Nessun file fuori dalla cartella del progetto.
- Ogni richiesta dell'utente va copiata parola per parola in fondo a `aidlc-docs/audit.md`.

## Comandi (PowerShell, dalla radice del repo)

- Test del server: `cd hub-server; .\.venv\Scripts\python.exe -m pytest`
- APK e test Android: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\windows\build_apk.ps1`
- Zip per l'hub: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\windows\build_zip.ps1`

## Tenere aggiornato `okf/`

Se cambi qualcosa descritto in `okf/`, aggiorna la pagina (anche `generated.at`) e aggiungi una riga in `okf/log.md`. `verified` lo scrive solo l'utente.
