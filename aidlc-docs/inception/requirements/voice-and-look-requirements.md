# Voice chat and phone redesign - requirements

## Intent
Talk to Lilith by voice on the phone and on the PC dashboard, and give the phone app and widget the same look as the dashboard.

## Decisions (user, 2026-11-11)
- Mode: push to talk. Tap the microphone, speak, Lilith answers in the chat and reads the reply aloud.
- Where: Android app, Android widget (microphone button), PC dashboard.
- Look: same style as the dashboard: weather-driven background, glass cards, large numbers.

## Functional requirements
- FR-1 The voice turns speech into text and sends it through the existing `POST /api/assistant/chat`. The server and the API stay the same.
- FR-2 While listening, the spoken words appear live in the input field. Silence ends the turn.
- FR-3 A reply to a spoken message is read aloud in Italian. A typed message gets a text reply only.
- FR-4 Tapping the microphone while Lilith speaks stops her and listens again. Closing the chat stops both.
- FR-5 Errors are shown in Italian: microphone denied, no microphone, no speech heard, recognition service unreachable.
- FR-6 Where speech recognition is missing, the microphone button stays hidden and text chat keeps working.
- FR-7 Android asks for the microphone permission the first time it is used.
- FR-8 The widget microphone button opens Lilith already listening.
- FR-9 The phone home shows weather, next event, tasks, alarm and a Lilith button on the weather background of the moment.

## Free components
- Android: `SpeechRecognizer` and `TextToSpeech` from the system.
- Dashboard: Web Speech API in Edge (`SpeechRecognition`, `speechSynthesis`).

## Out of scope
- Wake word, always-on listening, continuous conversation.
- Server-side speech, paid voices.

## Verification
- Hub tests stay green. New Kotlin helpers covered by unit tests (not runnable on this PC: no Android SDK).
- Three mockups: PC dashboard, phone app, phone widget.
