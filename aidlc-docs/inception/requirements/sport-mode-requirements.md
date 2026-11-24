# Sport mode - requirements

## Intent
While exercising with Bluetooth earbuds and the phone in a pocket, say "Lilith, ..." and hear the answer in the earbuds. Target devices: OnePlus Nord 2, JBL earbuds.

## Scope (phone app only, the hub and its API stay unchanged)
- FR-1 A switch "Modalità sport" on the app home starts and stops a foreground service. A permanent notification ("Lilith ti ascolta") with a "Ferma" button is shown while it runs.
- FR-2 While on, the service listens in loops (Italian, system recognizer). A sentence that starts with the name (optionally after "ehi", "ciao", "ok", "hey") is the question. The name alone makes Lilith say "Dimmi." and the next sentence within 12 seconds is the question. Any other sentence is ignored.
- FR-3 The question goes through the existing `POST /api/assistant/chat` with a hint to answer in one short sentence. The reply is read aloud; other audio is ducked while she speaks.
- FR-4 Listening pauses while the hub answers and while Lilith speaks, then resumes.
- FR-5 If a Bluetooth headset is connected, its microphone is used; otherwise the phone microphone.
- FR-6 Silence and no-match are normal and restart the loop. After 6 consecutive real failures (no network, no microphone) the service stops and says why.
- FR-7 The switch asks for microphone, notification and Bluetooth permissions the first time.

## Out of scope
- Wake word with the app closed and no notification, screen-off guarantees on every phone, hands-free start without touching the switch.
- Server changes.

## Files
- New: `Sport.kt` (WakeFilter, SportState), `SportService.kt`, `SportTest.kt`, `docs/sport-mode.md`.
- Changed: `AndroidManifest.xml`, `MainActivity.kt` (card and permissions), `Voice.kt` (silence marker made public).

## Success criteria
- WakeFilter unit tests cover: name first, filler words, name only then follow-up, follow-up expired, name inside another word, no name.
- Hub tests stay green.
- On the phone, with JBL earbuds: switch on, say "Lilith, che tempo fa?", hear the answer. Not verifiable on this PC (no Android SDK).

## Known risks to check on the phone
- Some recognizers pause other music while listening.
- Headset microphone over Bluetooth lowers music quality to call quality while active.
- OnePlus battery optimisation can stop the service: set the app to "Non ottimizzare".
