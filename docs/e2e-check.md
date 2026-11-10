# Verifica end-to-end

Da eseguire dopo aver completato le guide di setup.

| # | Passo | Risultato atteso |
| :-- | :-- | :-- |
| 1 | Sul PC Hub apri `http://localhost:8765` | Dashboard con meteo di Milano, eventi di oggi e task |
| 2 | Dal browser del telefono apri `http://<indirizzo>:8765/api/health` | `{"status":"ok"}` |
| 3 | Nell'app: "Salva e verifica" | Messaggio "Connesso" |
| 4 | Aggiungi un task dall'app con scadenza oggi | Compare sulla dashboard entro 60 s con etichetta "Oggi" |
| 5 | Aggiungi il widget e premi "Aggiorna" | Meteo, eventi e task visibili sul widget |
| 6 | Imposta una sveglia sul telefono fra 5 minuti | Sulla dashboard compare "Prossima sveglia" con quell'ora |
| 7 | Sul PC Hub: `powercfg /waketimers` | Elenca l'attività `DailyHub Wake` |
| 8 | Metti il PC in sospensione (Start -> Sospendi) | |
| 9 | All'ora della sveglia | Il PC si riattiva, il monitor si accende, Edge mostra la dashboard a schermo intero |
| 10 | Elimina la sveglia sul telefono | `powercfg /waketimers` non elenca più `DailyHub Wake` |

## Se qualcosa non va

- Passo 2 fallisce: firewall o IP sbagliato, vedi [network-setup.md](network-setup.md).
- Passo 6: la sveglia non arriva. Controlla l'ottimizzazione batteria dell'app oppure premi "Sincronizza sveglia ora". Nel log `hub-server\logs\hub.log` deve comparire `schedule_wake.ps1: Wake task scheduled at ...`.
- Passo 9: il PC non si riattiva. Verifica che i wake timer siano abilitati (vedi [windows-setup.md](windows-setup.md)) e che il PC fosse in sospensione e non spento. Prova `scripts\windows\schedule_wake.ps1 -At <ora fra 3 minuti>`.
- Compare la schermata di blocco: disattiva la richiesta di password al risveglio.
