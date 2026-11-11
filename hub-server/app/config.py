from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from zoneinfo import ZoneInfo

from dotenv import load_dotenv

SERVER_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = SERVER_DIR.parent


def _resolve(path: str) -> Path:
    candidate = Path(path)
    return candidate if candidate.is_absolute() else SERVER_DIR / candidate


def _as_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    api_token: str
    host: str = "0.0.0.0"
    port: int = 8765
    timezone: str = "Europe/Rome"
    latitude: float = 45.4642
    longitude: float = 9.19
    city: str = "Milano"
    data_dir: Path = SERVER_DIR / "data"
    google_client_secret_file: Path = SERVER_DIR / "secrets" / "client_secret.json"
    google_token_file: Path = SERVER_DIR / "secrets" / "google_token.json"
    calendar_ids: tuple[str, ...] = field(default_factory=lambda: ("primary",))
    wake_lead_minutes: int = 2
    wake_enabled: bool = True

    @property
    def tz(self) -> ZoneInfo:
        return ZoneInfo(self.timezone)

    @property
    def db_path(self) -> Path:
        return self.data_dir / "hub.sqlite3"


def load_settings() -> Settings:
    load_dotenv(SERVER_DIR / ".env")
    token = os.environ.get("HUB_API_TOKEN", "")
    if not token or token == "change-me":
        raise RuntimeError("HUB_API_TOKEN is not set: copy .env.example to .env and set a random token")
    return Settings(
        api_token=token,
        host=os.environ.get("HUB_HOST", "0.0.0.0"),
        port=int(os.environ.get("HUB_PORT", "8765")),
        timezone=os.environ.get("HUB_TIMEZONE", "Europe/Rome"),
        latitude=float(os.environ.get("WEATHER_LATITUDE", "45.4642")),
        longitude=float(os.environ.get("WEATHER_LONGITUDE", "9.19")),
        city=os.environ.get("WEATHER_CITY", "Milano"),
        data_dir=_resolve(os.environ.get("HUB_DATA_DIR", "data")),
        google_client_secret_file=_resolve(os.environ.get("GOOGLE_CLIENT_SECRET_FILE", "secrets/client_secret.json")),
        google_token_file=_resolve(os.environ.get("GOOGLE_TOKEN_FILE", "secrets/google_token.json")),
        calendar_ids=tuple(
            cid.strip() for cid in os.environ.get("GOOGLE_CALENDAR_IDS", "primary").split(",") if cid.strip()
        ),
        wake_lead_minutes=int(os.environ.get("WAKE_LEAD_MINUTES", "2")),
        wake_enabled=_as_bool(os.environ.get("WAKE_ENABLED", "true")),
    )
