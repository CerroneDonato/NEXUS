# Build and Test Summary - Daily Hub

## Results on the dev PC (2026-11-10)

| Unit | Check | Result |
| :-- | :-- | :-- |
| hub-server | `pytest` (19 tests: auth, tasks CRUD, ordering/overdue, aggregation, caching, provider failures, alarm scheduling, weather/calendar parsing, static caching headers) | PASS |
| hub-server | Live smoke test: real Open-Meteo call for Milan, task CRUD, alarm PUT | PASS (after adding `truststore` for corporate TLS interception) |
| dashboard | Rendered in browser: weather + 12h forecast, calendar error state, tasks add/complete, next alarm | PASS |
| windows-scripts | PowerShell parser check on all scripts | PASS |
| windows-scripts | `schedule_wake.ps1` and `WindowsWakeScheduler` register a task with WakeToRun=True, Interactive logon, correct trigger; cancel removes it | PASS |
| android-app | Build | NOT RUN: no Android SDK/JDK on the dev PC. Build with Android Studio (see docs/android-setup.md); unit tests in `app/src/test` |
| network | Documentation only (Tailscale / LAN) | N/A |
| e2e | Requires hub PC + phone; procedure in docs/e2e-check.md | PENDING (manual, by user) |

## Build instructions
- Server: `docs/windows-setup.md` (install.ps1)
- Android: `docs/android-setup.md`

## Security baseline compliance
- Bearer token on all data endpoints, constant-time comparison: compliant
- Secrets excluded from git (`.env`, `secrets/`, keystores): compliant
- OAuth scope minimal (`calendar.readonly`): compliant
- Cleartext HTTP: accepted for LAN/Tailscale (Tailscale encrypts transport); documented
- Firewall rule limited to Private profile: compliant
