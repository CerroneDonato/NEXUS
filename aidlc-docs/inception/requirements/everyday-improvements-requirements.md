# Everyday improvements - requirements

## Intent
Make NEXUS faster and more useful during the day without adding any spoken output.
Design follows Apple's principles: clarity, deference to content, restraint.

## Functional requirements
- **FR-1 Quick answers**: the hub answers short questions about current or tomorrow's
  weather, the umbrella, today's or tomorrow's agenda, open tasks and the next alarm
  directly, without calling Gemini. Anything with an action word, another day or a
  number still goes to the model. Quick answers stay in the conversation history.
- **FR-2 Morning briefing (visual only)**:
  - Dashboard: a "Buongiorno" card with weather, first event, tasks due and rain,
    shown once per day between 05:00 and 11:00 when the page opens or the PC wakes.
    It closes by itself after 60 seconds, or on tap or Esc.
  - Phone: a silent notification (low importance, no sound, no speech) about 90 seconds
    after the phone alarm, only for alarms between 04:00 and 11:59, once per day.
- **FR-3 7-day forecast**: shown on the dashboard ("Prossimi giorni"), in the app (card
  under the weather) and on the widget (day strip when the widget is at least 250 dp
  tall). Rain chance is shown only from 20%. Range bars share the week's scale.
- **FR-4 Event reminders**: a phone notification 15 minutes before each timed event of
  today, with time range and location. All-day events are skipped. Reminders are
  re-planned after every refresh and stale ones are cancelled.

## Non-functional requirements
- No text-to-speech for the briefing or reminders.
- Free: no new paid services or libraries.
- Exact alarms when allowed, otherwise inexact alarms allowed while idle.
- The notification permission is requested once on Android 13+.

## Out of scope
- Reminders for future days beyond today's snapshot.
- Briefing when no alarm is set on the phone.
