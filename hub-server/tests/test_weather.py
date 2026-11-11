from __future__ import annotations

from app.weather import describe, parse_forecast

PAYLOAD = {
    "current": {
        "time": "2026-11-10T21:00",
        "temperature_2m": 9.4,
        "apparent_temperature": 7.8,
        "weather_code": 3,
        "wind_speed_10m": 5.1,
        "is_day": 0,
    },
    "hourly": {
        "time": ["2026-11-10T20:00", "2026-11-10T21:00", "2026-11-10T22:00"],
        "temperature_2m": [10.0, 9.4, 9.0],
        "precipitation_probability": [0, 10, 40],
        "weather_code": [2, 3, 61],
    },
    "daily": {
        "time": ["2026-11-10", "2026-11-11"],
        "weather_code": [61, 0],
        "temperature_2m_max": [14.2, 16.0],
        "temperature_2m_min": [6.1, 5.0],
        "precipitation_probability_max": [70, 5],
        "sunrise": ["2026-11-10T07:12", "2026-11-11T07:13"],
        "sunset": ["2026-11-10T16:52", "2026-11-11T16:51"],
    },
}


def test_parse_forecast_maps_current_and_daily():
    result = parse_forecast(PAYLOAD, "Milano")

    assert result["city"] == "Milano"
    assert result["current"]["temperature"] == 9.4
    assert result["current"]["description"] == "Coperto"
    assert result["current"]["isDay"] is False
    assert result["today"] == {
        "min": 6.1,
        "max": 14.2,
        "precipitationProbability": 70,
        "sunrise": "2026-11-10T07:12",
        "sunset": "2026-11-10T16:52",
        "description": "Pioggia leggera",
        "icon": "rain",
    }


def test_parse_forecast_lists_each_forecast_day():
    days = parse_forecast(PAYLOAD, "Milano")["daily"]

    assert [d["date"] for d in days] == ["2026-11-10", "2026-11-11"]
    assert days[1] == {
        "date": "2026-11-11",
        "min": 5.0,
        "max": 16.0,
        "precipitationProbability": 5,
        "description": "Sereno",
        "icon": "clear",
    }


def test_parse_forecast_skips_past_hours():
    hours = parse_forecast(PAYLOAD, "Milano")["hourly"]

    assert [h["time"] for h in hours] == ["2026-11-10T21:00", "2026-11-10T22:00"]
    assert hours[1]["icon"] == "rain"


def test_unknown_code_is_handled():
    assert describe(1234) == ("N/D", "unknown")
    assert describe(None) == ("N/D", "unknown")
