"use strict";

const TOKEN_KEY = "dailyHubToken";
const REFRESH_MS = 60 * 1000;
const RESUME_GAP_MS = 15 * 1000;

const $ = (id) => document.getElementById(id);

const timeFmt = new Intl.DateTimeFormat("it-IT", { hour: "2-digit", minute: "2-digit" });
const dateFmt = new Intl.DateTimeFormat("it-IT", { weekday: "long", day: "numeric", month: "long" });
const shortDateFmt = new Intl.DateTimeFormat("it-IT", { weekday: "short", day: "numeric", month: "short" });

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
  if (total < 1) return "ora";
  if (total < 60) return `${total} min`;
  const h = Math.floor(total / 60);
  const m = total % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}

function greetingFor(date) {
  const h = date.getHours();
  if (h >= 5 && h < 12) return "Buongiorno";
  if (h >= 12 && h < 18) return "Buon pomeriggio";
  if (h >= 18 && h < 23) return "Buonasera";
  return "Buonanotte";
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
    throw new Error("Token non valido");
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const error = new Error(typeof body?.detail === "string" ? body.detail : `HTTP ${response.status}`);
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
    $("wx-desc").textContent = error || "Meteo non disponibile";
    $("wx-city").textContent = "";
    renderForecast(null);
    return;
  }
  const { current, today } = weather;
  icon.innerHTML = weatherIcon(current.icon, current.isDay);
  $("wx-temp").textContent = round(current.temperature);
  $("wx-desc").textContent = current.description;
  $("wx-city").textContent = weather.city;

  const sunrise = localDate(today.sunrise);
  const sunset = localDate(today.sunset);
  const items = [
    ["minmax", "Min / Max", `${round(today.min)}° / ${round(today.max)}°`],
    ["temp", "Percepita", `${round(current.apparentTemperature)}°`],
    ["drop", "Pioggia", `${round(today.precipitationProbability)}%`],
    ["wind", "Vento", `${round(current.windSpeed)} km/h`],
    ["sunrise", "Alba", sunrise ? timeFmt.format(sunrise) : "--"],
    ["sunset", "Tramonto", sunset ? timeFmt.format(sunset) : "--"],
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
    host.append(el("div", { class: "empty-state" }, "Previsioni non disponibili"));
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

function eventWindow(event) {
  if (event.allDay) return null;
  const start = localDate(event.start);
  const end = localDate(event.end) || start;
  return start ? { start, end } : null;
}

function renderAgenda(events, error, now = new Date()) {
  const next = $("next-event");
  const timeline = $("timeline");
  next.replaceChildren();
  timeline.replaceChildren();
  $("events-count").textContent = events.length ? `${events.length} oggi` : "";

  if (error) {
    const notConfigured = error.includes("non configurato");
    timeline.append(el("li", { class: "empty-state" },
      el("span", { class: notConfigured ? "" : "error-text" }, notConfigured ? "Calendario non collegato" : error),
      notConfigured ? el("small", {}, "Sul PC hub: python -m app.google_auth") : null,
    ));
    return;
  }
  if (!events.length) {
    timeline.append(el("li", { class: "empty-state" }, "Nessun impegno oggi", el("small", {}, "Giornata libera")));
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
        isNow ? "In corso" : "Prossimo",
        el("span", { class: "next-countdown" }, isNow ? `fino alle ${timeFmt.format(w.end)}` : `tra ${formatDuration(w.start - now)}`),
      ),
      el("div", { class: "next-title" }, e.title),
      el("div", { class: "next-meta" }, `${timeFmt.format(w.start)} – ${timeFmt.format(w.end)}${e.location ? ` · ${e.location}` : ""}`),
    );
  }

  for (const { e, w } of timed) {
    let state = "";
    if (w && w.end <= now) state = "past";
    else if (w && w.start <= now) state = "now";
    timeline.append(el("li", { class: state },
      el("div", { class: "tl-time" },
        w ? timeFmt.format(w.start) : "Giorno",
        w ? el("small", {}, timeFmt.format(w.end)) : el("small", {}, "intero"),
      ),
      el("div", { class: "tl-body" },
        el("div", { class: "tl-title" }, e.title, state === "now" ? el("span", { class: "badge" }, "ORA") : null),
        e.location ? el("div", { class: "tl-sub" }, e.location) : null,
      ),
    ));
  }
}

function dueChip(task, todayIso) {
  if (!task.due) return null;
  if (task.overdue) return el("span", { class: "due overdue" }, `Scaduto · ${shortDateFmt.format(new Date(`${task.due}T12:00:00`))}`);
  if (task.due === todayIso) return el("span", { class: "due today" }, "Oggi");
  return el("span", { class: "due" }, shortDateFmt.format(new Date(`${task.due}T12:00:00`)));
}

function renderTasks(tasks, todayIso) {
  const list = $("tasks");
  list.replaceChildren();
  const overdue = tasks.filter((t) => t.overdue).length;
  const dueToday = tasks.filter((t) => t.due === todayIso).length;
  $("tasks-count").textContent = tasks.length ? `${tasks.length} aperti` : "";

  const stats = $("task-stats");
  stats.replaceChildren(
    el("div", { class: `task-stat ${overdue ? "warn" : ""}` }, el("strong", {}, String(overdue)), el("span", {}, "Scaduti")),
    el("div", { class: `task-stat ${dueToday ? "ok" : ""}` }, el("strong", {}, String(dueToday)), el("span", {}, "Oggi")),
    el("div", { class: "task-stat" }, el("strong", {}, String(tasks.length)), el("span", {}, "Aperti")),
  );

  if (!tasks.length) {
    list.append(el("li", { class: "empty-state" }, "Tutto fatto", el("small", {}, "Nessun task aperto")));
    return;
  }
  for (const task of tasks) {
    const item = el("li", { class: task.overdue ? "overdue" : "" });
    item.append(
      el("button", { class: "check", title: "Completa", html: uiIcon("check", 3), onclick: () => completeTask(task.id, item) }),
      el("span", { class: "task-title" }, task.title),
      dueChip(task, todayIso) || el("span"),
      el("button", { class: "delete", title: "Elimina", html: uiIcon("close", 2), onclick: () => deleteTask(task.id, item) }),
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
      parts.push("Agenda libera oggi.");
    } else if (upcoming.length) {
      parts.push(`Hai <b>${upcoming.length} ${upcoming.length === 1 ? "impegno" : "impegni"}</b> in arrivo, il prossimo alle <b>${timeFmt.format(upcoming[0].start)}</b>.`);
    } else {
      parts.push("Impegni di oggi completati.");
    }
  }

  const overdue = data.tasks.filter((t) => t.overdue).length;
  const dueToday = data.tasks.filter((t) => t.due === data.date).length;
  if (dueToday || overdue) {
    const bits = [];
    if (dueToday) bits.push(`<b>${dueToday} task</b> in scadenza oggi`);
    if (overdue) bits.push(`<b>${overdue}</b> ${overdue === 1 ? "scaduto" : "scaduti"}`);
    parts.push(`${bits.join(" e ")}.`.replace(/^./, (c) => c.toUpperCase()));
  }

  const weather = data.weather;
  if (weather) {
    const wetNow = ["drizzle", "rain", "storm", "snow"].includes(weather.current && weather.current.icon);
    const rainy = (weather.hourly || []).find((h) => (h.precipitationProbability || 0) >= 50);
    if (wetNow) {
      const later = (weather.hourly || []).slice(1);
      const dry = later.find((h) => (h.precipitationProbability || 0) < 20);
      const moreRain = later.some((h) => (h.precipitationProbability || 0) >= 50);
      const what = weather.current.icon === "snow" ? "Nevica" : "Sta piovendo";
      parts.push(dry && !moreRain
        ? `${what}, schiarite verso le <b>${dry.time.slice(11, 16)}</b>.`
        : `${what}: porta l'ombrello.`);
    } else if (rainy) {
      parts.push(`Pioggia probabile dalle <b>${rainy.time.slice(11, 16)}</b>: porta l'ombrello.`);
    } else if (weather.today && weather.today.max !== null) {
      parts.push(`Massima di <b>${round(weather.today.max)}°</b>, niente pioggia in vista.`);
    }
  }
  $("summary").innerHTML = parts.join(" ");
}

function renderAlarm(alarm) {
  const chip = $("alarm-chip");
  if (!alarm || !alarm.nextAlarm) { chip.replaceChildren(); return; }
  const when = new Date(alarm.nextAlarm);
  chip.replaceChildren(el("span", { html: uiIcon("alarm") }), `Sveglia ${shortDateFmt.format(when)} · ${timeFmt.format(when)}`);
}

function setStatus(ok, text) {
  $("status-chip").classList.toggle("error", !ok);
  $("status-text").textContent = text;
}

function renderAll(data) {
  applyTheme(data.weather);
  renderWeather(data.weather, data.weatherError);
  renderAgenda(data.events, data.calendarError);
  renderTasks(data.tasks, data.date);
  renderAlarm(data.alarm);
  renderSummary(data);
}

/* ------------------------------------------------------------------ actions */

async function refresh() {
  clearTimeout(retryTimer);
  try {
    const data = await api("/api/today");
    lastData = data;
    failures = 0;
    renderAll(data);
    setStatus(true, `Aggiornato ${timeFmt.format(new Date())}`);
  } catch (err) {
    failures += 1;
    const wait = Math.min(60000, 3000 * 2 ** (failures - 1));
    setStatus(false, `Offline · riprovo tra ${Math.round(wait / 1000)} s`);
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
  const SUGGESTIONS = ["Cosa ho in programma oggi?", "Mi serve l'ombrello?", "Che task ho in scadenza?", "Com'è il meteo domani?"];
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
      bubble("bot", "Non sono ancora configurata. Sul PC hub aggiungi GEMINI_API_KEY nel file hub-server\\.env (chiave gratuita da aistudio.google.com) e riavvia il server.", { error: true });
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
    input.placeholder = `Chiedi a ${name}...`;
    if (!log.children.length || enabled === false) greet();
  }

  function open() {
    panel.inert = false;
    panel.classList.add("open");
    panel.setAttribute("aria-hidden", "false");
    $("assistant-orb").classList.add("hidden");
    setTimeout(() => input.focus(), 150);
  }

  function close() {
    panel.inert = true;
    panel.classList.remove("open");
    panel.setAttribute("aria-hidden", "true");
    $("assistant-orb").classList.remove("hidden");
  }

  async function send(text) {
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
      const result = await api("/api/assistant/chat", { method: "POST", body: JSON.stringify({ conversationId, message }) });
      thinking.remove();
      bubble("bot", result.reply, { actions: result.actions });
      if (result.actions.length) refresh();
    } catch (err) {
      thinking.remove();
      bubble("bot", err.message || "Qualcosa è andato storto.", { error: true });
    } finally {
      busy = false;
      panel.classList.remove("busy");
      input.focus();
    }
  }

  async function reset() {
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
  panel.querySelector(".send").innerHTML = uiIcon("send", 2);
  $("assistant-form").addEventListener("submit", (event) => { event.preventDefault(); send(input.value); });
  input.addEventListener("input", autosize);
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); send(input.value); }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && panel.classList.contains("open")) close();
    const typing = ["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName);
    if (event.key === "/" && !typing && !$("app").classList.contains("hidden")) { event.preventDefault(); open(); }
  });

  return { init, open, close, send };
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
    renderAgenda(lastData.events, lastData.calendarError);
    renderSummary(lastData);
    applyTheme(lastData.weather);
  }
  if (resumed && !$("app").classList.contains("hidden")) refresh();
}

renderClock();
setInterval(tick, 1000);
setInterval(() => { if (!$("app").classList.contains("hidden")) refresh(); }, REFRESH_MS);
document.addEventListener("visibilitychange", () => { if (!document.hidden) refresh(); });
window.addEventListener("online", refresh);
window.addEventListener("focus", refresh);

let resizeTimer = null;
window.addEventListener("resize", () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => { if (lastData) renderForecast(lastData.weather); }, 200);
});

if (readToken()) showApp(); else showLogin();
