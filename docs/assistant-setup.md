# Assistente Lilith (Google Gemini, gratuito)

Lilith risponde in linguaggio naturale e agisce sui dati dell'hub: meteo (anche dei prossimi
7 giorni), eventi del calendario di qualsiasi giorno, task (elenco, creazione, completamento,
modifica) e sveglia. Si apre dal pulsante luminoso in basso a destra della dashboard (o con il
tasto `/`) e dal pulsante **Lilith** nell'app Android.

## 1. Chiave API gratuita

1. Apri <https://aistudio.google.com/apikey> e accedi con il tuo account Google.
2. **Create API key** (se richiesto, crea un progetto nuovo). Non serve carta di credito.
3. Copia la chiave.

## 2. Configurazione sul PC hub

In `hub-server\.env` aggiungi:

```text
GEMINI_API_KEY=la-tua-chiave
GEMINI_MODEL=gemini-2.5-flash
ASSISTANT_NAME=Lilith
```

Poi riavvia il server:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\update.ps1
```

## 3. Prova

Sulla dashboard apri Lilith e scrivi: `aggiungi un task per domani: comprare il latte`.
Deve comparire la conferma verde "Task creato" e il task con la scadenza di domani.

## Note

- Piano gratuito: ci sono limiti di richieste al minuto e al giorno, ampiamente sufficienti per
  uso personale. Se li superi Lilith risponde "Ho raggiunto il limite gratuito di Gemini".
- Se Google rinomina o ritira il modello, cambia `GEMINI_MODEL` con uno elencato in AI Studio.
- Privacy: sul piano gratuito Google può usare le richieste per migliorare i suoi modelli. A
  Gemini arrivano solo i dati necessari alla domanda (titoli di eventi e task, meteo).
- Lilith non può eliminare task né creare eventi in calendario.
- La conversazione resta in memoria sul server per 2 ore di inattività; **Nuova conversazione**
  la azzera.
- Log: `hub-server\logs\hub.log` (righe `Assistant tool ...`).
