---
type: "External System"
title: "Open-Meteo"
description: "Previsioni e ricerca della città per hub e telefono, senza chiave."
tags: ["meteo"]
generated: { by: "cursor/claude-opus-5.5", at: "2026-11-24T23:20:00Z" }
resource: "https://open-meteo.com"
sources:
  - id: weather
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/app/weather.py"
  - id: weather-client
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/android-app/app/src/main/java/it/cerrone/dailyhub/WeatherClient.kt"
  - id: app-js
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/static/app.js"
  - id: env
    resource: "https://github.com/CerroneDonato/NEXUS/blob/main/hub-server/.env.example"
---

# Open-Meteo

- Previsioni da `https://api.open-meteo.com/v1/forecast`: l'hub le chiede in `weather.py`, il telefono direttamente.[^weather][^weather-client]
- Ricerca della città da `https://geocoding-api.open-meteo.com/v1/search`, nelle impostazioni di dashboard e app.[^app-js][^weather-client]
- Nessuna chiave. Il luogo parte da `WEATHER_CITY`, `WEATHER_LATITUDE` e `WEATHER_LONGITUDE` (Milano), poi segue le impostazioni sincronizzate.[^env]
