# App Android e widget (gratuito, senza Play Store)

## Compilare l'APK

Serve [Android Studio](https://developer.android.com/studio) (gratuito) su un PC qualsiasi, per esempio il PC di sviluppo.

1. Android Studio -> **Open** -> cartella `android-app/`.
2. Al primo avvio Android Studio scarica Gradle, l'SDK Android 35 e le dipendenze. Se propone di generare il Gradle wrapper o di aggiornare le versioni, accetta.
3. **Build -> Build App Bundle(s) / APK(s) -> Build APK(s)**. L'APK si trova in `android-app/app/build/outputs/apk/debug/app-debug.apk`.

Da riga di comando, dopo che Android Studio ha generato il wrapper:

```powershell
cd android-app
.\gradlew.bat assembleDebug
.\gradlew.bat test   # unit test del parsing
```

## Installare sul telefono

- Via USB: attiva **Opzioni sviluppatore -> Debug USB** e premi Run in Android Studio, oppure `adb install app-debug.apk`.
- Senza cavo: copia l'APK sul telefono (Drive, email, ecc.), aprilo e consenti "Installa app sconosciute" per quella app.

## Configurazione

1. Apri **Daily Hub** sul telefono.
2. **Indirizzo hub**: `http://<IP-del-PC>:8765`. Usa l'IP Tailscale (es. `http://100.101.102.103:8765`) oppure l'IP di casa (es. `http://192.168.1.20:8765`). Vedi [network-setup.md](network-setup.md).
3. **Token API**: il valore `HUB_API_TOKEN` di `hub-server\.env` sul PC dell'hub.
4. Premi **Salva e verifica**: l'app invia subito al PC la prossima sveglia.
5. Aggiungi il widget: tieni premuto sulla home -> Widget -> **Daily Hub**.

## Come funziona la sincronizzazione

- Quando imposti, modifichi o elimini una sveglia, Android invia `NEXT_ALARM_CLOCK_CHANGED`; l'app la intercetta e manda l'orario al PC (`PUT /api/alarm`).
- Ogni 15 minuti (limite minimo di Android) WorkManager aggiorna il widget e risincronizza la sveglia come rete di sicurezza.
- Il pulsante **Aggiorna** sul widget forza un aggiornamento immediato.

## Batteria e limiti noti

- Su alcuni telefoni (Xiaomi, Huawei, Samsung con ottimizzazione aggressiva) disattiva l'ottimizzazione batteria per Daily Hub: Impostazioni -> App -> Daily Hub -> Batteria -> **Senza restrizioni**.
- `getNextAlarmClock()` restituisce la prossima sveglia impostata da **qualsiasi** app che usa le sveglie di sistema (di solito solo l'app Orologio).
