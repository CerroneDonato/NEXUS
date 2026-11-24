"use strict";

/*
 * Interface language. The source texts are Italian: `t("testo italiano", { vars })` returns the
 * English text when the page is in English and the Italian original otherwise. A missing English
 * entry falls back to the Italian text, so nothing ever shows up blank.
 *
 * The choice is stored per device ("auto" follows the browser) and applied by reloading the page.
 */
const I18n = (() => {
  const KEY = "nexusLanguage";
  const stored = localStorage.getItem(KEY);
  const preference = stored === "it" || stored === "en" ? stored : "auto";
  const system = (navigator.language || "it").toLowerCase().startsWith("it") ? "it" : "en";
  const lang = preference === "auto" ? system : preference;

  const EN = {
    // static page
    "Inserisci il token API (HUB_API_TOKEN nel file .env).": "Enter the API token (HUB_API_TOKEN in the .env file).",
    "Entra": "Sign in",
    "Dettagli della sync": "Sync details",
    "Connessione...": "Connecting...",
    "Impostazioni (tasto I)": "Settings (key I)",
    "Impostazioni": "Settings",
    "Prossime 12 ore": "Next 12 hours",
    "Temperatura": "Temperature",
    "Agenda": "Agenda",
    "Prossimi giorni": "Coming days",
    "Aggiungi un task...": "Add a task...",
    "Aggiungi": "Add",
    "Scrivi all'assistente (tasto /)": "Write to the assistant (key /)",
    "Parla con l'assistente (tasto M)": "Talk to the assistant (key M)",
    "Tocca per chiudere": "Tap to close",
    "Chiudi (Esc)": "Close (Esc)",
    "Assistente": "Assistant",
    "Storico conversazioni": "Conversation history",
    "Nuova conversazione": "New conversation",
    "Chiedi qualcosa...": "Ask something...",
    "Parla (tasto M)": "Speak (key M)",
    "Invia": "Send",

    // greetings, time
    "ora": "now",
    "Buongiorno": "Good morning",
    "Buon pomeriggio": "Good afternoon",
    "Buonasera": "Good evening",
    "Buonanotte": "Good night",
    "Token non valido": "Invalid token",

    // weather
    "Meteo non disponibile": "Weather unavailable",
    "Min / Max": "Min / Max",
    "Percepita": "Feels like",
    "Pioggia": "Rain",
    "Vento": "Wind",
    "Alba": "Sunrise",
    "Tramonto": "Sunset",
    "Previsioni non disponibili": "Forecast unavailable",
    "Sereno": "Clear",
    "Prevalentemente sereno": "Mostly clear",
    "Parzialmente nuvoloso": "Partly cloudy",
    "Coperto": "Overcast",
    "Nebbia": "Fog",
    "Nebbia con brina": "Rime fog",
    "Pioviggine leggera": "Light drizzle",
    "Pioviggine": "Drizzle",
    "Pioviggine intensa": "Heavy drizzle",
    "Pioviggine gelata": "Freezing drizzle",
    "Pioviggine gelata intensa": "Heavy freezing drizzle",
    "Pioggia leggera": "Light rain",
    "Pioggia forte": "Heavy rain",
    "Pioggia gelata": "Freezing rain",
    "Pioggia gelata forte": "Heavy freezing rain",
    "Neve leggera": "Light snow",
    "Neve": "Snow",
    "Neve forte": "Heavy snow",
    "Nevischio": "Sleet",
    "Rovesci leggeri": "Light showers",
    "Rovesci": "Showers",
    "Rovesci violenti": "Violent showers",
    "Rovesci di neve": "Snow showers",
    "Rovesci di neve forti": "Heavy snow showers",
    "Temporale": "Thunderstorm",
    "Temporale con grandine": "Thunderstorm with hail",
    "Temporale con grandine forte": "Thunderstorm with heavy hail",

    // agenda
    "Oggi": "Today",
    "Tutto il giorno": "All day",
    "Agenda della settimana": "This week's agenda",
    "Settimana libera": "Free week",
    "{n} oggi": "{n} today",
    "Calendario non collegato": "Calendar not connected",
    "Calendario non disponibile": "Calendar unavailable",
    "Google Calendar non configurato: esegui 'python -m app.google_auth' sul PC dell'hub":
      "Google Calendar is not set up: run 'python -m app.google_auth' on the hub PC",
    "Il permesso di Google Calendar è scaduto o è stato rimosso. Rifai il login con 'python -m app.google_auth'.":
      "Google Calendar access expired or was removed. Sign in again with 'python -m app.google_auth'.",
    "Token Google non valido: ripeti 'python -m app.google_auth'": "Invalid Google token: run 'python -m app.google_auth' again",
    "Sul PC hub: python -m app.google_auth": "On the hub PC: python -m app.google_auth",
    "Nessun impegno oggi": "Nothing scheduled today",
    "Giornata libera": "A free day",
    "In corso": "Now",
    "Prossimo": "Next",
    "fino alle {time}": "until {time}",
    "Tra {time}": "In {time}",
    "ORA": "NOW",

    // tasks
    "Scaduto": "Overdue",
    "Scadenza": "Due date",
    "Salva": "Save",
    "Annulla": "Cancel",
    "{n} aperti": "{n} open",
    "Scaduti": "Overdue",
    "Aperti": "Open",
    "Tutto fatto": "All done",
    "Nessun task aperto": "No open tasks",
    "Completa": "Complete",
    "Clicca per modificare": "Click to edit",
    "Modifica": "Edit",
    "Elimina": "Delete",
    "Task": "Tasks",

    // summary
    "Agenda libera oggi.": "Nothing on the agenda today.",
    "Hai <b>{n} impegno</b> in arrivo, il prossimo alle <b>{time}</b>.": "You have <b>{n} event</b> coming up, the next at <b>{time}</b>.",
    "Hai <b>{n} impegni</b> in arrivo, il prossimo alle <b>{time}</b>.": "You have <b>{n} events</b> coming up, the next at <b>{time}</b>.",
    "Impegni di oggi completati.": "Today's events are over.",
    "<b>{n} task</b> in scadenza oggi": "<b>{n} task(s)</b> due today",
    "<b>{n}</b> scaduto": "<b>{n}</b> overdue",
    "<b>{n}</b> scaduti": "<b>{n}</b> overdue",
    " e ": " and ",
    "Nevica": "It's snowing",
    "Sta piovendo": "It's raining",
    "{what}, schiarite verso le <b>{time}</b>.": "{what}, clearing up around <b>{time}</b>.",
    "{what}: porta l'ombrello.": "{what}: take an umbrella.",
    "Pioggia probabile dalle <b>{time}</b>: porta l'ombrello.": "Rain likely from <b>{time}</b>: take an umbrella.",
    "Massima di <b>{max}°</b>, niente pioggia in vista.": "High of <b>{max}°</b>, no rain in sight.",
    "Sveglia": "Alarm",
    "altri {n}": "{n} more",
    "Agenda libera": "Nothing on the agenda",
    "{n} in scadenza oggi": "{n} due today",
    "{n} scaduto": "{n} overdue",
    "{n} scaduti": "{n} overdue",
    "Nessun task in scadenza": "No tasks due",
    "Pioggia dalle {time}: meglio l'ombrello": "Rain from {time}: better take an umbrella",
    "Niente pioggia in vista": "No rain in sight",

    // status
    "Sync {time}": "Sync {time}",
    "Nessuna sync": "No sync",
    "Aggiornato {time}": "Updated {time}",
    "Offline · riprovo tra {s} s": "Offline · retrying in {s} s",

    // assistant
    "Cosa ho in programma oggi?": "What's on today?",
    "Mi serve l'ombrello?": "Do I need an umbrella?",
    "Che task ho in scadenza?": "Which tasks are due?",
    "Com'è il meteo domani?": "What's the weather like tomorrow?",
    "Non sono ancora configurata. Sul PC hub aggiungi GEMINI_API_KEY nel file hub-server\\.env (chiave gratuita da aistudio.google.com) e riavvia il server.":
      "I'm not set up yet. On the hub PC add GEMINI_API_KEY to the hub-server\\.env file (free key from aistudio.google.com) and restart the server.",
    "Chiedi a {name}...": "Ask {name}...",
    "Microfono non autorizzato: consenti l'accesso al microfono nel browser.": "Microphone not allowed: grant microphone access in the browser.",
    "Nessun microfono trovato.": "No microphone found.",
    "Riconoscimento vocale non raggiungibile: serve la connessione a internet.": "Speech recognition unreachable: an internet connection is needed.",
    "Non ho sentito nulla, riprova.": "I didn't hear anything, try again.",
    "Ti ascolto...": "Listening...",
    "Sta parlando...": "Speaking...",
    "Assistente personale": "Personal assistant",
    "Errore del microfono ({failure}).": "Microphone error ({failure}).",
    "Qualcosa è andato storto.": "Something went wrong.",
    "Telefono": "Phone",
    "Ieri": "Yesterday",
    "Carico lo storico...": "Loading history...",
    "Storico non disponibile.": "History unavailable.",
    "Nessuna conversazione salvata. Le prossime resteranno qui.": "No saved conversations. The next ones will stay here.",
    "{n} messaggi": "{n} messages",
    "Elimina conversazione": "Delete conversation",
    "Conversazione non trovata.": "Conversation not found.",
    "Conversazione non trovata": "Conversation not found",
    "Task non trovato": "Task not found",

    // settings
    "Sync": "Sync",
    "Luogo e meteo": "Place and weather",
    "Calendario": "Calendar",
    "Promemoria e sveglia": "Reminders and alarm",
    "Lingua": "Language",
    "Info": "Info",
    "oggi {time}": "today {time}",
    "{n} task": "{n} task(s)",
    "{n} chat eliminate": "{n} chat(s) deleted",
    "niente di nuovo": "nothing new",
    "Salvato": "Saved",
    "Controlla i valori inseriti": "Check the values you entered",
    "Ultima sync riuscita": "Last successful sync",
    "Nessuna sync ricevuta": "No sync received",
    "Il telefono non ha ancora completato una sincronizzazione con questo PC": "The phone has not completed a sync with this PC yet",
    "L'ultimo tentativo ({time}) è fallito: {detail}.": "The last attempt ({time}) failed: {detail}.",
    "Copia": "Copy",
    "Indirizzo copiato": "Address copied",
    "Copia non disponibile: scrivilo a mano": "Copy not available: type it by hand",
    "Riuscita": "Succeeded",
    "Errore {status}": "Error {status}",
    "da {client}": "from {client}",
    "ricevuti: {what}": "received: {what}",
    "inviati: {what}": "sent: {what}",
    "Sincronizzato": "Synced",
    "Dati della sync non validi": "Invalid sync data",
    "Nessun tentativo è arrivato a questo PC. Se il telefono dice \"PC non raggiungibile\", controlla che sia sullo stesso Wi‑Fi, che l'indirizzo sia giusto e che il firewall di Windows lasci passare la porta {port}.":
      "No attempt has reached this PC. If the phone says \"PC unreachable\", check that it is on the same Wi‑Fi, that the address is right and that the Windows firewall lets port {port} through.",
    "Collega il telefono": "Connect the phone",
    "Indirizzo da scrivere nell'app": "Address to enter in the app",
    "Token": "Token",
    "{hint} (HUB_API_TOKEN nel file .env)": "{hint} (HUB_API_TOKEN in the .env file)",
    "Nell'app: Impostazioni → Sync. Se lasci l'indirizzo vuoto il telefono cerca il PC da solo sulla rete di casa.":
      "In the app: Settings → Sync. If you leave the address empty the phone finds the PC by itself on the home network.",
    "Ultimi tentativi": "Latest attempts",
    "Mostro solo gli ultimi 10 scambi con dati: i controlli a vuoto non vengono registrati.":
      "Only the last 10 exchanges with data are shown: empty checks are not recorded.",
    "Aggiorna": "Refresh",
    "Nome": "Name",
    "Modello Gemini": "Gemini model",
    "Stato": "Status",
    "Attiva": "Active",
    "Non configurata": "Not configured",
    "Modello": "Model",
    "Aggiungi GEMINI_API_KEY nel file hub-server\\.env (chiave gratuita da aistudio.google.com) e riavvia il server. La chiave arriva al telefono con la sync.":
      "Add GEMINI_API_KEY to the hub-server\\.env file (free key from aistudio.google.com) and restart the server. The key reaches the phone with the sync.",
    "Il nuovo nome arriva anche al telefono alla prossima sync.": "The new name also reaches the phone at the next sync.",
    "Il nome del modello Gemini usato da Lilith, per esempio gemini-3.5-flash-lite. Cambia subito sul PC e arriva anche al telefono con la sync. Se il nome non esiste Lilith lo dirà nella chat.":
      "The name of the Gemini model Lilith uses, for example gemini-3.5-flash-lite. It changes on the PC right away and reaches the phone with the sync. If the name does not exist Lilith will say so in the chat.",
    "Città": "City",
    "Latitudine": "Latitude",
    "Longitudine": "Longitude",
    "Trova coordinate": "Find coordinates",
    "Trovata: {place}. Premi Salva.": "Found: {place}. Press Save.",
    "Città non trovata": "City not found",
    "Luogo del meteo": "Weather location",
    "Vale per la dashboard, Lilith e, alla prossima sync, anche per il telefono.": "Applies to the dashboard, Lilith and, at the next sync, the phone too.",
    "Collegato": "Connected",
    "Non collegato": "Not connected",
    "Per collegarlo o rifare il login: sul PC hub apri un terminale nella cartella hub-server ed esegui .venv\\Scripts\\python -m app.google_auth, poi riavvia il server.":
      "To connect it or sign in again: on the hub PC open a terminal in the hub-server folder and run .venv\\Scripts\\python -m app.google_auth, then restart the server.",
    "Il telefono legge il calendario direttamente da Android, anche senza PC: il permesso si gestisce nell'app, Impostazioni → Calendario.":
      "The phone reads the calendar straight from Android, even without the PC: the permission is managed in the app, Settings → Calendar.",
    "Accendi lo schermo prima della sveglia (minuti)": "Turn the screen on before the alarm (minutes)",
    "Sveglia del telefono": "Phone alarm",
    "Prossima sveglia": "Next alarm",
    "Nessuna": "None",
    "Il PC si sveglia": "The PC wakes up",
    "Non programmato": "Not scheduled",
    "Il risveglio automatico è disattivato nel file .env (WAKE_ENABLED).": "Automatic wake-up is turned off in the .env file (WAKE_ENABLED).",
    "Anticipo": "Lead time",
    "Promemoria": "Reminders",
    "I promemoria degli impegni e il riepilogo del mattino arrivano sul telefono: si regolano nell'app, Impostazioni → Promemoria e sveglia.":
      "Event reminders and the morning briefing arrive on the phone: set them in the app, Settings → Reminders and alarm.",
    "Automatica (come il dispositivo)": "Automatic (like the device)",
    "Lingua dell'interfaccia": "Interface language",
    "Vale per questa dashboard e si applica subito. Il telefono ha la sua scelta nell'app, Impostazioni → Lingua.":
      "Applies to this dashboard right away. The phone has its own choice in the app, Settings → Language.",
    "Lilith": "Lilith",
    "In inglese Lilith risponde sempre tramite Gemini: le risposte rapide locali, scritte in italiano, restano spente.":
      "In English Lilith always answers through Gemini: the local quick answers, written in Italian, stay off.",
    "Versione {version}": "Version {version}",
    "Progetto": "Project",
    "Hub personale: questa dashboard sul PC, l'app e i widget sul telefono, l'assistente Lilith. Tutto resta in casa tra PC e telefono.":
      "A personal hub: this dashboard on the PC, the app and widgets on the phone, the assistant Lilith. Everything stays at home between PC and phone.",
    "Versione": "Version",
    "Server avviato": "Server started",
    "Porta": "Port",
    "Scorciatoie": "Shortcuts",
    "Apri Lilith": "Open Lilith",
    "Parla con Lilith": "Talk to Lilith",
    "Chiudi": "Close",
    "Impostazioni non disponibili: {error}": "Settings unavailable: {error}",
    "Caricamento...": "Loading...",
  };

  function t(text, vars) {
    let out = lang === "en" ? (EN[text] ?? text) : text;
    if (vars) out = out.replace(/\{(\w+)\}/g, (match, key) => (key in vars ? vars[key] : match));
    return out;
  }

  /** Translate the texts and tooltips that are written directly in index.html. */
  function translateDom(root) {
    if (lang !== "en") return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    for (const node of nodes) {
      const raw = node.nodeValue;
      const text = raw.trim();
      if (text && EN[text]) node.nodeValue = raw.replace(text, EN[text]);
    }
    for (const node of root.querySelectorAll("[title],[placeholder],[aria-label]")) {
      for (const attr of ["title", "placeholder", "aria-label"]) {
        const value = node.getAttribute(attr);
        if (value && EN[value]) node.setAttribute(attr, EN[value]);
      }
    }
  }

  function setPreference(value) {
    localStorage.setItem(KEY, value);
    location.reload();
  }

  document.documentElement.lang = lang;
  translateDom(document.body);

  return { t, lang, preference, setPreference };
})();

const t = I18n.t;
const LANG = I18n.lang;
const LOCALE = LANG === "en" ? "en-GB" : "it-IT";
const SPEECH_LANG = LANG === "en" ? "en-GB" : "it-IT";
