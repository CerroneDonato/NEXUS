# Requirements - Daily Hub

## Intent
Personal daily dashboard shown on an always-on Windows PC and on an Android home-screen widget.

## Functional Requirements
- FR-1: Show today's weather for Milan (current temperature, min/max, condition, rain probability).
- FR-2: Show today's Google Calendar events (read-only).
- FR-3: Manage personal tasks (title, optional due date, done flag): create, list, update, delete.
- FR-4: Single aggregate endpoint `GET /api/today` used by dashboard and widget.
- FR-5: Android app reports the next phone alarm (`PUT /api/alarm`).
- FR-6: Hub schedules a Windows wake task at alarm time that wakes the PC, turns on the monitor and opens the dashboard full screen.
- FR-7: Code is developed on the dev PC and deployed to the hub PC through git (`update.ps1`).

## Non-Functional Requirements
- NFR-1 Security: API protected by a shared bearer token; secrets (`.env`, OAuth files) never committed.
- NFR-2 Resilience: weather/calendar failures must not break `/api/today` (partial data + error field).
- NFR-3 Performance: weather cached 30 min, calendar cached 5 min.
- NFR-4 Footprint: hub PC needs only Python, git, Edge.
- NFR-5 Cost: every component must be free (only Cursor tokens are paid). No paid APIs, hosting, stores or billing accounts.

## Constraints
- Phone: Android (AlarmManager.getNextAlarmClock available).
- Hub PC: Windows, left on or in sleep (not shut down).
- Dev PC has no Android SDK: the APK is built where Android Studio is installed.
