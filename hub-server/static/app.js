:root {
  --bg: #0f1420;
  --bg-2: #1b2440;
  --panel: rgba(17, 24, 37, 0.62);
  --panel-strong: rgba(14, 18, 28, 0.8);
  --text: #edf3ff;
  --muted: #bfd0ea;
  --accent: #7cc7ff;
  --accent-2: #8ee4ff;
  --warn: #ff8a65;
  --ok: #7bd389;
  --shadow: rgba(5, 10, 20, 0.42);
  --glass: rgba(255, 255, 255, 0.06);
  --border: rgba(255, 255, 255, 0.12);
  font-family: "Segoe UI", system-ui, sans-serif;
}

* { box-sizing: border-box; }

html, body {
  margin: 0;
  min-height: 100vh;
  width: 100%;
  overflow: hidden;
}

body {
  position: relative;
  background: linear-gradient(135deg, var(--bg), var(--bg-2));
  color: var(--text);
  transition: background 0.7s ease, filter 0.7s ease;
}

body::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 20%, rgba(255,255,255,0.15), transparent 26%),
    radial-gradient(circle at 75% 15%, rgba(255,255,255,0.08), transparent 24%);
  opacity: 0.85;
  pointer-events: none;
}

body[data-weather="clear"] { background: linear-gradient(135deg, #0f1f34 0%, #6baed6 30%, #f1d18f 100%); }
body[data-weather="partly-cloudy"] { background: linear-gradient(135deg, #13263d 0%, #5c7da8 30%, #b9d0e6 100%); }
body[data-weather="cloudy"] { background: linear-gradient(135deg, #1a2635 0%, #586c7e 35%, #a3b0bc 100%); }
body[data-weather="rain"] { background: linear-gradient(135deg, #102033 0%, #2d4d6a 30%, #5a778d 100%); }
body[data-weather="drizzle"] { background: linear-gradient(135deg, #132437 0%, #42657d 30%, #7897a8 100%); }
body[data-weather="snow"] { background: linear-gradient(135deg, #101b2b 0%, #5c7a9f 30%, #dfeaf6 100%); }
body[data-weather="storm"] { background: linear-gradient(135deg, #0f1725 0%, #1a2d45 30%, #4d5d78 100%); }
body[data-weather="fog"] { background: linear-gradient(135deg, #394851 0%, #7f8d8f 40%, #bdbfbd 100%); }

.sky-glow {
  position: absolute;
  inset: auto;
  pointer-events: none;
  filter: blur(18px);
  opacity: 0.65;
  animation: drift 18s ease-in-out infinite alternate;
}

.sky-glow-1 {
  width: 36vw;
  height: 36vw;
  top: 6vh;
  right: 8vw;
  border-radius: 50%;
  background: rgba(255,255,255,0.18);
}

.sky-glow-2 {
  width: 24vw;
  height: 24vw;
  bottom: 10vh;
  left: 8vw;
  border-radius: 50%;
  background: rgba(132, 184, 255, 0.14);
}

.sky-glow-3 {
  width: 18vw;
  height: 18vw;
  left: 44vw;
  top: 16vh;
  border-radius: 50%;
  background: rgba(255,255,255,0.08);
}

@keyframes drift {
  0% { transform: translate3d(0, 0, 0); }
  100% { transform: translate3d(30px, 14px, 0); }
}

.hidden { display: none !important; }

main {
  position: relative;
  z-index: 1;
  padding: 2.5vh 3vw;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 2.2vh;
}

.top-copy {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.clock {
  font-size: clamp(3rem, 8.4vh, 7rem);
  font-weight: 300;
  line-height: 1;
  letter-spacing: -0.05em;
  text-shadow: 0 8px 18px rgba(0,0,0,0.18);
}

.date {
  font-size: clamp(1rem, 2.3vh, 1.8rem);
  color: rgba(255,255,255,0.82);
  text-transform: capitalize;
}

.alarm {
  font-size: clamp(1rem, 2.2vh, 1.55rem);
  color: rgba(255,255,255,0.78);
  padding: 0.7rem 1rem;
  border-radius: 999px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.09);
}

.grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1.2fr 1.2fr;
  gap: 1.5vw;
}

.card {
  background: var(--panel);
  backdrop-filter: blur(10px);
  border: 1px solid var(--border);
  border-radius: 26px;
  padding: 2.2vh 1.5vw;
  overflow: hidden;
  box-shadow: 0 18px 42px var(--shadow);
}

.card h2 {
  margin: 0 0 1.7vh;
  font-size: clamp(1rem, 2.3vh, 1.5rem);
  font-weight: 600;
  color: rgba(255,255,255,0.88);
}

.weather-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.weather-badge {
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.9);
  border-radius: 999px;
  padding: 0.35rem 0.75rem;
  font-size: 0.76rem;
}

.weather-body {
  display: flex;
  align-items: center;
  gap: 1.2vw;
  min-height: 10vh;
}

.weather-body svg {
  width: clamp(64px, 11vh, 140px);
  height: clamp(64px, 11vh, 140px);
  filter: drop-shadow(0 16px 26px rgba(0,0,0,0.18));
}

.temp {
  font-size: clamp(2.7rem, 7.3vh, 5.2rem);
  font-weight: 300;
  line-height: 1.1;
}

.weather-meta {
  color: rgba(255,255,255,0.8);
  font-size: clamp(0.9rem, 2vw, 1.3rem);
  line-height: 1.45;
}

.hourly {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 0.8rem 0.5rem;
  margin-top: 2vh;
}

.hour {
  text-align: center;
  color: rgba(255,255,255,0.7);
  font-size: clamp(0.72rem, 1.7vh, 0.95rem);
}

.hour svg {
  width: 32px;
  height: 32px;
  display: block;
  margin: 0.35rem auto;
}

.hour strong {
  color: var(--text);
  display: block;
  font-size: 1.02em;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.list li {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 1.05vh 0;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  font-size: clamp(1rem, 2.4vh, 1.3rem);
}

.list li:last-child { border-bottom: none; }

.list .time {
  color: var(--accent-2);
  min-width: 5.6rem;
  font-variant-numeric: tabular-nums;
}

.list .grow {
  flex: 1;
  min-width: 0;
}

.list .empty { color: rgba(255,255,255,0.7); }

.list .due {
  color: rgba(255,255,255,0.8);
  font-size: 0.8em;
  white-space: nowrap;
}

.list .due.overdue { color: var(--warn); }
.list .due.today { color: var(--ok); }

.list input[type=checkbox] {
  width: 1.3em;
  height: 1.3em;
  accent-color: var(--accent);
}

.list button.delete {
  background: none;
  border: none;
  color: rgba(255,255,255,0.75);
  font-size: 1.2em;
  cursor: pointer;
}

.task-form {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5vh;
}

.task-form input, .task-form button, .login input, .login button {
  font: inherit;
  padding: 0.65rem 0.8rem;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.12);
  background: rgba(7, 11, 18, 0.3);
  color: var(--text);
}

.task-form input#task-title { flex: 1; min-width: 0; }
.task-form button, .login button {
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  color: #06131d;
  border: none;
  cursor: pointer;
  font-weight: 700;
}

.task-form input::-webkit-calendar-picker-indicator { filter: invert(1); }

.status {
  margin-top: 1.5vh;
  color: rgba(255,255,255,0.7);
  font-size: 0.85rem;
  text-align: right;
}

.error { color: var(--warn); }

.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
}

.login form {
  background: var(--panel);
  backdrop-filter: blur(8px);
  border: 1px solid var(--border);
  padding: 2rem;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: min(420px, 90vw);
}

.login h1 {
  margin: 0;
  color: rgba(255,255,255,0.95);
}

.login p {
  margin: 0;
  color: rgba(255,255,255,0.7);
}

@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
  main { padding: 1.5rem 1rem; }
}
