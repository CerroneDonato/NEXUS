"""Entry point on the hub PC: python run.py"""

from __future__ import annotations

import logging
import sys
from datetime import datetime

import truststore
import uvicorn

# Use the Windows certificate store so HTTPS works behind proxies with custom root CAs.
truststore.inject_into_ssl()

from app.assistant import GeminiClient
from app.calendar_client import GoogleCalendarClient
from app.config import load_settings
from app.db import Database
from app.main import HubServices, create_app
from app.wake import create_wake_scheduler
from app.weather import OpenMeteoClient


def build_app():
    settings = load_settings()
    logs_dir = settings.data_dir.parent / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)
    if sys.stderr is None:  # started with pythonw.exe (no console)
        sys.stdout = sys.stderr = open(logs_dir / "console.log", "a", encoding="utf-8", buffering=1)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        handlers=[logging.FileHandler(logs_dir / "hub.log", encoding="utf-8"), logging.StreamHandler()],
    )
    services = HubServices(
        settings=settings,
        db=Database(settings.db_path),
        weather=OpenMeteoClient(settings.latitude, settings.longitude, settings.timezone, settings.city),
        calendar=GoogleCalendarClient(settings.google_token_file, settings.calendar_ids, settings.tz),
        wake=create_wake_scheduler(settings.wake_enabled),
        now=lambda: datetime.now(settings.tz),
        assistant_model=GeminiClient(settings.gemini_api_key, settings.gemini_model) if settings.gemini_api_key else None,
    )
    return create_app(services), settings


if __name__ == "__main__":
    app, settings = build_app()
    uvicorn.run(app, host=settings.host, port=settings.port)
