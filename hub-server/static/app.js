"use strict";

const TOKEN_KEY = "dailyHubToken";
const REFRESH_MS = 60 * 1000;
const RESUME_GAP_MS = 15 * 1000;
const REVISION_MS = 4 * 1000;

const $ = (id) => document.getElementById(id);

const timeFmt = new Intl.DateTimeFormat(LOCALE, { hour: "2-digit", minute: "2-digit" });
const dateFmt = new Intl.DateTimeFormat(LOCALE, { weekday: "long", day: "numeric", month: "long" });
const shortDateFmt = new Intl.DateTimeFormat(LOCALE, { weekday: "short", day: "numeric", month: "short" });

/* ------------------------------------------------------------------ icons */

const GRADIENTS = `
  <defs>
    <radialGradient id="g-sun" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="#fff6c4"/><stop offset="1" stop-color="#ffb703"/></radialGradient>
    <linearGradient id="g-moon" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fbf8dc"/><stop offset="1" stop-color="#c9c3a0"/></linearGradient>
    <linearGradient id="g-cloud" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#cdd8e6"/></linearGradient>
    <linearGradient id="g-cloud-dark" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#b3bfd0"/><stop offset="1" stop-color="#6b788e"/></linearGradient>
  </defs>`;

const CLOUD_PATH = "M18 50h28a12 12 0 0 0 1.5-23.9A16 16 0 0 0 17.3 31 9.5 9.5 0 0 0 18 50z";
const SUN = '<circle cx="32" cy="32" r="13" fill="url(#g-sun)"/><g stroke="#ffd166" stroke-width="3.5" stroke-linecap="round"><path d="M32 6v7M32 51v7M6 32h7M51 32h7M13.6 13.6l5 5M45.4 45.4l5 5M13.6 50.4l5-5M45.4 18.6l5-5"/></g>';
const MOON = '<path d="M40 10a22 22 0 1 0 14 34A18 18 0 1 1 40 10z" fill="url(#g-moon)"/>';
const cloud = (dark = false, extra = "") => `<path d="${CLOUD_PATH}" fill="url(#${dark ? "g-cloud-dark" : "g-cloud"})" ${extra}/>`;
const small = (inner, x, y, s) => `<g transform="translate(${x} ${y}) scale(${s})">${inner}</g>`;
const svg = (inner) => `<svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">${GRADIENTS}${inner}</svg>`;

function weatherIcon(icon, isDay = true) {
  const body = isDay ? SUN : MOON;
  switch (icon) {
    case "clear": return svg(body);
    case "partly-cloudy": return svg(small(body, 0, -2, 0.72) + small(cloud(), 6, 10, 0.92));
    case "cloudy": return svg(small(cloud(true), 10, -6, 0.8) + small(cloud(), 0, 6, 0.95));
    case "fog": return svg(small(cloud(), 6, -6, 0.8) + '<g stroke="#e6edf5" stroke-width="3.5" stroke-linecap="round" opacity="0.9"><path d="M12 48h40M18 56h30"/></g>');
    case "drizzle": return svg(small(cloud(), 2, -6, 0.95) + '<g stroke="#8fd3ff" stroke-width="3" stroke-linecap="round"><path d="M24 50l-2 5M34 50l-2 5M44 50l-2 5"/></g>');
    case "rain": return svg(small(cloud(true), 2, -8, 0.95) + '<g stroke="#7cc7ff" stroke-width="3.5" stroke-linecap="round"><path d="M22 46l-4 10M33 46l-4 10M44 46l-4 10"/></g>');
    case "snow": return svg(small(cloud(), 2, -8, 0.95) + '<g fill="#ffffff"><circle cx="22" cy="50" r="3"/><circle cx="33" cy="56" r="3"/><circle cx="44" cy="50" r="3"/></g>');
    case "storm": return svg(small(cloud(true), 2, -10, 0.95) + '<path d="M34 36l-9 14h8l-4 12 13-18h-8l4-8z" fill="#ffd166"/>');
    default: return svg(cloud());
  }
}

const UI_ICONS = {
  alarm: '<circle cx="12" cy="13" r="7"/><path d="M12 10v3l2 2M5 4L2 7M19 4l3 3"/>',
  temp: '<path d="M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0z"/>',
  minmax: '<path d="M8 4v16M8 4l-3 3M8 4l3 3M16 20V4M16 20l-3-3M16 20l3-3"/>',
  wind: '<path d="M3 8h11a3 3 0 1 0-3-3M3 16h15a3 3 0 1 1-3 3M3 12h18"/>',
  drop: '<path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z"/>',
  sunrise: '<path d="M12 3v5M8 7l4-4 4 4M4 18h16M6.3 13.3l1.4 1.4M17.7 13.3l-1.4 1.4M8 18a4 4 0 0 1 8 0"/>',
  sunset: '<path d="M12 8V3M8 4l4 4 4-4M4 18h16M6.3 13.3l1.4 1.4M17.7 13.3l-1.4 1.4M8 18a4 4 0 0 1 8 0"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  send: '<path d="M4 12l16-8-6 16-2.5-6.5L4 12z"/>',
  restart: '<path d="M4 12a8 8 0 1 0 2.3-5.7M4 4v4.5h4.5"/>',
  mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M8.5 21h7"/>',
  stop: '<rect x="7" y="7" width="10" height="10" rx="2"/>',
  calendar: '<rect x="4" y="5" width="16" height="15" rx="3"/><path d="M4 10h16M9 3v4M15 3v4"/>',
  tasks: '<circle cx="12" cy="12" r="8"/><path d="M8.5 12.2l2.4 2.4 4.6-5"/>',
  history: '<path d="M4 12a8 8 0 1 0 2.4-5.7"/><path d="M4 4.5V8.5H8"/><path d="M12 8v4.3l2.8 1.7"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  sync: '<path d="M20 11a8 8 0 0 0-14.3-4.9L4 8M4 4v4h4M4 13a8 8 0 0 0 14.3 4.9L20 16M20 20v-4h-4"/>',
  spark: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/><path d="M19 16l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z"/>',
  pin: '<path d="M12 21s-6.5-6.1-6.5-11a6.5 6.5 0 0 1 13 0c0 4.9-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.4"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.6v.2"/>',
  edit: '<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4zM13.5 6.5l4 4"/>',
  globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.6 2.4 3.8 5.3 3.8 8.5s-1.2 6.1-3.8 8.5c-2.6-2.4-3.8-5.3-3.8-8.5S9.4 5.9 12 3.5z"/>',
};

function uiIcon(name, strokeWidth = 1.8) {
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round">${UI_ICONS[name]}</svg>`;
}

/* ------------------------------------------------------------------ helpers */

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "html") node.innerHTML = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value);
  }
  for (const child of children) {
    if (child !== null && child !== undefined && child !== false) node.append(child);
  }
  return node;
}

const round = (v) => (v === null || v === undefined ? "--" : Math.round(v));
const localDate = (s) => (s ? new Date(s) : null);
const minutes = (ms) => Math.round(ms / 60000);

function formatDuration(ms) {
  const total = Math.max(0, minutes(ms));
  if (total < 1) return t("ora");
  if (total < 60) return `${total} min`;
  const h = Math.floor(total / 60);
  const m = total % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}

function greetingFor(date) {
  const h = date.getHours();
  if (h >= 5 && h < 12) return t("Buongiorno");
  if (h >= 12 && h < 18) return t("Buon pomeriggio");
  if (h >= 18 && h < 23) return t("Buonasera");
  return t("Buonanotte");
}

/* ------------------------------------------------------------------ api */

function readToken() {
  const hash = new URLSearchParams(location.hash.slice(1));
  const fromHash = hash.get("token");
  if (fromHash) {
    localStorage.setItem(TOKEN_KEY, fromHash);
    history.replaceState(null, "", location.pathname);
  }
  return localStorage.getItem(TOKEN_KEY);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    cache: "no-store",
    headers: {
      "Authorization": `Bearer ${localStorage.getItem(TOKEN_KEY)}`,
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  if (response.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    showLogin();
    throw new Error(t("Token non valido"));
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const error = new Error(typeof body?.detail === "string" ? t(body.detail) : `HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

/* ------------------------------------------------------------------ sky */

const Sky = (() => {
  const canvas = $("sky-canvas");
  const ctx = canvas.getContext("2d");
  const flash = document.querySelector(".sky-flash");
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  let width = 0;
  let height = 0;
  let config = { stars: 0, clouds: 0, cloudTone: "light", rain: 0, snow: 0, fog: 0, lightning: false };
  let stars = [];
  let clouds = [];
  let drops = [];
  let flakes = [];
  let fogBands = [];
  let last = 0;
  let nextLightning = 0;
  let running = false;
  let currentKey = "";

  const rand = (a, b) => a + Math.random() * (b - a);

  const CLOUD_TONES = {
    light: [255, 255, 255, 0.55],
    grey: [205, 214, 226, 0.5],
    dark: [70, 80, 98, 0.6],
    night: [40, 50, 70, 0.55],
    warm: [255, 200, 180, 0.4],
  };

  function cloudSprite(tone) {
    const [r, g, b, a] = CLOUD_TONES[tone];
    const sprite = document.createElement("canvas");
    sprite.width = 640;
    sprite.height = 280;
    const sctx = sprite.getContext("2d");
    const puffs = 7 + Math.floor(Math.random() * 4);
    for (let i = 0; i < puffs; i++) {
      const cx = rand(110, 530);
      const cy = rand(120, 190);
      const radius = rand(70, 130);
      const grad = sctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      grad.addColorStop(0, `rgba(${r},${g},${b},${a})`);
      grad.addColorStop(0.55, `rgba(${r},${g},${b},${a * 0.45})`);
      grad.addColorStop(1, `rgba(${r},${g},${b},0)`);
      sctx.fillStyle = grad;
      sctx.fillRect(0, 0, sprite.width, sprite.height);
    }
    return sprite;
  }

  function makeCloud(x) {
    const scale = rand(0.7, 1.6);
    return { x, y: rand(-height * 0.05, height * 0.5), scale, speed: rand(5, 16) / scale, sprite: cloudSprite(config.cloudTone) };
  }

  const makeDrop = (anyY) => ({
    x: rand(-width * 0.1, width * 1.1),
    y: anyY ? rand(-height, height) : rand(-height * 0.3, -10),
    len: rand(12, 26),
    speed: rand(800, 1250),
    alpha: rand(0.25, 0.6),
  });

  const makeFlake = (anyY) => ({
    x: rand(0, width),
    y: anyY ? rand(-height, height) : rand(-40, -5),
    r: rand(1, 3.4),
    speed: rand(25, 70),
    sway: rand(10, 35),
    phase: rand(0, Math.PI * 2),
  });

  function build() {
    stars = Array.from({ length: config.stars }, () => ({
      x: rand(0, width), y: rand(0, height * 0.75), r: rand(0.3, 1.5), phase: rand(0, Math.PI * 2), speed: rand(0.6, 1.8),
    }));
    clouds = Array.from({ length: config.clouds }, () => makeCloud(rand(-width * 0.3, width)));
    drops = Array.from({ length: config.rain }, () => makeDrop(true));
    flakes = Array.from({ length: config.snow }, () => makeFlake(true));
    fogBands = Array.from({ length: config.fog }, (_, i) => ({
      x: rand(-width, 0), y: height * (0.3 + i * 0.13), h: rand(height * 0.12, height * 0.22), speed: rand(8, 20), alpha: rand(0.1, 0.2),
    }));
    nextLightning = performance.now() + rand(3000, 8000);
  }

  function resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = window.innerWidth;
    height = window.innerHeight;
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    build();
    if (reduceMotion) draw(0, 0);
  }

  function draw(now, dt) {
    ctx.clearRect(0, 0, width, height);

    for (const s of stars) {
      const twinkle = 0.55 + 0.45 * Math.sin(now / 1000 * s.speed + s.phase);
      ctx.globalAlpha = twinkle;
      ctx.fillStyle = "#ffffff";
      ctx.beginPath();
      ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;

    for (const c of clouds) {
      c.x += c.speed * dt;
      const w = c.sprite.width * c.scale;
      if (c.x > width + 20) Object.assign(c, makeCloud(-w - rand(0, width * 0.3)));
      ctx.drawImage(c.sprite, c.x, c.y, w, c.sprite.height * c.scale);
    }

    for (const band of fogBands) {
      band.x += band.speed * dt;
      if (band.x > width) band.x = -width * 1.5;
      const grad = ctx.createLinearGradient(0, band.y, 0, band.y + band.h);
      grad.addColorStop(0, "rgba(235,240,245,0)");
      grad.addColorStop(0.5, `rgba(235,240,245,${band.alpha})`);
      grad.addColorStop(1, "rgba(235,240,245,0)");
      ctx.fillStyle = grad;
      ctx.fillRect(band.x, band.y, width * 1.6, band.h);
    }

    if (drops.length) {
      ctx.strokeStyle = "rgba(200,225,255,1)";
      ctx.lineWidth = 1.1;
      ctx.lineCap = "round";
      for (const d of drops) {
        d.y += d.speed * dt;
        d.x += d.speed * 0.12 * dt;
        if (d.y > height) Object.assign(d, makeDrop(false));
        ctx.globalAlpha = d.alpha;
        ctx.beginPath();
        ctx.moveTo(d.x, d.y);
        ctx.lineTo(d.x - d.len * 0.12, d.y - d.len);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;
    }

    if (flakes.length) {
      ctx.fillStyle = "rgba(255,255,255,0.9)";
      for (const f of flakes) {
        f.y += f.speed * dt;
        f.phase += dt;
        if (f.y > height + 5) Object.assign(f, makeFlake(false));
        ctx.beginPath();
        ctx.arc(f.x + Math.sin(f.phase) * f.sway, f.y, f.r, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    if (config.lightning && !reduceMotion && now > nextLightning) {
      flash.classList.remove("on");
      void flash.offsetWidth;
      flash.classList.add("on");
      nextLightning = now + rand(6000, 15000);
    }
  }

  function loop(now) {
    if (!running) return;
    const dt = last ? Math.min((now - last) / 1000, 0.05) : 0;
    last = now;
    draw(now, dt);
    requestAnimationFrame(loop);
  }

  const PRESETS = {
    "day-clear": { clouds: 2, cloudTone: "light" },
    "day-partly": { clouds: 7, cloudTone: "light" },
    "day-cloudy": { clouds: 12, cloudTone: "grey" },
    "night-clear": { stars: 260 },
    "night-partly": { stars: 140, clouds: 6, cloudTone: "night" },
    "night-cloudy": { stars: 20, clouds: 12, cloudTone: "night" },
    "rain": { clouds: 10, cloudTone: "dark", rain: 320 },
    "storm": { clouds: 12, cloudTone: "dark", rain: 460, lightning: true },
    "snow": { clouds: 7, cloudTone: "grey", snow: 190 },
    "fog": { clouds: 3, cloudTone: "grey", fog: 6 },
    "dawn": { stars: 30, clouds: 5, cloudTone: "warm" },
    "dusk": { stars: 50, clouds: 5, cloudTone: "warm" },
  };

  function set(theme, intensity = 1) {
    const key = `${theme}:${intensity}`;
    if (key === currentKey) return;
    currentKey = key;
    const preset = PRESETS[theme] || PRESETS["day-cloudy"];
    config = {
      stars: 0, clouds: 0, cloudTone: "light", rain: 0, snow: 0, fog: 0, lightning: false,
      ...preset,
    };
    config.rain = Math.round(config.rain * intensity);
    config.snow = Math.round(config.snow * intensity);
    build();
    if (reduceMotion) {
      draw(0, 0);
    } else if (!running) {
      running = true;
      last = 0;
      requestAnimationFrame(loop);
    }
  }

  window.addEventListener("resize", resize);
  resize();
  return { set };
})();

function themeFor(weather, now = new Date()) {
  if (!weather || !weather.current) {
    const h = now.getHours();
    return { theme: h >= 7 && h < 18 ? "day-cloudy" : "night-cloudy", night: !(h >= 7 && h < 18), intensity: 1 };
  }
  const { icon, isDay } = weather.current;
  const sunrise = localDate(weather.today && weather.today.sunrise);
  const sunset = localDate(weather.today && weather.today.sunset);
  const near = (d) => d && Math.abs(now - d) < 40 * 60 * 1000;
  const fair = icon === "clear" || icon === "partly-cloudy";
  const night = !isDay;

  if (fair && near(sunrise)) return { theme: "dawn", night, intensity: 1 };
  if (fair && near(sunset)) return { theme: "dusk", night, intensity: 1 };

  const prefix = isDay ? "day" : "night";
  switch (icon) {
    case "clear": return { theme: `${prefix}-clear`, night, intensity: 1 };
    case "partly-cloudy": return { theme: `${prefix}-partly`, night, intensity: 1 };
    case "cloudy": return { theme: `${prefix}-cloudy`, night, intensity: 1 };
    case "fog": return { theme: "fog", night, intensity: 1 };
    case "drizzle": return { theme: "rain", night, intensity: 0.45 };
    case "rain": return { theme: "rain", night, intensity: 1 };
    case "storm": return { theme: "storm", night, intensity: 1 };
    case "snow": return { theme: "snow", night, intensity: 1 };
    default: return { theme: `${prefix}-cloudy`, night, intensity: 1 };
  }
}

function applyTheme(weather) {
  const { theme, night, intensity } = themeFor(weather);
  document.body.dataset.theme = theme;
  document.body.dataset.night = String(night);
  Sky.set(theme, intensity);
}

/* ------------------------------------------------------------------ state */

let lastData = null;
let lastTick = Date.now();
let lastMinute = -1;
let retryTimer = null;
let failures = 0;

/* ------------------------------------------------------------------ render */

function renderClock() {
  const now = new Date();
  $("clock-hm").textContent = timeFmt.format(now);
  $("clock-s").textContent = String(now.getSeconds()).padStart(2, "0");
  $("date").textContent = dateFmt.format(now);
  $("greeting").textContent = greetingFor(now);
}

function isDayAt(date, weather) {
  const sunrise = localDate(weather.today && weather.today.sunrise);
  const sunset = localDate(weather.today && weather.today.sunset);
  if (!sunrise || !sunset) return date.getHours() >= 7 && date.getHours() < 18;
  const mins = date.getHours() * 60 + date.getMinutes();
  return mins >= sunrise.getHours() * 60 + sunrise.getMinutes() && mins < sunset.getHours() * 60 + sunset.getMinutes();
}

function renderWeather(weather, error) {
  const icon = $("wx-icon");
  const stats = $("wx-stats");
  stats.replaceChildren();
  if (!weather) {
    icon.innerHTML = weatherIcon("unknown");
    $("wx-temp").textContent = "--";
    $("wx-desc").textContent = t(error || "Meteo non disponibile");
    $("wx-city").textContent = "";
    renderForecast(null);
    return;
  }
  const { current, today } = weather;
  icon.innerHTML = weatherIcon(current.icon, current.isDay);
  $("wx-temp").textContent = round(current.temperature);
  $("wx-desc").textContent = t(current.description);
  $("wx-city").textContent = weather.city;

  const sunrise = localDate(today.sunrise);
  const sunset = localDate(today.sunset);
  const items = [
    ["minmax", t("Min / Max"), `${round(today.min)}° / ${round(today.max)}°`],
    ["temp", t("Percepita"), `${round(current.apparentTemperature)}°`],
    ["drop", t("Pioggia"), `${round(today.precipitationProbability)}%`],
    ["wind", t("Vento"), `${round(current.windSpeed)} km/h`],
    ["sunrise", t("Alba"), sunrise ? timeFmt.format(sunrise) : "--"],
    ["sunset", t("Tramonto"), sunset ? timeFmt.format(sunset) : "--"],
  ];
  for (const [name, label, value] of items) {
    stats.append(el("div", { class: "stat" },
      el("span", { html: uiIcon(name) }),
      el("div", {}, el("div", { class: "stat-label" }, label), el("div", { class: "stat-value" }, value)),
    ));
  }
  renderForecast(weather);
}

function smoothPath(points) {
  if (points.length < 2) return "";
  let d = `M${points[0][0]},${points[0][1]}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] || points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] || p2;
    const c1x = p1[0] + (p2[0] - p0[0]) / 6;
    const c1y = p1[1] + (p2[1] - p0[1]) / 6;
    const c2x = p2[0] - (p3[0] - p1[0]) / 6;
    const c2y = p2[1] - (p3[1] - p1[1]) / 6;
    d += ` C${c1x.toFixed(1)},${c1y.toFixed(1)} ${c2x.toFixed(1)},${c2y.toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`;
  }
  return d;
}

function renderForecast(weather) {
  const host = $("forecast");
  host.replaceChildren();
  const hours = (weather && weather.hourly) || [];
  if (!hours.length) {
    host.append(el("div", { class: "empty-state" }, t("Previsioni non disponibili")));
    return;
  }

  const width = Math.max(host.clientWidth, 300);
  const height = Math.max(Math.min(window.innerHeight * 0.13, 150), 90);
  const n = hours.length;
  const col = width / n;
  const temps = hours.map((h) => h.temperature ?? 0);
  const min = Math.min(...temps);
  const max = Math.max(...temps);
  const span = Math.max(max - min, 3);
  const top = 24;
  const bottom = height - 30;
  const points = temps.map((t, i) => [col * (i + 0.5), bottom - ((t - min) / span) * (bottom - top)]);
  const line = smoothPath(points);
  const area = `${line} L${points[n - 1][0].toFixed(1)},${height} L${points[0][0].toFixed(1)},${height} Z`;

  let bars = "";
  let labels = "";
  hours.forEach((h, i) => {
    const p = Math.max(0, Math.min(100, h.precipitationProbability || 0));
    const bh = (p / 100) * (height * 0.42);
    if (bh > 0.5) {
      bars += `<rect x="${(col * i + col * 0.3).toFixed(1)}" y="${(height - bh).toFixed(1)}" width="${(col * 0.4).toFixed(1)}" height="${bh.toFixed(1)}" rx="3" class="chart-bar"/>`;
    }
    labels += `<text x="${points[i][0].toFixed(1)}" y="${(points[i][1] - 10).toFixed(1)}" class="chart-label">${round(h.temperature)}°</text>`;
  });

  const dots = points.map(([x, y]) => `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="3" class="chart-dot"/>`).join("");

  const chart = el("div", {
    html: `<svg class="chart" viewBox="0 0 ${width.toFixed(0)} ${height.toFixed(0)}" style="height:${height.toFixed(0)}px">
      <defs><linearGradient id="g-area" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" style="stop-color:var(--accent);stop-opacity:0.38"/>
        <stop offset="1" style="stop-color:var(--accent);stop-opacity:0"/>
      </linearGradient></defs>
      ${bars}
      <path d="${area}" fill="url(#g-area)"/>
      <path d="${line}" class="chart-line"/>
      ${dots}${labels}
    </svg>`,
  });
  host.append(chart.firstElementChild);

  const row = el("div", { class: "forecast-hours", style: `grid-template-columns: repeat(${n}, minmax(0, 1fr))` });
  for (const h of hours) {
    const date = new Date(h.time);
    row.append(el("div", { html: `${h.time.slice(11, 13)}${weatherIcon(h.icon, isDayAt(date, weather))}` }));
  }
  host.append(row);
}

const weekdayShortFmt = new Intl.DateTimeFormat(LOCALE, { weekday: "short" });
const dayMonthFmt = new Intl.DateTimeFormat(LOCALE, { day: "numeric", month: "short" });

/** Cold blue to warm orange, like the range bars of a weather app. */
function tempColor(t) {
  const stops = [[-5, [124, 170, 255]], [5, [124, 199, 255]], [12, [111, 227, 196]], [19, [255, 211, 122]], [27, [255, 150, 90]]];
  if (t <= stops[0][0]) return `rgb(${stops[0][1]})`;
  for (let i = 1; i < stops.length; i++) {
    const [t1, c1] = stops[i];
    const [t0, c0] = stops[i - 1];
    if (t <= t1) {
      const k = (t - t0) / (t1 - t0);
      return `rgb(${c0.map((v, j) => Math.round(v + (c1[j] - v) * k)).join(",")})`;
    }
  }
  return `rgb(${stops[stops.length - 1][1]})`;
}

function renderWeek(weather) {
  const list = $("week");
  list.replaceChildren();
  const days = ((weather && weather.daily) || []).filter((d) => d.min !== null && d.max !== null);
  if (!days.length) {
    list.append(el("li", { class: "empty-state" }, t("Previsioni non disponibili")));
    return;
  }
  const lo = Math.min(...days.map((d) => d.min));
  const hi = Math.max(...days.map((d) => d.max));
  const span = Math.max(hi - lo, 1);
  const now = weather.current && weather.current.temperature;

  days.forEach((d, i) => {
    const name = i === 0 ? t("Oggi") : weekdayShortFmt.format(new Date(`${d.date}T12:00:00`)).replace(".", "");
    const rain = (d.precipitationProbability || 0) >= 20 ? `${d.precipitationProbability}%` : "";
    const left = ((d.min - lo) / span) * 100;
    const width = Math.max(((d.max - d.min) / span) * 100, 4);
    const range = el("div", {
      class: "week-range",
      style: `left:${left.toFixed(1)}%;width:${width.toFixed(1)}%;background:linear-gradient(90deg, ${tempColor(d.min)}, ${tempColor(d.max)})`,
    });
    const track = el("div", { class: "week-track" }, range);
    if (i === 0 && typeof now === "number") {
      const at = Math.min(Math.max(((now - lo) / span) * 100, 0), 100);
      track.append(el("span", { class: "week-now", style: `left:${at.toFixed(1)}%` }));
    }
    list.append(el("li", {},
      el("span", { class: "week-day" }, name),
      el("span", { class: "week-icon", html: weatherIcon(d.icon, true) }, el("small", {}, rain)),
      el("span", { class: "week-min" }, `${round(d.min)}°`),
      track,
      el("span", { class: "week-max" }, `${round(d.max)}°`),
    ));
  });
}

function eventWindow(event) {
  if (event.allDay) return null;
  const start = localDate(event.start);
  const end = localDate(event.end) || start;
  return start ? { start, end } : null;
}

function agendaDayLabel(iso, todayIso) {
  const when = new Date(`${iso}T12:00:00`);
  const dateText = dayMonthFmt.format(when).replace(/\./g, "");
  if (iso === todayIso) return { name: t("Oggi"), date: dateText };
  const raw = weekdayShortFmt.format(when).replace(/\./g, "");
  return { name: raw.charAt(0).toUpperCase() + raw.slice(1), date: dateText };
}

function agendaTimeLabel(event) {
  if (event.allDay) return t("Tutto il giorno");
  const span = eventWindow(event);
  return span ? timeFmt.format(span.start) : t("Tutto il giorno");
}

function renderAgendaWeek(week, todayIso, error) {
  const host = $("agenda-week");
  if (!host) return;
  host.replaceChildren();
  if (error || !Array.isArray(week)) {
    host.hidden = true;
    return;
  }
  host.hidden = false;
  const days = week.filter((day) => day.events && day.events.length);
  host.append(el("div", { class: "agenda-week-label" }, t("Agenda della settimana")));
  if (!days.length) {
    host.append(el("p", { class: "agenda-week-quiet" }, t("Settimana libera")));
    return;
  }
  const list = el("ol", { class: "agenda-week-list" });
  days.forEach((day, dayIndex) => {
    const label = agendaDayLabel(day.date, todayIso);
    day.events.forEach((event, index) => {
      list.append(el("li", { class: index === 0 && dayIndex > 0 ? "agenda-day-next" : "" },
        index === 0
          ? el("span", { class: "agenda-day-when" },
            el("span", { class: "agenda-day-name" }, label.name),
            el("span", { class: "agenda-day-date" }, label.date),
          )
          : el("span", { class: "agenda-day-when" }),
        el("span", { class: "agenda-day-time" }, agendaTimeLabel(event)),
        el("span", { class: "agenda-day-title" },
          el("span", { class: "agenda-day-text" }, event.title),
          event.location ? el("span", { class: "agenda-day-loc" }, event.location) : null),
      ));
    });
  });
  host.append(list);
}

function renderAgenda(events, error, now = new Date(), week = null, todayIso = "") {
  const next = $("next-event");
  const timeline = $("timeline");
  next.replaceChildren();
  timeline.replaceChildren();
  $("events-count").textContent = events.length ? t("{n} oggi", { n: events.length }) : "";
  renderAgendaWeek(week, todayIso, error);

  if (error) {
    const notConfigured = error.includes("non configurato");
    timeline.append(el("li", { class: "empty-state" },
      el("span", { class: notConfigured ? "" : "error-text" }, notConfigured ? t("Calendario non collegato") : t(error)),
      notConfigured ? el("small", {}, t("Sul PC hub: python -m app.google_auth")) : null,
    ));
    return;
  }
  if (!events.length) {
    timeline.append(el("li", { class: "empty-state" }, t("Nessun impegno oggi"), el("small", {}, t("Giornata libera"))));
    return;
  }

  const timed = events.map((e) => ({ e, w: eventWindow(e) }));
  const ongoing = timed.find(({ w }) => w && w.start <= now && now < w.end);
  const upcoming = timed.find(({ w }) => w && w.start > now);
  const focus = ongoing || upcoming;

  if (focus) {
    const { e, w } = focus;
    const isNow = focus === ongoing;
    next.append(
      el("div", { class: "next-label" },
        isNow ? t("In corso") : t("Prossimo"),
        el("span", { class: "next-countdown" }, isNow ? t("fino alle {time}", { time: timeFmt.format(w.end) }) : t("Tra {time}", { time: formatDuration(w.start - now) })),
      ),
      el("div", { class: "next-title" }, e.title),
      el("div", { class: "next-meta" }, `${timeFmt.format(w.start)} – ${timeFmt.format(w.end)}${e.location ? ` · ${e.location}` : ""}`),
    );
  }

  for (const { e, w } of timed) {
    if (focus && e === focus.e) continue;
    let state = "";
    if (w && w.end <= now) state = "past";
    else if (w && w.start <= now) state = "now";
    timeline.append(el("li", { class: state },
      el("div", { class: "tl-time" },
        w ? timeFmt.format(w.start) : t("Tutto il giorno"),
        w ? el("small", {}, timeFmt.format(w.end)) : null,
      ),
      el("div", { class: "tl-body" },
        el("div", { class: "tl-title" }, e.title, state === "now" ? el("span", { class: "badge" }, t("ORA")) : null),
        e.location ? el("div", { class: "tl-sub" }, e.location) : null,
      ),
    ));
  }
}

function dueChip(task, todayIso) {
  if (!task.due) return null;
  if (task.overdue) return el("span", { class: "due overdue" }, `${t("Scaduto")} · ${shortDateFmt.format(new Date(`${task.due}T12:00:00`))}`);
  if (task.due === todayIso) return el("span", { class: "due today" }, t("Oggi"));
  return el("span", { class: "due" }, shortDateFmt.format(new Date(`${task.due}T12:00:00`)));
}

let editingTask = false;

function startEdit(task, item) {
  if (editingTask) return;
  editingTask = true;
  const title = el("input", { class: "task-edit-title", value: task.title, maxlength: 200, autocomplete: "off" });
  const due = el("input", { class: "task-edit-due", type: "date", value: task.due || "", title: t("Scadenza") });
  const finish = (changed) => {
    editingTask = false;
    if (changed) refresh();
    else if (lastData) renderTasks(lastData.tasks, lastData.date);
  };
  const save = async () => {
    const newTitle = title.value.trim();
    if (!newTitle) { title.focus(); return; }
    const newDue = due.value || null;
    if (newTitle === task.title && newDue === (task.due || null)) { finish(false); return; }
    try {
      await api(`/api/tasks/${task.id}`, { method: "PATCH", body: JSON.stringify({ title: newTitle, due: newDue }) });
      finish(true);
    } catch {
      finish(false);
    }
  };
  const onKey = (event) => {
    if (event.key === "Enter") { event.preventDefault(); save(); }
    else if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); finish(false); }
  };
  title.addEventListener("keydown", onKey);
  due.addEventListener("keydown", onKey);
  item.classList.add("editing");
  item.replaceChildren(
    el("button", { class: "check", title: t("Salva"), html: uiIcon("check", 3), onclick: save }),
    title,
    due,
    el("button", { class: "delete", title: t("Annulla"), html: uiIcon("close", 2), onclick: () => finish(false) }),
  );
  title.focus();
  title.select();
}

function renderTasks(tasks, todayIso) {
  if (editingTask) return;
  const list = $("tasks");
  list.replaceChildren();
  const overdue = tasks.filter((x) => x.overdue).length;
  const dueToday = tasks.filter((x) => x.due === todayIso).length;
  $("tasks-count").textContent = tasks.length ? t("{n} aperti", { n: tasks.length }) : "";

  const stats = $("task-stats");
  stats.replaceChildren(
    el("div", { class: `task-stat ${overdue ? "warn" : ""}` }, el("strong", {}, String(overdue)), el("span", {}, t("Scaduti"))),
    el("div", { class: `task-stat ${dueToday ? "ok" : ""}` }, el("strong", {}, String(dueToday)), el("span", {}, t("Oggi"))),
    el("div", { class: "task-stat" }, el("strong", {}, String(tasks.length)), el("span", {}, t("Aperti"))),
  );

  if (!tasks.length) {
    list.append(el("li", { class: "empty-state" }, t("Tutto fatto"), el("small", {}, t("Nessun task aperto"))));
    return;
  }
  for (const task of tasks) {
    const item = el("li", { class: task.overdue ? "overdue" : "" });
    const edit = () => startEdit(task, item);
    const chip = dueChip(task, todayIso);
    if (chip) { chip.classList.add("editable"); chip.addEventListener("click", edit); }
    item.append(
      el("button", { class: "check", title: t("Completa"), html: uiIcon("check", 3), onclick: () => completeTask(task.id, item) }),
      el("span", { class: "task-title editable", title: t("Clicca per modificare"), onclick: edit }, task.title),
      chip || el("span"),
      el("button", { class: "delete", title: t("Modifica"), html: uiIcon("edit", 2), onclick: edit }),
      el("button", { class: "delete", title: t("Elimina"), html: uiIcon("close", 2), onclick: () => deleteTask(task.id, item) }),
    );
    list.append(item);
  }
}

function renderSummary(data, now = new Date()) {
  const parts = [];
  const timed = data.events.map(eventWindow).filter(Boolean);
  const upcoming = timed.filter((w) => w.start > now);
  if (!data.calendarError) {
    if (!data.events.length) {
      parts.push(t("Agenda libera oggi."));
    } else if (upcoming.length) {
      parts.push(t(upcoming.length === 1
        ? "Hai <b>{n} impegno</b> in arrivo, il prossimo alle <b>{time}</b>."
        : "Hai <b>{n} impegni</b> in arrivo, il prossimo alle <b>{time}</b>.",
      { n: upcoming.length, time: timeFmt.format(upcoming[0].start) }));
    } else {
      parts.push(t("Impegni di oggi completati."));
    }
  }

  const overdue = data.tasks.filter((x) => x.overdue).length;
  const dueToday = data.tasks.filter((x) => x.due === data.date).length;
  if (dueToday || overdue) {
    const bits = [];
    if (dueToday) bits.push(t("<b>{n} task</b> in scadenza oggi", { n: dueToday }));
    if (overdue) bits.push(t(overdue === 1 ? "<b>{n}</b> scaduto" : "<b>{n}</b> scaduti", { n: overdue }));
    parts.push(`${bits.join(t(" e "))}.`.replace(/^./, (c) => c.toUpperCase()));
  }

  const weather = data.weather;
  if (weather) {
    const wetNow = ["drizzle", "rain", "storm", "snow"].includes(weather.current && weather.current.icon);
    const rainy = (weather.hourly || []).find((h) => (h.precipitationProbability || 0) >= 50);
    if (wetNow) {
      const later = (weather.hourly || []).slice(1);
      const dry = later.find((h) => (h.precipitationProbability || 0) < 20);
      const moreRain = later.some((h) => (h.precipitationProbability || 0) >= 50);
      const what = weather.current.icon === "snow" ? t("Nevica") : t("Sta piovendo");
      parts.push(dry && !moreRain
        ? t("{what}, schiarite verso le <b>{time}</b>.", { what, time: dry.time.slice(11, 16) })
        : t("{what}: porta l'ombrello.", { what }));
    } else if (rainy) {
      parts.push(t("Pioggia probabile dalle <b>{time}</b>: porta l'ombrello.", { time: rainy.time.slice(11, 16) }));
    } else if (weather.today && weather.today.max !== null) {
      parts.push(t("Massima di <b>{max}°</b>, niente pioggia in vista.", { max: round(weather.today.max) }));
    }
  }
  $("summary").innerHTML = parts.join(" ");
}

function renderAlarm(alarm) {
  const chip = $("alarm-chip");
  if (!alarm || !alarm.nextAlarm) { chip.replaceChildren(); return; }
  const when = new Date(alarm.nextAlarm);
  chip.replaceChildren(el("span", { html: uiIcon("alarm") }), `${t("Sveglia")} ${shortDateFmt.format(when)} · ${timeFmt.format(when)}`);
}

function setStatus(ok, text) {
  $("status-chip").classList.toggle("error", !ok);
  $("status-text").textContent = text;
}

function renderAll(data) {
  applyTheme(data.weather);
  renderWeather(data.weather, data.weatherError);
  renderAgenda(data.events, data.calendarError, new Date(), data.week, data.date);
  renderTasks(data.tasks, data.date);
  renderAlarm(data.alarm);
  renderSummary(data);
  renderWeek(data.weather);
  Morning.maybeShow(data);
}

/* ------------------------------------------------------------------ morning */

/** A quiet "Buongiorno" card the first time the dashboard is opened or woken in the morning. */
const Morning = (() => {
  const SHOWN_KEY = "nexusMorningShown";
  const VISIBLE_MS = 60 * 1000;
  const host = $("morning");
  let armed = true;
  let timer = null;

  function rows(data, now) {
    const items = [];
    if (data.calendarError) {
      items.push(["calendar", t("Calendario non collegato")]);
    } else {
      const timed = data.events.map((e) => ({ e, w: eventWindow(e) })).filter(({ w }) => w && w.end > now);
      const allDay = data.events.filter((e) => e.allDay);
      if (timed.length) {
        const { e, w } = timed[0];
        const more = timed.length > 1 ? ` · ${t("altri {n}", { n: timed.length - 1 })}` : "";
        items.push(["calendar", `${timeFmt.format(w.start)}  ${e.title}${more}`]);
      } else if (allDay.length) {
        items.push(["calendar", `${t("Tutto il giorno")}  ${allDay[0].title}`]);
      } else {
        items.push(["calendar", t("Agenda libera")]);
      }
    }

    const overdue = data.tasks.filter((x) => x.overdue).length;
    const dueToday = data.tasks.filter((x) => x.due === data.date).length;
    const bits = [];
    if (dueToday) bits.push(t("{n} in scadenza oggi", { n: dueToday }));
    if (overdue) bits.push(t(overdue === 1 ? "{n} scaduto" : "{n} scaduti", { n: overdue }));
    items.push(["tasks", bits.length ? `${t("Task")}: ${bits.join(" · ")}` : t("Nessun task in scadenza")]);

    const weather = data.weather;
    if (weather) {
      const rainy = (weather.hourly || []).find((h) => (h.precipitationProbability || 0) >= 50);
      items.push(["drop", rainy ? t("Pioggia dalle {time}: meglio l'ombrello", { time: rainy.time.slice(11, 16) }) : t("Niente pioggia in vista")]);
    }
    return items;
  }

  function fill(data, now) {
    $("morning-title").textContent = greetingFor(now);
    $("morning-date").textContent = dateFmt.format(now);
    const weather = data.weather;
    $("morning-icon").innerHTML = weather ? weatherIcon(weather.current.icon, weather.current.isDay) : "";
    $("morning-temp").textContent = weather ? `${round(weather.current.temperature)}°` : "";
    $("morning-desc").textContent = weather
      ? `${t(weather.current.description)} · ${round(weather.today.min)}° / ${round(weather.today.max)}°`
      : "";
    $("morning-rows").replaceChildren(...rows(data, now).map(([icon, text]) =>
      el("li", {}, el("span", { class: "morning-row-icon", html: uiIcon(icon) }), el("span", {}, text))));
  }

  function show() {
    host.hidden = false;
    requestAnimationFrame(() => host.classList.add("open"));
    clearTimeout(timer);
    timer = setTimeout(hide, VISIBLE_MS);
  }

  function hide() {
    if (host.hidden) return;
    clearTimeout(timer);
    host.classList.remove("open");
    setTimeout(() => { host.hidden = true; }, 450);
  }

  function maybeShow(data) {
    if (!armed) return;
    armed = false;
    const now = new Date();
    const hour = now.getHours();
    if (hour < 5 || hour >= 11 || localStorage.getItem(SHOWN_KEY) === data.date) return;
    localStorage.setItem(SHOWN_KEY, data.date);
    fill(data, now);
    show();
  }

  host.addEventListener("click", hide);
  document.addEventListener("keydown", (event) => { if (event.key === "Escape") hide(); });

  /** Called when the page is opened again or the PC wakes up, so the card can appear once. */
  function arm() { armed = true; }

  return { maybeShow, arm };
})();

/* ------------------------------------------------------------------ actions */

function showSync(lastSync) {
  $("sync-chip").textContent = lastSync ? t("Sync {time}", { time: timeFmt.format(new Date(lastSync)) }) : t("Nessuna sync");
}

/** Cheap check every few seconds: reload everything only when the hub says something changed. */
let lastRevision = null;
async function pollRevision() {
  if ($("app").classList.contains("hidden") || document.hidden) return;
  try {
    const state = await api("/api/revision");
    const key = `${state.revision}:${state.marker}`;
    if (lastRevision !== null && key !== lastRevision) refresh();
    lastRevision = key;
    showSync(state.lastSync);
  } catch { /* the regular refresh reports connection problems */ }
}

async function refresh() {
  clearTimeout(retryTimer);
  try {
    const data = await api("/api/today");
    lastData = data;
    failures = 0;
    renderAll(data);
    showSync(data.lastSync);
    setStatus(true, t("Aggiornato {time}", { time: timeFmt.format(new Date()) }));
  } catch (err) {
    failures += 1;
    const wait = Math.min(60000, 3000 * 2 ** (failures - 1));
    setStatus(false, t("Offline · riprovo tra {s} s", { s: Math.round(wait / 1000) }));
    retryTimer = setTimeout(refresh, wait);
  }
}

async function completeTask(id, item) {
  item.classList.add("leaving");
  try {
    await api(`/api/tasks/${id}`, { method: "PATCH", body: JSON.stringify({ done: true }) });
  } finally {
    setTimeout(refresh, 350);
  }
}

async function deleteTask(id, item) {
  item.classList.add("leaving");
  try {
    await api(`/api/tasks/${id}`, { method: "DELETE" });
  } finally {
    setTimeout(refresh, 350);
  }
}

function showLogin() {
  $("app").classList.add("hidden");
  $("login").classList.remove("hidden");
}

function showApp() {
  $("login").classList.add("hidden");
  $("app").classList.remove("hidden");
  refresh();
  Assistant.init();
  if (!showApp.heartbeat) {
    showApp.heartbeat = setInterval(() => {
      api("/api/dashboard/heartbeat", { method: "POST" }).catch(() => {});
    }, 5000);
    setInterval(pollRevision, REVISION_MS);
    api("/api/dashboard/heartbeat", { method: "POST" }).catch(() => {});
  }
}

$("login-form").addEventListener("submit", (event) => {
  event.preventDefault();
  localStorage.setItem(TOKEN_KEY, $("login-token").value.trim());
  showApp();
});

$("task-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const title = $("task-title").value.trim();
  if (!title) return;
  const due = $("task-due").value || null;
  await api("/api/tasks", { method: "POST", body: JSON.stringify({ title, due }) });
  $("task-title").value = "";
  $("task-due").value = "";
  refresh();
});

/* ------------------------------------------------------------------ assistant */

const Assistant = (() => {
  const SUGGESTIONS = ["Cosa ho in programma oggi?", "Mi serve l'ombrello?", "Che task ho in scadenza?", "Com'è il meteo domani?"].map((text) => t(text));
  const panel = $("assistant");
  const log = $("assistant-log");
  const input = $("assistant-input");
  let conversationId = newId();
  let name = "Lilith";
  let enabled = null;
  let busy = false;

  function newId() {
    return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
  }

  function scrollDown() {
    log.scrollTop = log.scrollHeight;
  }

  function bubble(role, text, extra = {}) {
    const node = el("div", { class: `msg ${role}${extra.error ? " error" : ""}` }, el("div", { class: "msg-text" }, text));
    if (extra.actions && extra.actions.length) {
      node.append(el("div", { class: "msg-actions" },
        ...extra.actions.map((a) => el("span", { class: "msg-action", html: uiIcon("check", 2.4) }, a))));
    }
    log.append(node);
    scrollDown();
    return node;
  }

  function renderSuggestions() {
    const host = $("assistant-suggestions");
    host.replaceChildren();
    if (log.children.length || !enabled) return;
    for (const text of SUGGESTIONS) {
      host.append(el("button", { type: "button", class: "suggestion", onclick: () => send(text) }, text));
    }
  }

  function greet() {
    log.replaceChildren();
    if (enabled === false) {
      bubble("bot", t("Non sono ancora configurata. Sul PC hub aggiungi GEMINI_API_KEY nel file hub-server\\.env (chiave gratuita da aistudio.google.com) e riavvia il server."), { error: true });
    }
    renderSuggestions();
  }

  async function init() {
    try {
      const info = await api("/api/assistant");
      name = info.name;
      enabled = info.enabled;
    } catch {
      enabled = null;
    }
    $("assistant-name").textContent = name;
    $("assistant-orb-label").textContent = name;
    input.placeholder = t("Chiedi a {name}...", { name });
    if (!log.children.length || enabled === false) greet();
  }

  function open() {
    panel.inert = false;
    panel.classList.add("open");
    panel.setAttribute("aria-hidden", "false");
    $("assistant-launcher").classList.add("hidden");
    setTimeout(() => input.focus(), 150);
  }

  function close() {
    Voice.stop();
    panel.inert = true;
    panel.classList.remove("open");
    panel.setAttribute("aria-hidden", "true");
    $("assistant-launcher").classList.remove("hidden");
  }

  const Voice = (() => {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    const synth = window.speechSynthesis;
    const mic = $("assistant-mic");
    const ERRORS = {
      "not-allowed": t("Microfono non autorizzato: consenti l'accesso al microfono nel browser."),
      "service-not-allowed": t("Microfono non autorizzato: consenti l'accesso al microfono nel browser."),
      "audio-capture": t("Nessun microfono trovato."),
      "network": t("Riconoscimento vocale non raggiungibile: serve la connessione a internet."),
      "no-speech": t("Non ho sentito nulla, riprova."),
    };
    let recognizer = null;

    function setState(state) {
      panel.classList.toggle("listening", state === "listening");
      panel.classList.toggle("speaking", state === "speaking");
      mic.innerHTML = uiIcon(state === "listening" ? "stop" : "mic", 2);
      $("assistant-sub").textContent =
        state === "listening" ? t("Ti ascolto...") : state === "speaking" ? t("Sta parlando...") : t("Assistente personale");
    }

    function spokenVoice() {
      const voices = synth.getVoices().filter((v) => v.lang.toLowerCase().startsWith(LANG));
      return voices.find((v) => /natural|online/i.test(v.name)) || voices[0] || null;
    }

    function spoken(text) {
      return text.replace(/^\s*[-•*]\s*/gm, "").replace(/([^.:;!?])\s*\n+/g, "$1. ").replace(/\s*\n+/g, " ");
    }

    function speak(text) {
      if (!synth) return;
      synth.cancel();
      const utterance = new SpeechSynthesisUtterance(spoken(text));
      utterance.lang = SPEECH_LANG;
      const voice = spokenVoice();
      if (voice) utterance.voice = voice;
      utterance.onstart = () => setState("speaking");
      utterance.onend = utterance.onerror = () => setState(null);
      synth.speak(utterance);
    }

    function stop() {
      if (recognizer) recognizer.abort();
      if (synth) synth.cancel();
      setState(null);
    }

    function listen() {
      if (!Recognition || busy) return;
      if (recognizer) { recognizer.stop(); return; }
      if (synth) synth.cancel();
      const current = new Recognition();
      recognizer = current;
      current.lang = SPEECH_LANG;
      current.interimResults = true;
      current.maxAlternatives = 1;
      let heard = "";
      let failure = null;
      current.onresult = (event) => {
        let interim = "";
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const result = event.results[i];
          if (result.isFinal) heard += result[0].transcript; else interim += result[0].transcript;
        }
        input.value = `${heard}${interim}`.trim();
        autosize();
      };
      current.onerror = (event) => { failure = event.error; };
      current.onend = () => {
        recognizer = null;
        setState(null);
        const text = input.value.trim();
        if (failure && failure !== "aborted") bubble("bot", ERRORS[failure] || t("Errore del microfono ({failure}).", { failure }), { error: true });
        else if (!failure && text) send(text, { speak: true });
      };
      input.value = "";
      setState("listening");
      try {
        current.start();
      } catch {
        recognizer = null;
        setState(null);
        bubble("bot", ERRORS["not-allowed"], { error: true });
      }
    }

    const orbMic = $("assistant-orb-mic");
    orbMic.innerHTML = uiIcon("mic", 2);
    if (Recognition) {
      mic.classList.remove("hidden");
      orbMic.classList.remove("hidden");
      if (synth) synth.getVoices();
    }
    mic.addEventListener("click", listen);
    orbMic.addEventListener("click", () => { open(); listen(); });
    setState(null);
    return { available: Boolean(Recognition), listen, speak, stop };
  })();

  async function send(text, { speak = false } = {}) {
    const message = text.trim();
    if (!message || busy) return;
    busy = true;
    input.value = "";
    autosize();
    $("assistant-suggestions").replaceChildren();
    bubble("user", message);
    const thinking = el("div", { class: "msg bot thinking" }, el("span"), el("span"), el("span"));
    log.append(thinking);
    scrollDown();
    panel.classList.add("busy");
    try {
      const result = await api("/api/assistant/chat", { method: "POST", body: JSON.stringify({ conversationId, message, language: LANG }) });
      thinking.remove();
      bubble("bot", result.reply, { actions: result.actions });
      if (speak) Voice.speak(result.reply);
      if (result.actions.length) refresh();
    } catch (err) {
      thinking.remove();
      bubble("bot", err.message || t("Qualcosa è andato storto."), { error: true });
    } finally {
      busy = false;
      panel.classList.remove("busy");
      input.focus();
    }
  }

  const SOURCES = { phone: t("Telefono"), sport: "Sport", pc: "PC" };
  const historyList = $("assistant-history");

  function whenLabel(iso) {
    const date = new Date(iso);
    const now = new Date();
    if (date.toDateString() === now.toDateString()) return timeFmt.format(date);
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    if (date.toDateString() === yesterday.toDateString()) return t("Ieri");
    return shortDateFmt.format(date);
  }

  function hideHistory() {
    panel.classList.remove("history-open");
    $("assistant-history-btn").classList.remove("active");
    renderSuggestions();
  }

  async function showHistory() {
    if (panel.classList.contains("history-open")) { hideHistory(); return; }
    Voice.stop();
    panel.classList.add("history-open");
    $("assistant-history-btn").classList.add("active");
    historyList.replaceChildren(el("div", { class: "history-empty" }, t("Carico lo storico...")));
    try {
      const items = await api("/api/assistant/history");
      renderHistory(items);
    } catch (err) {
      historyList.replaceChildren(el("div", { class: "history-empty" }, err.message || t("Storico non disponibile.")));
    }
  }

  function renderHistory(items) {
    if (!items.length) {
      historyList.replaceChildren(el("div", { class: "history-empty" }, t("Nessuna conversazione salvata. Le prossime resteranno qui.")));
      return;
    }
    historyList.replaceChildren(...items.map((item) => {
      const meta = `${SOURCES[item.source] || "PC"} · ${t("{n} messaggi", { n: item.messages })}`;
      return el("div", { class: "history-item", role: "listitem" },
        el("button", { type: "button", class: "history-main", onclick: () => openConversation(item.id) },
          el("span", { class: "history-title" }, item.title),
          el("span", { class: "history-meta" }, meta)),
        el("span", { class: "history-when" }, whenLabel(item.lastAt)),
        el("button", {
          type: "button", class: "history-delete", title: t("Elimina conversazione"), html: uiIcon("close", 2),
          onclick: () => removeConversation(item.id),
        }));
    }));
  }

  async function openConversation(id) {
    try {
      const detail = await api(`/api/assistant/history/${encodeURIComponent(id)}`);
      conversationId = id;
      log.replaceChildren();
      for (const message of detail.messages) {
        bubble(message.role === "user" ? "user" : "bot", message.text, { actions: message.actions });
      }
      hideHistory();
      scrollDown();
      input.focus();
    } catch (err) {
      historyList.prepend(el("div", { class: "history-empty" }, err.message || t("Conversazione non trovata.")));
    }
  }

  async function removeConversation(id) {
    try {
      await api(`/api/assistant/history/${encodeURIComponent(id)}`, { method: "DELETE" });
    } catch { /* already gone */ }
    if (id === conversationId) { conversationId = newId(); greet(); }
    renderHistory(await api("/api/assistant/history").catch(() => []));
  }

  async function reset() {
    Voice.stop();
    hideHistory();
    const old = conversationId;
    conversationId = newId();
    greet();
    if (enabled) api(`/api/assistant/chat/${old}`, { method: "DELETE" }).catch(() => {});
    input.focus();
  }

  function autosize() {
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 160)}px`;
  }

  panel.inert = true;
  $("assistant-orb").addEventListener("click", open);
  $("assistant-close").addEventListener("click", close);
  $("assistant-new").addEventListener("click", reset);
  $("assistant-close").innerHTML = uiIcon("close", 2);
  $("assistant-new").innerHTML = uiIcon("restart", 2);
  $("assistant-history-btn").innerHTML = uiIcon("history", 2);
  $("assistant-history-btn").addEventListener("click", showHistory);
  panel.querySelector(".send").innerHTML = uiIcon("send", 2);
  $("assistant-form").addEventListener("submit", (event) => { event.preventDefault(); send(input.value); });
  input.addEventListener("input", autosize);
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); send(input.value); }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && panel.classList.contains("open")) close();
    const typing = ["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName);
    if (typing || $("app").classList.contains("hidden") || !$("settings").hidden) return;
    if (event.key === "/") { event.preventDefault(); open(); }
    if (event.key.toLowerCase() === "m" && Voice.available) { event.preventDefault(); open(); Voice.listen(); }
  });

  return { init, open, close, send };
})();

/* ------------------------------------------------------------------ settings */

const Settings = (() => {
  const PAGES = [
    { id: "sync", label: "Sync", icon: "sync" },
    { id: "lilith", label: "Lilith", icon: "spark" },
    { id: "place", label: "Luogo e meteo", icon: "pin" },
    { id: "calendar", label: "Calendario", icon: "calendar" },
    { id: "wake", label: "Promemoria e sveglia", icon: "alarm" },
    { id: "language", label: "Lingua", icon: "globe" },
    { id: "info", label: "Info", icon: "info" },
  ];
  const REOPEN_KEY = "nexusSettingsPage";
  const host = $("settings");
  const menu = $("settings-menu");
  const body = $("settings-body");
  const secondsFmt = new Intl.DateTimeFormat(LOCALE, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  let page = "sync";
  let info = null;
  let loadError = "";
  let flash = null;
  let timer = null;

  function when(iso, withSeconds = false) {
    if (!iso) return "--";
    const d = new Date(iso);
    const time = (withSeconds ? secondsFmt : timeFmt).format(d);
    return d.toDateString() === new Date().toDateString() ? t("oggi {time}", { time }) : `${shortDateFmt.format(d)} ${time}`;
  }

  function counts(c) {
    if (!c || !Object.keys(c).length) return null;
    const parts = [
      c.tasks ? t("{n} task", { n: c.tasks }) : null,
      c.messages ? t("{n} messaggi", { n: c.messages }) : null,
      c.deletions ? t("{n} chat eliminate", { n: c.deletions }) : null,
    ].filter(Boolean);
    return parts.length ? parts.join(", ") : t("niente di nuovo");
  }

  const group = (title, ...children) => el("div", { class: "set-group" }, title ? el("h3", {}, title) : null, ...children);
  const row = (label, value, tone) => el("div", { class: "set-row" },
    el("span", { class: "set-label" }, label),
    el("span", { class: `set-value${tone ? ` ${tone}` : ""}` }, value));
  const note = (text) => el("p", { class: "set-note" }, text);
  const field = (label, attrs) => el("label", { class: "set-field" }, el("span", {}, label), el("input", { autocomplete: "off", ...attrs }));
  const button = (label, onclick, primary = false) =>
    el("button", { type: "button", class: primary ? "btn-primary set-btn" : "set-btn", onclick }, label);

  async function load() {
    try {
      info = await api("/api/info");
      loadError = "";
    } catch (err) {
      loadError = err.message;
    }
    render();
  }

  async function save(changes) {
    try {
      info = await api("/api/settings", { method: "PUT", body: JSON.stringify(changes) });
      flash = { ok: true, text: t("Salvato") };
      refresh();
      Assistant.init();
    } catch (err) {
      flash = { ok: false, text: err.status === 422 ? t("Controlla i valori inseriti") : err.message };
    }
    render();
  }

  function renderMenu() {
    menu.replaceChildren(...PAGES.map((p) => el("button", {
      type: "button",
      class: `settings-link${p.id === page ? " active" : ""}`,
      onclick: () => { page = p.id; flash = null; render(); },
    }, el("span", { class: "settings-link-icon", html: uiIcon(p.icon) }), t(p.label))));
  }

  function syncPage() {
    const log = (info.syncLog || []).slice(0, 10);
    const last = log[0];
    const hero = info.lastSync
      ? el("div", { class: "set-hero ok" }, el("strong", {}, t("Ultima sync riuscita")), el("span", {}, when(info.lastSync)))
      : el("div", { class: "set-hero warn" }, el("strong", {}, t("Nessuna sync ricevuta")), el("span", {}, t("Il telefono non ha ancora completato una sincronizzazione con questo PC")));
    const failing = last && !last.ok
      ? note(t("L'ultimo tentativo ({time}) è fallito: {detail}.", { time: when(last.at, true), detail: t(last.detail) }))
      : null;
    const copy = button(t("Copia"), async () => {
      try { await navigator.clipboard.writeText(info.phoneAddress); flash = { ok: true, text: t("Indirizzo copiato") }; }
      catch { flash = { ok: false, text: t("Copia non disponibile: scrivilo a mano") }; }
      render();
    });
    const list = log.length
      ? el("ul", { class: "set-log" }, ...log.map((e) => el("li", { class: e.ok ? "ok" : "warn" },
          el("span", { class: "set-log-dot" }),
          el("div", { class: "set-log-main" },
            el("div", {}, el("strong", {}, e.ok ? t("Riuscita") : t("Errore {status}", { status: e.status })), ` · ${t(e.detail)}`),
            el("small", {}, [
              e.client ? t("da {client}", { client: e.client }) : null,
              counts(e.received) ? t("ricevuti: {what}", { what: counts(e.received) }) : null,
              counts(e.sent) ? t("inviati: {what}", { what: counts(e.sent) }) : null,
              e.durationMs ? `${e.durationMs} ms` : null,
            ].filter(Boolean).join(" · "))),
          el("span", { class: "set-log-time" }, when(e.at, true)))))
      : note(t("Nessun tentativo è arrivato a questo PC. Se il telefono dice \"PC non raggiungibile\", controlla che sia sullo stesso Wi‑Fi, che l'indirizzo sia giusto e che il firewall di Windows lasci passare la porta {port}.", { port: info.port }));
    return [
      hero,
      failing,
      group(t("Collega il telefono"),
        el("div", { class: "set-row" },
          el("span", { class: "set-label" }, t("Indirizzo da scrivere nell'app")),
          el("span", { class: "set-value strong" }, info.phoneAddress), copy),
        row(t("Token"), t("{hint} (HUB_API_TOKEN nel file .env)", { hint: info.tokenHint })),
        note(t("Nell'app: Impostazioni → Sync. Se lasci l'indirizzo vuoto il telefono cerca il PC da solo sulla rete di casa."))),
      group(t("Ultimi tentativi"), list,
        note(t("Mostro solo gli ultimi 10 scambi con dati: i controlli a vuoto non vengono registrati.")),
        el("div", { class: "set-actions" }, button(t("Aggiorna"), load))),
    ];
  }

  function lilithPage() {
    const a = info.assistant;
    const name = field(t("Nome"), { value: a.name, maxlength: 30 });
    const model = field(t("Modello Gemini"), { value: a.model, maxlength: 60, spellcheck: "false" });
    return [
      group(t("Stato"),
        row(t("Assistente"), a.enabled ? t("Attiva") : t("Non configurata"), a.enabled ? "ok" : "warn"),
        row(t("Modello"), a.model),
        a.enabled ? null : note(t("Aggiungi GEMINI_API_KEY nel file hub-server\\.env (chiave gratuita da aistudio.google.com) e riavvia il server. La chiave arriva al telefono con la sync."))),
      group(t("Nome"),
        name,
        note(t("Il nuovo nome arriva anche al telefono alla prossima sync.")),
        el("div", { class: "set-actions" }, button(t("Salva"), () => save({ assistantName: name.querySelector("input").value }), true))),
      group(t("Modello"),
        model,
        note(t("Il nome del modello Gemini usato da Lilith, per esempio gemini-3.5-flash-lite. Cambia subito sul PC e arriva anche al telefono con la sync. Se il nome non esiste Lilith lo dirà nella chat.")),
        el("div", { class: "set-actions" }, button(t("Salva"), () => save({ geminiModel: model.querySelector("input").value }), true))),
    ];
  }

  function placePage() {
    const p = info.place;
    const city = field(t("Città"), { value: p.city, maxlength: 60 });
    const lat = field(t("Latitudine"), { value: p.latitude, type: "number", step: "0.0001", min: -90, max: 90 });
    const lon = field(t("Longitudine"), { value: p.longitude, type: "number", step: "0.0001", min: -180, max: 180 });
    const input = (f) => f.querySelector("input");
    const find = button(t("Trova coordinate"), async () => {
      const name = input(city).value.trim();
      if (!name) return;
      try {
        const url = `https://geocoding-api.open-meteo.com/v1/search?count=1&language=${LANG}&name=${encodeURIComponent(name)}`;
        const hit = (await (await fetch(url)).json()).results?.[0];
        if (!hit) throw new Error();
        input(city).value = hit.name;
        input(lat).value = hit.latitude.toFixed(4);
        input(lon).value = hit.longitude.toFixed(4);
        flash = { ok: true, text: t("Trovata: {place}. Premi Salva.", { place: `${hit.name}${hit.admin1 ? `, ${hit.admin1}` : ""}` }) };
      } catch {
        flash = { ok: false, text: t("Città non trovata") };
      }
      renderFlash();
    });
    return [
      group(t("Luogo del meteo"),
        city,
        el("div", { class: "set-pair" }, lat, lon),
        note(t("Vale per la dashboard, Lilith e, alla prossima sync, anche per il telefono.")),
        el("div", { class: "set-actions" }, find, button(t("Salva"), () => save({
          city: input(city).value,
          latitude: Number(input(lat).value),
          longitude: Number(input(lon).value),
        }), true))),
    ];
  }

  function calendarPage() {
    const c = info.calendar;
    return [
      group("Google Calendar",
        row(t("Stato"), c.connected ? t("Collegato") : t("Non collegato"), c.connected ? "ok" : "warn"),
        c.error ? note(t(c.error)) : null,
        note(t("Per collegarlo o rifare il login: sul PC hub apri un terminale nella cartella hub-server ed esegui .venv\\Scripts\\python -m app.google_auth, poi riavvia il server."))),
      group(t("Telefono"), note(t("Il telefono legge il calendario direttamente da Android, anche senza PC: il permesso si gestisce nell'app, Impostazioni → Calendario."))),
    ];
  }

  function wakePage() {
    const alarm = info.alarm;
    const lead = field(t("Accendi lo schermo prima della sveglia (minuti)"), { value: info.wake.leadMinutes, type: "number", min: 0, max: 120 });
    return [
      group(t("Sveglia del telefono"),
        row(t("Prossima sveglia"), alarm.nextAlarm ? when(alarm.nextAlarm) : t("Nessuna")),
        row(t("Il PC si sveglia"), alarm.scheduled ? when(alarm.wakeAt) : t("Non programmato")),
        info.wake.enabled ? null : note(t("Il risveglio automatico è disattivato nel file .env (WAKE_ENABLED)."))),
      group(t("Anticipo"),
        lead,
        el("div", { class: "set-actions" }, button(t("Salva"), () => save({ wakeLeadMinutes: Number(lead.querySelector("input").value) }), true))),
      group(t("Promemoria"), note(t("I promemoria degli impegni e il riepilogo del mattino arrivano sul telefono: si regolano nell'app, Impostazioni → Promemoria e sveglia."))),
    ];
  }

  function languagePage() {
    const choices = [["auto", t("Automatica (come il dispositivo)")], ["it", "Italiano"], ["en", "English"]];
    return [
      group(t("Lingua dell'interfaccia"),
        el("div", { class: "set-choices" }, ...choices.map(([value, label]) => el("button", {
          type: "button",
          class: `set-choice${value === I18n.preference ? " active" : ""}`,
          onclick: () => {
            if (value === I18n.preference) return;
            sessionStorage.setItem(REOPEN_KEY, "language");
            I18n.setPreference(value);
          },
        }, label))),
        note(t("Vale per questa dashboard e si applica subito. Il telefono ha la sua scelta nell'app, Impostazioni → Lingua."))),
      group(t("Lilith"),
        note(t("In inglese Lilith risponde sempre tramite Gemini: le risposte rapide locali, scritte in italiano, restano spente."))),
    ];
  }

  function infoPage() {
    return [
      el("div", { class: "set-hero" }, el("strong", {}, "NEXUS"), el("span", {}, t("Versione {version}", { version: info.version }))),
      group(t("Progetto"),
        note(t("Hub personale: questa dashboard sul PC, l'app e i widget sul telefono, l'assistente Lilith. Tutto resta in casa tra PC e telefono.")),
        row(t("Versione"), info.version),
        row(t("Server avviato"), when(info.startedAt)),
        row(t("Porta"), String(info.port))),
      group(t("Scorciatoie"),
        row("/", t("Apri Lilith")),
        row("M", t("Parla con Lilith")),
        row("I", t("Impostazioni")),
        row("Esc", t("Chiudi"))),
    ];
  }

  function renderFlash() {
    body.querySelector(".set-flash")?.remove();
    if (flash) body.querySelector(".set-title")?.after(el("div", { class: `set-flash ${flash.ok ? "ok" : "warn"}` }, flash.text));
  }

  function render() {
    renderMenu();
    const current = PAGES.find((p) => p.id === page);
    const head = el("h2", { class: "set-title" }, t(current.label));
    if (!info) {
      body.replaceChildren(head, note(loadError ? t("Impostazioni non disponibili: {error}", { error: loadError }) : t("Caricamento...")));
      return;
    }
    const pages = { sync: syncPage, lilith: lilithPage, place: placePage, calendar: calendarPage, wake: wakePage, language: languagePage, info: infoPage };
    body.replaceChildren(head, ...pages[page]().filter(Boolean));
    renderFlash();
  }

  function open(target = "sync") {
    page = target;
    flash = null;
    host.hidden = false;
    requestAnimationFrame(() => host.classList.add("open"));
    render();
    load();
    clearInterval(timer);
    timer = setInterval(() => { if (page === "sync") load(); }, 10 * 1000);
  }

  function close() {
    clearInterval(timer);
    host.classList.remove("open");
    setTimeout(() => { host.hidden = true; }, 250);
  }

  $("settings-btn").innerHTML = uiIcon("gear");
  $("settings-btn").addEventListener("click", () => open());
  $("sync-chip").addEventListener("click", () => open("sync"));
  $("settings-close").innerHTML = uiIcon("close", 2);
  $("settings-close").addEventListener("click", close);
  host.addEventListener("click", (event) => { if (event.target === host) close(); });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !host.hidden) { close(); return; }
    const typing = ["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName);
    if (typing || $("app").classList.contains("hidden") || !host.hidden) return;
    if (event.key.toLowerCase() === "i") { event.preventDefault(); open(); }
  });

  const reopen = sessionStorage.getItem(REOPEN_KEY);
  if (reopen) {
    sessionStorage.removeItem(REOPEN_KEY);
    setTimeout(() => open(reopen), 400);
  }

  return { open, close };
})();

/* ------------------------------------------------------------------ timers */

function tick() {
  const now = Date.now();
  const resumed = now - lastTick > RESUME_GAP_MS;
  lastTick = now;
  renderClock();
  const minute = new Date(now).getMinutes();
  if (lastData && minute !== lastMinute) {
    lastMinute = minute;
    renderAgenda(lastData.events, lastData.calendarError, new Date(), lastData.week, lastData.date);
    renderSummary(lastData);
    applyTheme(lastData.weather);
  }
  if (resumed && !$("app").classList.contains("hidden")) {
    Morning.arm();
    refresh();
  }
}

renderClock();
setInterval(tick, 1000);
setInterval(() => { if (!$("app").classList.contains("hidden")) refresh(); }, REFRESH_MS);
document.addEventListener("visibilitychange", () => {
  if (document.hidden) return;
  Morning.arm();
  refresh();
});
window.addEventListener("online", refresh);
window.addEventListener("focus", refresh);

let resizeTimer = null;
window.addEventListener("resize", () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => { if (lastData) renderForecast(lastData.weather); }, 200);
});

if (readToken()) showApp(); else showLogin();
