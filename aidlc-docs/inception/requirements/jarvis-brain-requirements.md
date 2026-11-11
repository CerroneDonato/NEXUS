# Tappa 2 - Cervello di Jarvis (requisiti)

## Obiettivo
Un assistente in linguaggio naturale, in italiano, che risponde a domande sulla giornata e
agisce sui dati dell'hub (task, calendario, meteo). Si usa da una chat sulla dashboard del PC
e da una schermata chat nell'app Android.

## Scelte confermate
- Motore: Google Gemini (modello Flash) via API REST, piano gratuito. Il PC hub ha 8 GB di RAM,
  quindi niente modello locale.
- Canali: chat sulla dashboard + chat nell'app Android.
- Tutto gratuito: nessuna libreria a pagamento, chiave API gratuita da Google AI Studio.

## Architettura
L'intelligenza vive solo nel server hub. Dashboard e app inviano il messaggio a un nuovo
endpoint; il server dialoga con Gemini usando il "function calling": il modello decide quale
strumento chiamare, il server lo esegue sui propri dati e restituisce il risultato al modello,
che formula la risposta finale.

```text
Dashboard / App Android
        |  POST /api/assistant/chat
        v
   Hub server  <---->  Gemini API (Google)
        |
   strumenti: meteo, calendario, task, sveglia
```

## Requisiti funzionali
- FR-1 `POST /api/assistant/chat` (protetto da token) riceve `{conversationId, message}` e
  restituisce `{reply, actions[]}`; `actions` elenca le modifiche fatte (es. "Task creato: ...")
  cosi i client sanno quando ricaricare i dati.
- FR-2 `DELETE /api/assistant/chat/{conversationId}` azzera la conversazione.
- FR-3 Memoria di conversazione in RAM: ultimi 20 messaggi per conversazione, scade dopo 2 ore
  di inattività.
- FR-4 Strumenti disponibili al modello:
  - `get_weather` - meteo attuale, prossime ore e previsione giornaliera fino a 7 giorni;
  - `list_events(date)` - eventi di un giorno qualsiasi (oggi, domani, una data);
  - `list_tasks(include_done)` - task aperti (ed eventualmente completati);
  - `create_task(title, due?)` - crea un task;
  - `complete_task(task_id)` - segna un task come fatto;
  - `update_task(task_id, title?, due?)` - modifica titolo o scadenza;
  - `get_alarm` - prossima sveglia del telefono.
  L'eliminazione dei task resta fuori dall'assistente (azione irreversibile).
- FR-5 Persona "Jarvis": italiano, tono cortese e conciso, conosce data, ora, città e fuso orario
  correnti; non inventa dati, se un'informazione manca lo dice.
- FR-6 Se `GEMINI_API_KEY` manca, l'endpoint risponde 503 "Assistente non configurato" e le
  interfacce mostrano come configurarlo.
- FR-7 Dashboard: pulsante "Jarvis" che apre un pannello chat sopra la dashboard, con storico,
  indicatore "sta pensando", pulsante per nuova conversazione; dopo un'azione la dashboard si
  aggiorna da sola.
- FR-8 App Android: nuova schermata "Jarvis" raggiungibile dalla schermata principale, stessa
  chat; dopo un'azione aggiorna il widget.

## Requisiti non funzionali
- NFR-1 Al massimo 5 giri di strumenti per messaggio; timeout di 30 s verso Gemini.
- NFR-2 Messaggi utente limitati a 2000 caratteri.
- NFR-3 La chiave sta solo in `hub-server/.env` (mai nel repo, mai nei client).
- NFR-4 Errori di rete o limiti di quota di Gemini restituiscono un messaggio chiaro in italiano,
  senza rompere la dashboard.
- NFR-5 Il meteo per i 7 giorni non deve cambiare i campi già usati da dashboard e widget.

## File previsti
Nuovi:
- `hub-server/app/assistant.py` - client Gemini, ciclo strumenti, memoria conversazioni
- `hub-server/app/assistant_tools.py` - definizione ed esecuzione degli strumenti
- `hub-server/tests/test_assistant.py` - test con un finto Gemini (nessuna chiamata reale)
- `android-app/app/src/main/java/it/cerrone/dailyhub/ChatScreen.kt` - schermata chat
- `docs/assistant-setup.md` - come ottenere la chiave gratuita e attivarla

Modificati:
- `hub-server/app/config.py`, `hub-server/.env.example` - `GEMINI_API_KEY`, `GEMINI_MODEL`
- `hub-server/app/main.py`, `hub-server/run.py` - endpoint e collegamento dei servizi
- `hub-server/app/weather.py` - previsione giornaliera a 7 giorni (campo aggiuntivo `daily`)
- `hub-server/tests/conftest.py` - finto assistente nei test
- `hub-server/static/index.html`, `app.js`, `style.css` - pannello chat
- `android-app/.../HubClient.kt`, `MainActivity.kt` - chiamata chat e accesso alla schermata
- `README.md` - riga sulla nuova funzione

## Fuori scope (tappe successive)
- Voce (microfono e risposta parlata), bot Telegram, notifiche proattive, briefing al risveglio.
- Creazione o modifica di eventi in Google Calendar (richiede un permesso OAuth più ampio).
- Eliminazione task da parte dell'assistente; memoria a lungo termine fra conversazioni.

## Criteri di successo
- AC-1 Con un finto Gemini che chiede `create_task`, la chiamata alla chat crea davvero il task
  nel database e `actions` lo riporta.
- AC-2 Con un finto Gemini che chiede `list_events` per domani, lo strumento riceve la data
  corretta e il risultato arriva al modello.
- AC-3 Senza chiave l'endpoint risponde 503; senza token risponde 401.
- AC-4 Il ciclo si ferma dopo 5 giri di strumenti con un messaggio di errore chiaro.
- AC-5 Tutti i test esistenti continuano a passare.
- AC-6 Sulla dashboard (server di prova con finto assistente) il pannello si apre, invia un
  messaggio e mostra la risposta; verificato con screenshot.
- AC-7 Verifica reale sul PC hub con la tua chiave: "aggiungi un task per domani: comprare il
  latte" crea il task con scadenza corretta. (Da fare tu, non eseguibile da qui.)
- Limite noto: l'app Android non si può compilare su questo PC; va verificata in Android Studio.

## Privacy
Sul piano gratuito Google può usare i contenuti delle richieste per migliorare i propri modelli.
Al modello arrivano solo i dati necessari alla domanda (titoli di eventi e task, meteo).
