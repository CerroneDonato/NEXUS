# PC e telefono autonomi, sincronizzati in rete locale (requisiti)

Stato: **implementata il 19 novembre, senza scheda di tracciamento. Manca la prova sul telefono (qui non c'è l'SDK Android)**.

## Obiettivo
Eliminare l'hub sempre acceso. PC e telefono diventano due ambienti completi e indipendenti,
ognuno con i propri dati. Quando sono sulla stessa rete si sincronizzano: da soli, ogni tanto,
oppure con un pulsante. Lontano da casa il telefono funziona per intero, Lilith e modalità sport comprese.

## Scelte confermate
- In sincronizzazione passa tutto: task, storico chat, sveglia del telefono, impostazioni comuni.
- Lilith sul telefono chiama Gemini da sola, con la chiave gratuita dell'utente.
- La chiave Gemini la passa il PC alla prima sincronizzazione e ogni volta che cambia.
- Il server sul PC parte con la dashboard e si chiude con lei: niente servizio sempre attivo.
- Tutto gratuito; nessun servizio cloud nuovo.

## Architettura

```text
   Telefono (completo)                        PC (completo)
   ---------------------------                ---------------------------
   SQLite locale: task, chat, kv              SQLite locale: task, chat, kv
   Meteo: Open-Meteo diretto                  Meteo: Open-Meteo diretto
   Calendario: calendario di Android          Calendario: Google Calendar API
   Lilith: Gemini diretto + strumenti locali  Lilith: Gemini diretto + strumenti locali
   Widget e modalità sport                    Dashboard a schermo intero
              |                                          ^
              |   POST /api/sync (stessa rete, token)    |
              +------------------------------------------+
              il telefono avvia, il PC risponde; il PC si annuncia in rete (mDNS)
```

Il calendario non si sincronizza: entrambi leggono lo stesso account Google, ognuno dalla sua fonte.

## Requisiti funzionali

### Telefono autonomo
- **FR-1 Meteo locale**: il telefono chiama Open-Meteo direttamente (meteo attuale, prossime ore, 7 giorni).
- **FR-2 Calendario locale**: il telefono legge gli eventi dal calendario di Android (permesso `READ_CALENDAR`),
  per oggi e per i 7 giorni successivi. Sostituisce `/api/today` per eventi e settimana.
- **FR-3 Dati locali**: task, storico chat e impostazioni stanno in un database SQLite sul telefono.
  App, widget, promemoria e buongiorno leggono solo da lì.
- **FR-4 Lilith sul telefono**: stesse domande rapide e stessi strumenti del PC (meteo, eventi, task,
  sveglia), eseguiti sui dati locali; per il resto chiama Gemini con la chiave salvata sul telefono.
  La modalità sport usa questa Lilith, quindi funziona anche fuori casa. Come sul PC, «cosa ho da fare
  oggi» risponde con impegni e task insieme.

### Sincronizzazione
- **FR-5 Identità dei record**: ogni task e ogni messaggio ha un id globale (UUID), un `updatedAt`
  e, se eliminato, una traccia di eliminazione (non sparisce subito), così non riappare dall'altro lato.
- **FR-6 Regola dei conflitti**: per i task vince la modifica con `updatedAt` più recente; a parità,
  vince il PC. I messaggi di chat si aggiungono soltanto; eliminare una conversazione la elimina su entrambi.
  Le impostazioni comuni seguono la stessa regola per chiave.
- **FR-7 Scambio**: un'unica chiamata `POST /api/sync` dal telefono al PC. Il telefono invia le sue modifiche
  dall'ultima sincronizzazione; il PC le applica e risponde con le proprie modifiche dallo stesso momento
  (contatore delle modifiche del PC). Ripetere la stessa sincronizzazione non crea duplicati.
- **FR-8 Sveglia**: la prossima sveglia del telefono viaggia nella sincronizzazione; il PC la usa per
  programmare l'accensione, come oggi.
- **FR-9 Impostazioni comuni**: città, coordinate, nome dell'assistente e chiave Gemini. La chiave
  arriva dal PC alla prima sincronizzazione e di nuovo quando cambia nel `.env` del PC; sul telefono
  sta nelle preferenze private dell'app e non si mostra in chiaro.
- **FR-10 Quando sincronizza** (solo mentre la dashboard del PC è aperta, perché il server vive con lei):
  - all'apertura dell'app;
  - in automatico ogni 15 minuti quando il telefono è su Wi-Fi (se il PC non risponde, non succede nulla);
  - con il pulsante **Sincronizza** nell'app;
  - il PC mostra l'ora dell'ultima sincronizzazione sulla dashboard.
- **FR-13 Server legato alla dashboard**: un solo collegamento su Windows avvia il server e apre la
  dashboard a schermo intero; chiudendo la dashboard il server si ferma. All'avvio il server legge
  la sveglia ricevuta l'ultima volta e riprogramma l'accensione.
- **FR-11 Trovare il PC**: il PC si annuncia sulla rete locale (mDNS, `_nexus._tcp`); il telefono lo trova
  da solo. Resta possibile scrivere l'indirizzo a mano. Il token resta l'abbinamento tra i due.
- **FR-12 Prima sincronizzazione**: i task e lo storico già presenti sul PC ricevono un UUID durante
  l'aggiornamento del database e arrivano sul telefono alla prima sincronizzazione.

## Requisiti non funzionali
- **NFR-1** Nessun servizio a pagamento; dipendenze nuove solo gratuite (`zeroconf` per Python).
- **NFR-2** La sincronizzazione funziona solo sulla rete locale; fuori casa ogni dispositivo lavora da solo.
- **NFR-3** Una sincronizzazione con qualche centinaio di task e messaggi finisce in meno di 3 secondi.
- **NFR-4** La logica di unione (conflitti, eliminazioni, ripetizioni) è una funzione pura, con test sia
  in Python sia in Kotlin.
- **NFR-5** Mai segreti nel repo; la chiave Gemini sul telefono sta nelle preferenze private dell'app.
  Viaggia solo nella risposta di `/api/sync`, protetta dal token, sulla rete di casa.

## Tappe (ognuna lascia l'app funzionante)
1. **Telefono autonomo in lettura**: FR-1, FR-2; widget, promemoria e buongiorno senza hub.
2. **Task locali e sincronizzazione**: FR-3 (task), FR-5, FR-6, FR-7, FR-10, FR-11, FR-12.
3. **Lilith sul telefono e storico chat sincronizzato**: FR-4, FR-3 (chat), FR-6 (chat).
4. **Sveglia e impostazioni nella sincronizzazione**: FR-8, FR-9, FR-13; il telefono non chiama più
   l'hub in tempo reale.

## Fatto subito (fuori dalle tappe, richiesto il 19 novembre)
- **Risposta sulla giornata**: «cosa ho da fare oggi», «cosa ho oggi», «cosa ho in programma» rispondono
  con impegni e task insieme; «impegni» o «task» da soli restano separati. Vale per le risposte rapide e
  per Gemini (regola nel prompt).
- **Nuovo logo dell'app**: icona adattiva (sfondo, primo piano, monocromatica per le icone a tema).
- **Tre widget al posto di uno**: «Agenda e task» (prende il posto del widget esistente),
  «Meteo», «Lilith e sport».

## File previsti

### Telefono (`android-app/app/src/main/java/it/cerrone/dailyhub/`)
| File | Tappa | Cosa |
|---|---|---|
| `WeatherClient.kt` (nuovo) | 1 | Open-Meteo diretto |
| `PhoneCalendar.kt` (nuovo) | 1 | Eventi dal calendario di Android |
| `LocalToday.kt` (nuovo) | 1 | Costruisce il `TodaySnapshot` in locale |
| `LocalDb.kt` (nuovo) | 2 | SQLite: task, chat, kv, stato di sincronizzazione |
| `SyncClient.kt`, `SyncWorker.kt`, `PcFinder.kt` (nuovi) | 2 | Scambio, sincronizzazione periodica, ricerca mDNS |
| `Merge.kt` (nuovo) | 2 | Regole di unione pure |
| `PhoneAssistant.kt`, `QuickAnswers.kt`, `PhoneTools.kt` (nuovi) | 3 | Lilith sul telefono |
| `MainActivity.kt`, `ChatScreen.kt`, `widget/HubWidget.kt`, `RefreshWorker.kt`, `Reminders.kt`, `SportService.kt`, `Prefs.kt`, `Model.kt`, `AlarmSync.kt` | 1-4 | Leggono e scrivono in locale; pulsante Sincronizza; chiave Gemini nelle impostazioni |
| `HubClient.kt` | 2-4 | Si riduce alla sola sincronizzazione, poi viene sostituito da `SyncClient.kt` |
| `AndroidManifest.xml` | 1-2 | Permessi `READ_CALENDAR`, `CHANGE_WIFI_MULTICAST_STATE` |

### PC (`hub-server/`)
| File | Tappa | Cosa |
|---|---|---|
| `app/db.py` | 2 | Colonne `uid`, `updated_at`, `deleted`, contatore modifiche; aggiornamento dei dati esistenti |
| `app/tasks.py`, `app/history.py` | 2-3 | UUID, eliminazioni con traccia, contatore modifiche |
| `app/sync.py` (nuovo) | 2 | Regole di unione e scambio |
| `app/main.py` | 2-4 | `POST /api/sync`, ultima sincronizzazione nello stato |
| `app/assistant_tools.py` | 2 | `task_id` diventa l'UUID |
| `run.py`, `requirements.txt` | 2 | Annuncio mDNS con `zeroconf` |
| `static/app.js`, `static/index.html`, `static/style.css` | 2 | «Ultima sincronizzazione» sulla dashboard |
| `scripts/windows/` (avvio dashboard) | 4 | Avvia server e dashboard insieme, ferma il server alla chiusura |
| `tests/test_sync.py` (nuovo) | 2-4 | Test della sincronizzazione |

## Fuori ambito
- Sincronizzazione fuori dalla rete di casa (cloud, tunnel).
- Il PC che avvia la sincronizzazione verso il telefono.
- Creare eventi in calendario.
- Più di un telefono.
- Cifratura oltre al token sulla rete locale (come oggi).

## Criteri di riuscita (verificabili)
- **AC-1** Con il PC spento, l'app e il widget mostrano meteo, eventi della settimana e task.
- **AC-2** Con il PC spento, Lilith risponde sul telefono (chat e modalità sport) e crea un task locale.
- **AC-3** Un task creato sul telefono fuori casa compare sulla dashboard entro 15 minuti dal rientro
  (o subito con Sincronizza).
- **AC-4** Lo stesso task modificato su entrambi prima di sincronizzare: dopo la sincronizzazione
  entrambi mostrano la modifica più recente.
- **AC-5** Un task eliminato su un lato non riappare dopo la sincronizzazione.
- **AC-6** Due sincronizzazioni di fila senza modifiche non cambiano nulla e non creano duplicati.
- **AC-7** I task e lo storico già sul PC arrivano sul telefono alla prima sincronizzazione.
- **AC-8** La sveglia del telefono arriva al PC con la sincronizzazione e il PC programma l'accensione.
- **AC-9** Il telefono trova il PC da solo sulla stessa rete, senza indirizzo scritto a mano.
- **AC-10** Test Python verdi, compresi i nuovi test di sincronizzazione; test Kotlin di `Merge.kt` scritti.
- **AC-11** Dopo la prima sincronizzazione Lilith risponde sul telefono senza aver scritto la chiave a mano;
  cambiando la chiave sul PC, il telefono usa la nuova dopo la sincronizzazione successiva.
- **AC-12** Aprendo il collegamento della dashboard parte anche il server; chiudendo la dashboard
  il server non risponde più.

## Rischi
- **Lilith scritta due volte** (Python e Kotlin): le domande rapide e gli strumenti vanno tenuti allineati.
- **Orologi diversi** tra PC e telefono: la regola «più recente» si basa sull'ora; entrambi usano l'ora di rete.
- **Firewall di Windows** sulla rete «Pubblica» blocca il telefono, come per l'hub di oggi.
- **Accensione del PC**: funziona solo se il PC ha ricevuto la sveglia prima di spegnersi.
- **Android non compilabile su questo PC**: la parte telefono si verifica solo sul tuo telefono.

## Domande aperte
Nessuna: la chiave Gemini passa dal PC (FR-9) e il server vive con la dashboard (FR-13).
