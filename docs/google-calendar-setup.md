# Collegare Google Calendar (gratuito)

L'API di Google Calendar è gratuita per uso personale e **non** richiede di attivare la fatturazione.

1. Vai su [Google Cloud Console](https://console.cloud.google.com/) con il tuo account Google e crea un progetto, ad esempio `daily-hub`.
2. **API e servizi -> Libreria**: cerca "Google Calendar API" e clicca **Abilita**.
3. **API e servizi -> Schermata consenso OAuth** (Google Auth Platform):
   - tipo utente **Esterno**, nome app "Daily Hub", la tua email come supporto e sviluppatore;
   - in **Pubblico / Utenti di test** aggiungi la tua email Gmail.
   - Lascia l'app in modalità **Test**: è sufficiente per uso personale.
4. **Credenziali -> Crea credenziali -> ID client OAuth**, tipo **App desktop**. Scarica il JSON.
5. Sul PC dell'hub salva il file come `hub-server\secrets\client_secret.json`.
6. Esegui il login una volta:

```powershell
cd hub-server
.\.venv\Scripts\python.exe -m app.google_auth
```

Si apre il browser: accedi e accetta (vedrai l'avviso "app non verificata": è la tua app, clicca Avanzate -> Vai a Daily Hub). Il token viene salvato in `hub-server\secrets\google_token.json`.

7. Riavvia il server (o attendi: la cache del calendario dura 5 minuti).

## Note

- In modalità Test, Google può far scadere il refresh token dopo 7 giorni. Se la dashboard mostra "Token Google non valido", ripeti il passo 6. Per evitarlo, nella schermata consenso puoi cliccare **Pubblica app**: per un'app con un solo utente non serve la verifica Google, comparirà solo l'avviso "non verificata" al login.
- Con `GOOGLE_CALENDAR_IDS=primary` (il default) la dashboard legge tutti i calendari spuntati in Google Calendar, non solo quello principale. Per limitare l'elenco imposta gli ID espliciti: `GOOGLE_CALENDAR_IDS=primary,<id-calendario>` (l'ID si trova in Google Calendar -> Impostazioni del calendario -> Integra calendario).
