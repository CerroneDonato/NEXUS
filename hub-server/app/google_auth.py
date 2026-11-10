"""One-time Google OAuth login. Run on the hub PC: python -m app.google_auth"""

from __future__ import annotations

from google_auth_oauthlib.flow import InstalledAppFlow

from .calendar_client import SCOPES
from .config import load_settings


def main() -> None:
    settings = load_settings()
    secret_file = settings.google_client_secret_file
    if not secret_file.exists():
        raise SystemExit(
            f"File OAuth non trovato: {secret_file}\nVedi docs/google-calendar-setup.md per scaricarlo."
        )
    flow = InstalledAppFlow.from_client_secrets_file(str(secret_file), SCOPES)
    creds = flow.run_local_server(port=0, prompt="consent")
    settings.google_token_file.parent.mkdir(parents=True, exist_ok=True)
    settings.google_token_file.write_text(creds.to_json(), encoding="utf-8")
    print(f"Token salvato in {settings.google_token_file}")


if __name__ == "__main__":
    main()
