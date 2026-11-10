from __future__ import annotations

from datetime import datetime
from typing import Any

import httpx

OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"

# WMO weather interpretation codes -> (Italian description, icon key used by the clients)
WMO_CODES: dict[int, tuple[str, str]] = {
    0: ("Sereno", "clear"),
    1: ("Prevalentemente sereno", "clear"),
    2: ("Parzialmente nuvoloso", "partly-cloudy"),
    3: ("Coperto", "cloudy"),
    45: ("Nebbia", "fog"),
    48: ("Nebbia con brina", "fog"),
    51: ("Pioviggine leggera", "drizzle"),
    53: ("Pioviggine", "drizzle"),
    55: ("Pioviggine intensa", "drizzle"),
    56: ("Pioviggine gelata", "drizzle"),
    57: ("Pioviggine gelata intensa", "drizzle"),
    61: ("Pioggia leggera", "rain"),
    63: ("Pioggia", "rain"),
    65: ("Pioggia forte", "rain"),
    66: ("Pioggia gelata", "rain"),
    67: ("Pioggia gelata forte", "rain"),
    71: ("Neve leggera", "snow"),
    73: ("Neve", "snow"),
    75: ("Neve forte", "snow"),
    77: ("Nevischio", "snow"),
    80: ("Rovesci leggeri", "rain"),
    81: ("Rovesci", "rain"),
    82: ("Rovesci violenti", "rain"),
    85: ("Rovesci di neve", "snow"),
    86: ("Rovesci di neve forti", "snow"),
    95: ("Temporale", "storm"),
    96: ("Temporale con grandine", "storm"),
    99: ("Temporale con grandine forte", "storm"),
}


def describe(code: int | None) -> tuple[str, str]:
    if code is None:
        return ("N/D", "unknown")
    return WMO_CODES.get(int(code), ("N/D", "unknown"))


class OpenMeteoClient:
    def __init__(self, latitude: float, longitude: float, timezone: str, city: str, http: httpx.Client | None = None):
        self._params = {
            "latitude": latitude,
            "longitude": longitude,
            "timezone": timezone,
            "forecast_days": 2,
            "current": "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day",
            "hourly": "temperature_2m,precipitation_probability,weather_code",
            "daily": "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
        }
        self._city = city
        self._http = http or httpx.Client(timeout=10.0)

    def fetch(self) -> dict[str, Any]:
        response = self._http.get(OPEN_METEO_URL, params=self._params)
        response.raise_for_status()
        return parse_forecast(response.json(), self._city)


def parse_forecast(payload: dict[str, Any], city: str) -> dict[str, Any]:
    current = payload.get("current", {})
    daily = payload.get("daily", {})
    hourly = payload.get("hourly", {})

    def first(values: list | None) -> Any:
        return values[0] if values else None

    current_desc, current_icon = describe(current.get("weather_code"))
    daily_desc, daily_icon = describe(first(daily.get("weather_code")))

    current_time = current.get("time")
    hours = []
    for index, time_str in enumerate(hourly.get("time", [])):
        if current_time and time_str[:13] < current_time[:13]:
            continue
        desc, icon = describe(hourly["weather_code"][index])
        hours.append(
            {
                "time": time_str,
                "temperature": hourly["temperature_2m"][index],
                "precipitationProbability": hourly["precipitation_probability"][index],
                "description": desc,
                "icon": icon,
            }
        )

    return {
        "city": city,
        "current": {
            "time": current_time,
            "temperature": current.get("temperature_2m"),
            "apparentTemperature": current.get("apparent_temperature"),
            "windSpeed": current.get("wind_speed_10m"),
            "isDay": bool(current.get("is_day", 1)),
            "description": current_desc,
            "icon": current_icon,
        },
        "today": {
            "min": first(daily.get("temperature_2m_min")),
            "max": first(daily.get("temperature_2m_max")),
            "precipitationProbability": first(daily.get("precipitation_probability_max")),
            "sunrise": first(daily.get("sunrise")),
            "sunset": first(daily.get("sunset")),
            "description": daily_desc,
            "icon": daily_icon,
        },
        "hourly": hours[:12],
        "fetchedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
    }
