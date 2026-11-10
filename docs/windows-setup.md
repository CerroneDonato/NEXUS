# Setup del PC Hub (Windows)

Tutto gratuito: servono solo Python, git ed Edge (già presente in Windows).

## 1. Prerequisiti

1. Installa [Python 3.12+](https://www.python.org/downloads/windows/) (spunta "Add python.exe to PATH").
2. Installa [Git for Windows](https://git-scm.com/download/win).
3. Clona il repo (vedi README, sezione "Repo remoto"):

```powershell
cd $HOME
git clone <URL-del-tuo-repo-privato> ADayInALife
cd ADayInALife
```

## 2. Installazione

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\install.ps1
```

Lo script:
- crea `hub-server\.venv` e installa le dipendenze;
- crea `hub-server\.env` con un token casuale e lo stampa a video (ti serve per l'app Android);
- registra l'attività pianificata **DailyHub Server**, che avvia il server a ogni accesso;
- se lanciato come amministratore apre la porta 8765 nel firewall (solo reti private).

Verifica: apri `http://localhost:8765` e inserisci il token.

## 3. Google Calendar

Segui [google-calendar-setup.md](google-calendar-setup.md).

## 4. Abilitare la riattivazione automatica (wake timers)

Il PC deve restare **acceso o in sospensione**, non spento né in ibernazione profonda.

Da PowerShell (non serve amministratore per lo schema corrente):

```powershell
# Consenti i timer di riattivazione (corrente alternata e batteria)
powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_SLEEP RTCWAKE 1
powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_SLEEP RTCWAKE 1
powercfg /SETACTIVE SCHEME_CURRENT
```

In alternativa: Pannello di controllo -> Opzioni risparmio energia -> Modifica impostazioni avanzate -> Sospensione -> **Consenti timer di riattivazione** = Abilita.

Verifica che il PC supporti la riattivazione:

```powershell
powercfg /a          # stati di sospensione supportati
powercfg /waketimers # dopo aver sincronizzato una sveglia deve comparire "DailyHub Wake"
```

Consigli:
- Disattiva la richiesta di password al risveglio (Impostazioni -> Account -> Opzioni di accesso -> "Se sei stato assente" = Mai), altrimenti vedrai la schermata di blocco sopra la dashboard.
- Se usi Modern Standby (S0) e il PC non si risveglia, imposta lo spegnimento dello schermo ma **non** la sospensione: lo script riaccende comunque il monitor.

## 5. Test manuale

Esegui questi comandi **dalla cartella del repo** (quella che contiene `scripts`). `show_dashboard.ps1` crea `.env` se manca, avvia il server e apre Edge a schermo intero. Per uscire: Alt+F4.

```powershell
cd $HOME\ADayInALife
powershell -ExecutionPolicy Bypass -File scripts\windows\show_dashboard.ps1

$at = (Get-Date).AddMinutes(3).ToString('yyyy-MM-ddTHH:mm:ss')
powershell -ExecutionPolicy Bypass -File scripts\windows\schedule_wake.ps1 -At $at
```

Poi Start -> Sospendi (non Arresta il sistema). Allo scadere dei 3 minuti il PC si riattiva e la dashboard torna a schermo intero. `schedule_wake.ps1` abilita anche i timer di riattivazione di Windows.

## 6. Aggiornamenti dal PC di sviluppo

Se `git push` da questo PC risponde HTTP 403, le credenziali salvate non sono l'account `CerroneDonato`. In alternativa copia sul PC dell'hub lo zip preparato sul Desktop (`ADayInALife-NEXUS.zip`, senza `.venv` e senza `.env`) e sostituisci la cartella del repo tenendo `hub-server\.env` e `hub-server\.venv`.

## 7. Aggiornamenti con git, quando il push funziona

```powershell
# Manuale
powershell -ExecutionPolicy Bypass -File scripts\windows\update.ps1
# Automatico ogni notte alle 04:00
powershell -ExecutionPolicy Bypass -File scripts\windows\update.ps1 -RegisterNightly
```

## File e log

- Dati (task, sveglia): `hub-server\data\hub.sqlite3`
- Log: `hub-server\logs\hub.log`
- Segreti: `hub-server\.env`, `hub-server\secrets\` (mai nel repo)
