"use strict";

const TOKEN_KEY = "dailyHubToken";
const REFRESH_MS = 60 * 1000;

const ICONS = {
  "clear": '<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="12" fill="#ffd166"/><g stroke="#ffd166" stroke-width="4" stroke-linecap="round"><path d="M32 6v8M32 50v8M6 32h8M50 32h8M13.6 13.6l5.7 5.7M44.7 44.7l5.7 5.7M13.6 50.4l5.7-5.7M44.7 19.3l5.7-5.7"/></g></svg>',
  "partly-cloudy": '<svg viewBox="0 0 64 64"><circle cx="24" cy="24" r="10" fill="#ffd166"/><path d="M20 50h26a10 10 0 0 0 0-20 14 14 0 0 0-26 4 8 8 0 0 0 0 16z" fill="#c9d3e3"/></svg>',
  "cloudy": '<svg viewBox="0 0 64 64"><path d="M16 48h32a12 12 0 0 0 0-24 16 16 0 0 0-30 4 10 10 0 0 0-2 20z" fill="#c9d3e3"/></svg>',
  "fog": '<svg viewBox="0 0 64 64"><g stroke="#c9d3e3" stroke-width="5" stroke-linecap="round"><path d="M10 24h44M14 34h36M10 44h44"/></g></svg>',
  "drizzle": '<svg viewBox="0 0 64 64"><path d="M16 38h32a11 11 0 0 0 0-22 15 15 0 0 0-28 4 9 9 0 0 0-4 18z" fill="#c9d3e3"/><g stroke="#5fb3ff" stroke-width="3" stroke-linecap="round"><path d="M22 46v4M32 46v4M42 46v4"/></g></svg>',
  "rain": '<svg viewBox="0 0 64 64"><path d="M16 36h32a11 11 0 0 0 0-22 15 15 0 0 0-28 4 9 9 0 0 0-4 18z" fill="#c9d3e3"/><g stroke="#5fb3ff" stroke-width="4" stroke-linecap="round"><path d="M22 42l-3 10M32 42l-3 10M42 42l-3 10"/></g></svg>',
  "snow": '<svg viewBox="0 0 64 64"><path d="M16 36h32a11 11 0 0 0 0-22 15 15 0 0 0-28 4 9 9 0 0 0-4 18z" fill="#c9d3e3"/><g fill="#ffffff"><circle cx="22" cy="46" r="3"/><circle cx="32" cy="52" r="3"/><circle cx="42" cy="46" r="3"/></g></svg>',
  "storm": '<svg viewBox="0 0 64 64"><path d="M16 34h32a11 11 0 0 0 0-22 15 15 0 0 0-28 4 9 9 0 0 0-4 18z" fill="#9aa6bb"/><path d="M34 34l-8 14h8l-4 12 12-18h-8l4-8z" fill="#ffd166"/></svg>',
  "unknown": '<svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="20" fill="none" stroke="#8a95ab" stroke-width="4"/></svg>',
};

const timeFmt = new Intl.DateTimeFormat("it-IT", { hour: "2-digit", minute: "2-digit" });
const dateFmt = new Intl.DateTimeFormat("it-IT", { weekday: "long", day: "numeric", month: "long" });
const shortDateFmt = new Intl.DateTimeFormat("it-IT", { weekday: "short", day: "numeric", month: "short" });

const $ = (id) => document.getElementById(id);

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
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key === "class") node.className = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value);
  }
  for (const child of children) {
    if (child !== null && child !== undefined) node.append(child);
  }
  return node;
}

function iconNode(key) {
  const wrapper = document.createElement("span");
  wrapper.innerHTML = ICONS[key] || ICONS.unknown;
  return wrapper.firstChild;
}

function round(value) {
  return value === null || value === undefined ? "--" : Math.round(value);
}

function renderClock() {
  const now = new Date();
  $("clock").textContent = timeFmt.format(now);
  $("date").textContent = dateFmt.format(now);
}

function renderWeather(weather, error) {
  const body = $("weather-body");
  const hourly = $("hourly");
  body.replaceChildren();
  hourly.replaceChildren();
  if (!weather) {
    body.append(el("p", { class: "error" }, error || "Meteo non disponibile"));
    return;
  }
  $("weather-city").textContent = weather.city;
  const { current, today } = weather;
  body.append(
    iconNode(current.icon),
    el("div", {},
      el("div", { class: "temp" }, `${round(current.temperature)}°`),
      el("div", { class: "weather-meta" },
        el("div", {}, current.description),
        el("div", {}, `Min ${round(today.min)}° / Max ${round(today.max)}°`),
        el("div", {}, `Pioggia ${round(today.precipitationProbability)}% · Percepita ${round(current.apparentTemperature)}°`),
      ),
    ),
  );
  for (const hour of weather.hourly || []) {
    hourly.append(el("div", { class: "hour" },
      hour.time.slice(11, 16),
      iconNode(hour.icon),
      el("strong", {}, `${round(hour.temperature)}°`),
      `${round(hour.precipitationProbability)}%`,
    ));
  }
}

function renderEvents(events, error) {
  const list = $("events");
  list.replaceChildren();
  if (error) list.append(el("li", { class: "error" }, error));
  if (!events.length && !error) list.append(el("li", { class: "empty" }, "Nessun evento oggi"));
  for (const event of events) {
    const time = event.allDay
      ? "Tutto il giorno"
      : `${timeFmt.format(new Date(event.start))}–${timeFmt.format(new Date(event.end))}`;
    list.append(el("li", {},
      el("span", { class: "time" }, time),
      el("span", { class: "grow" }, event.title, event.location ? el("div", { class: "sub" }, event.location) : null),
    ));
  }
}

function dueLabel(task, todayIso) {
  if (!task.due) return null;
  let cls = "due";
  let text = shortDateFmt.format(new Date(`${task.due}T12:00:00`));
  if (task.overdue) { cls += " overdue"; text = `Scaduto · ${text}`; }
  else if (task.due === todayIso) { cls += " today"; text = "Oggi"; }
  return el("span", { class: cls }, text);
}

function renderTasks(tasks, todayIso) {
  const list = $("tasks");
  list.replaceChildren();
  if (!tasks.length) list.append(el("li", { class: "empty" }, "Nessun task aperto"));
  for (const task of tasks) {
    list.append(el("li", {},
      el("input", { type: "checkbox", title: "Completa", onchange: () => updateTask(task.id, { done: true }) }),
      el("span", { class: "grow" }, task.title),
      dueLabel(task, todayIso),
      el("button", { class: "delete", title: "Elimina", onclick: () => deleteTask(task.id) }, "\u00d7"),
    ));
  }
}

function renderAlarm(alarm) {
  const node = $("alarm");
  if (!alarm || !alarm.nextAlarm) { node.textContent = "Nessuna sveglia sincronizzata"; return; }
  const when = new Date(alarm.nextAlarm);
  node.textContent = `Prossima sveglia: ${shortDateFmt.format(when)} ${timeFmt.format(when)}`;
}

async function refresh() {
  try {
    const data = await api("/api/today");
    renderWeather(data.weather, data.weatherError);
    renderEvents(data.events, data.calendarError);
    renderTasks(data.tasks, data.date);
    renderAlarm(data.alarm);
    $("status").textContent = `Aggiornato alle ${timeFmt.format(new Date())}`;
    $("status").classList.remove("error");
  } catch (err) {
    $("status").textContent = `Errore di aggiornamento: ${err.message}`;
    $("status").classList.add("error");
  }
}

async function updateTask(id, patch) {
  await api(`/api/tasks/${id}`, { method: "PATCH", body: JSON.stringify(patch) });
  refresh();
}

async function deleteTask(id) {
  await api(`/api/tasks/${id}`, { method: "DELETE" });
  refresh();
}

function showLogin() {
  $("app").classList.add("hidden");
  $("login").classList.remove("hidden");
}

function showApp() {
  $("login").classList.add("hidden");
  $("app").classList.remove("hidden");
  refresh();
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

renderClock();
setInterval(renderClock, 1000);
setInterval(() => { if (!$("app").classList.contains("hidden")) refresh(); }, REFRESH_MS);
document.addEventListener("visibilitychange", () => { if (!document.hidden) refresh(); });

if (readToken()) showApp(); else showLogin();
