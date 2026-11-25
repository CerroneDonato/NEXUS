---
type: "Playbook"
title: "Test"
description: "Come provare server e app dal PC di sviluppo, e cosa si verifica solo sull'hub o sul telefono."
tags: ["test", "pytest"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
sources:
  - id: readme
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/README.md"
  - id: conftest
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/tests/conftest.py"
  - id: build-apk
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/scripts/windows/build_apk.ps1"
---

# Test

- **Server**: da `hub-server`, la prima volta `py -3 -m venv .venv` e `.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt`, poi `.\.venv\Scripts\python.exe -m pytest`.[^readme] I test usano servizi e orologio finti: niente rete, login o `.env`.[^conftest]
- **App**: `build_apk.ps1` esegue i test unitari ([Build dell'APK](build-apk.md)).[^build-apk]
- **Solo sull'hub**: riavvio, push, login Google, accensione alla sveglia.
- **Solo sul telefono**, a casa: sync reale, ricerca del PC, widget, notifiche, microfono, modalità sport.
