# Setup del PC Hub (Windows)

Tutto gratuito: servono solo Python, git ed Edge (già presente in Windows).

## 1. Prerequisiti

1. Installa [Python 3.12+](https://www.python.org/downloads/windows/) (spunta "Add python.exe to PATH").
2. Installa [Git for Windows](https://git-scm.com/download/win).
3. Clona il repo (vedi README, sezione "Repo su GitHub"):

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
- rimuove la vecchia attività pianificata **DailyHub Server**, se c'è: il server lo avvia la dashboard e poi resta acceso, così il telefono si sincronizza anche a dashboard chiusa;
- se lanciato come amministratore apre la porta 8765 nel firewall (solo reti private).

Verifica: apri la dashboard con `scripts\windows\show_dashboard.ps1`, che avvia il server se non è acceso, e inserisci il token.

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

Dal PC di sviluppo GitHub si legge ma il push viene bloccato dalla rete aziendale. Per questo le modifiche arrivano sull'hub come zip, e i commit su GitHub li fa l'hub.

1. Sul PC di sviluppo `scripts\windows\build_zip.ps1` crea `NEXUS-modifiche.zip` nella cartella del progetto, con i soli file cambiati rispetto all'ultima versione su GitHub.
2. Porta lo zip sull'hub, per esempio con Google Drive.
3. Sull'hub fai doppio clic su `scripts\windows\applica_modifiche.bat`: prende lo zip più recente in Download, oppure trascina lo zip sul `.bat`. Lo applica al repo, fa il commit `NEXUS <versione>`, il push su GitHub e riavvia NEXUS.

`.env`, `secrets`, `data`, `.venv` e `logs` non vengono mai toccati. Lo script non applica niente se sull'hub ci sono modifiche locali ai file del repo, se lo zip è più vecchio della versione installata, se parte da una versione di GitHub che l'hub non ha, o se dopo lo zip un commit fatto altrove su GitHub ha cambiato un file che lo zip riporterebbe indietro (in quel caso rifai lo zip sul PC di sviluppo). Dalla 1.1.7 lo zip porta anche l'APK del telefono (`NEXUS-<versione>.apk`): lo script lo mette nella cartella del progetto senza committarlo, e lo installi sul telefono. Se il push non riesce l'aggiornamento resta attivo: rilanciando lo script il push viene ritentato.

La prima volta lo script non è ancora sull'hub: copia `applica_modifiche.ps1` e `applica_modifiche.bat` dallo zip in `scripts\windows`, poi fai doppio clic. Con la 1.1.7 copia `applica_modifiche.ps1` dallo zip prima di applicarlo: la versione precedente non sa gestire l'APK.

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
