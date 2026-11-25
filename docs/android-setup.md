# App Android e widget (gratuito, senza Play Store)

## Compilare l'APK

Sul PC di sviluppo, dalla cartella del repo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\windows\build_apk.ps1
```

- Android Studio non serve. La prima volta lo script scarica JDK 17, Gradle e Android SDK 35 (circa 1,5 GB) in `%LOCALAPPDATA%\nexus-build`; le volte successive li riusa.
- Esegue i test unitari dell'app, poi crea `NEXUS-<versione>.apk` nella cartella del progetto. Con `-SkipTests` salta i test.
- L'APK è firmato con la chiave debug del PC di sviluppo (`%USERPROFILE%\.android\debug.keystore`). Il telefono accetta un aggiornamento solo con la stessa firma: compila sempre su quel PC e non cancellare la chiave.

## Installare sul telefono

- Copia l'APK sul telefono (Google Drive, email, cavo), aprilo e consenti «Installa app sconosciute» per l'app da cui lo apri.
- Oppure via USB, con **Opzioni sviluppatore → Debug USB** attivo: `adb install -r NEXUS-<versione>.apk`.
- Per aggiornare installa il nuovo APK sopra il vecchio: dati e impostazioni restano.

Al primo avvio l'app chiede l'accesso al calendario (per gli eventi del telefono) e, da Android 13, le notifiche (promemoria e «Buongiorno»). Il microfono lo chiede solo quando usi la voce: chat a voce o modalità sport.

## Collegare il PC

Meteo, agenda e task funzionano anche senza PC. Il PC serve per condividere task, chat e Lilith con la dashboard.

1. Sulla dashboard del PC apri rotellina → **Sync**: lì trovi indirizzo e token.
2. Nell'app apri **Impostazioni → Sync** e scrivi il **Token API**. L'**Indirizzo del PC** può restare vuoto, perché l'app cerca il PC in rete da sola; se non lo trova premi **Cerca PC in rete** o scrivi l'indirizzo (la porta 8765 si aggiunge da sola).
3. Premi **Sincronizza ora**. **Prova connessione** controlla che il PC risponda e che il token sia giusto.

Telefono e PC devono essere sulla stessa rete: vedi [network-setup.md](network-setup.md). Se app e PC hanno versioni diverse, le impostazioni lo segnalano.

## Widget

Tieni premuto sulla home → Widget → **Daily Hub** e scegli fra **Agenda e task**, **Meteo** e **Lilith e sport**.

## Come funziona la sincronizzazione

C'è una sola chiamata, `POST /api/sync`, e parte sempre dal telefono: il telefono manda quello che ha cambiato (task, chat, prossima sveglia, impostazioni) e riceve quello che è cambiato sul PC.

- In background ogni 15 minuti, solo su Wi-Fi.
- Con l'app aperta ogni 15 secondi, e circa un secondo dopo una modifica a task o chat.
- All'apertura dell'app, con **Sincronizza ora** e con il pulsante di sync del widget **Agenda e task**.

Quando imposti, modifichi o elimini una sveglia, Android avvisa l'app (`NEXT_ALARM_CLOCK_CHANGED`), che aggiorna i widget e chiede una sync appena c'è il Wi-Fi: così il PC sa quando riattivarsi. Il calendario invece non si sincronizza: il PC legge Google Calendar, il telefono il proprio calendario.

## Batteria e limiti noti

- Su alcuni telefoni (Xiaomi, Huawei, Samsung con ottimizzazione aggressiva) disattiva l'ottimizzazione batteria per Daily Hub: Impostazioni -> App -> Daily Hub -> Batteria -> **Senza restrizioni**.
- `getNextAlarmClock()` restituisce la prossima sveglia impostata da **qualsiasi** app che usa le sveglie di sistema (di solito solo l'app Orologio).
