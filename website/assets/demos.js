/*
 * Stella website: interactive demos (Right Shift ClickGUI, Region Map, Spawner radar, sky switcher,
 * HUD editor, knives, cost chart) plus small touches (marquee, scroll progress, gallery navigation).
 * Same rules as site.js: DOM building only, no innerHTML, no network calls.
 */
"use strict";

/* ------------------------------------------------------------------ Right Shift: full-screen ClickGUI */
let overlayGui = null;
function setupOverlay() {
  const ov = $("#gui-overlay");
  if (!ov) return;
  let lastFocus = null;
  const open = () => {
    if (!ov.hidden) return;
    lastFocus = document.activeElement;
    ov.hidden = false;
    document.body.style.setProperty("overflow", "hidden");
    if (!overlayGui) overlayGui = clickGui($("#gui-overlay-stage"), { search: $("#overlay-search") });
    else overlayGui.render();
    setTheme(themeIndex); // colour the overlay's GUI too
    $("#overlay-search").focus({ preventScroll: true });
  };
  const close = () => {
    if (ov.hidden) return;
    ov.hidden = true;
    document.body.style.removeProperty("overflow");
    GUI_INSTANCES.forEach(g => g.render());
    if (lastFocus && lastFocus.focus) lastFocus.focus({ preventScroll: true });
  };
  window.stellaGui = { open, close, toggle: () => (ov.hidden ? open() : close()) };
  $("#open-gui").addEventListener("click", open);
  $("#gui-overlay-close").addEventListener("click", close);
  ov.addEventListener("click", e => { if (e.target === ov) close(); });
  document.addEventListener("keydown", e => {
    if (e.code === "ShiftRight" && !e.repeat) {
      const t = e.target;
      const typing = t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA") && t.id !== "overlay-search";
      if (!typing) { e.preventDefault(); window.stellaGui.toggle(); }
      return;
    }
    if (e.key === "Escape") {
      if (GUI_INSTANCES.some(g => g.closeSettings())) return;
      close();
    }
  });
}

/* ------------------------------------------------------------------ everything else */
function setupDemos() {
  if (typeof setupDashboard === "function") setupDashboard();
  marquee();
  progressBar();
  heroCards();
  regionMap();
  radar();
  skySwitcher();
  hudPlayground();
  knives();
  costChart();
  galleryNav();
}

function marquee() {
  const a = $("#marquee-a"), b = $("#marquee-b");
  if (!a || !b) return;
  const mods = sharedMods();
  const half = Math.ceil(mods.length / 2);
  const fill = (track, list) => {
    const chips = list.map(m => el("span", { class: m.on ? "on" : null }, m.n));
    track.append(...chips, ...chips.map(c => c.cloneNode(true))); // twice, so the loop is seamless
  };
  fill(a, mods.slice(0, half));
  fill(b, mods.slice(half));
}

function progressBar() {
  const bar = $("#progress");
  if (!bar) return;
  let queued = false;
  const update = () => {
    queued = false;
    const max = document.documentElement.scrollHeight - window.innerHeight;
    bar.style.setProperty("transform", "scaleX(" + (max > 0 ? Math.min(1, window.scrollY / max) : 0) + ")");
  };
  window.addEventListener("scroll", () => { if (!queued) { queued = true; requestAnimationFrame(update); } }, { passive: true });
  update();
}

/** Hero: the floating HUD cards drift with the pointer, and the FPS counter ticks like the real one. */
function heroCards() {
  const fps = $("#fc-fps"), hudFps = $("#hud-fps");
  setInterval(() => {
    const v = String(228 + Math.round(Math.random() * 26));
    if (fps) fps.textContent = v;
    if (hudFps) hudFps.textContent = v;
  }, 1000);
  if (reduceMotion || !window.matchMedia("(hover: hover)").matches) return;
  const screen = $("#screen"), cards = $$(".float-card");
  screen.addEventListener("pointermove", e => {
    const r = screen.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
    cards.forEach((c, i) => c.style.setProperty("transform", `translate(${x * (14 + i * 8)}px, ${y * (10 + i * 6)}px)`));
  });
  screen.addEventListener("pointerleave", () => cards.forEach(c => c.style.removeProperty("transform")));
}

/* ------------------------------------------------------------------ Region Map (real DonutSMP layout from RegionConfig) */
const REGION = {
  origin: -225000, size: 50000,
  ids: [
    [82, 100, 101, 102, 103, 104, 105, 106, 91], [83, 44, 75, 42, 41, 40, 39, 38, 92], [84, 45, 14, 13, 12, 11, 10, 37, 93],
    [85, 46, 74, 3, 2, 1, 25, 36, 94], [86, 47, 72, 71, 5, 4, 24, 35, 95], [87, 51, 17, 9, 8, 7, 23, 34, 96],
    [88, 54, 18, 61, 62, 21, 22, 33, 97], [89, 26, 27, 28, 29, 30, 59, 32, 98], [90, 107, 108, 109, 110, 111, 112, 113, 99]],
  cats: [
    [-1, -1, 0, -1, 0, 0, -1, -1, -1], [-1, -1, -1, -1, -1, -1, -1, -1, -1], [-1, -1, -1, -1, -1, -1, -1, -1, -1],
    [-1, -1, -1, -1, 2, -1, -1, -1, -1], [2, -1, -1, -1, 1, 1, -1, -1, -1], [-1, -1, -1, -1, 1, 2, -1, 2, -1],
    [2, -1, -1, -1, -1, -1, -1, -1, 0], [0, -1, -1, 2, 2, 1, 2, 2, 2], [0, -1, -1, 2, -1, -1, 2, 2, 2]],
  names: ["Glitched (no RTP spots)", "Good for media / baltop", "Good for media / other bases"],
};

function regionMap() {
  const grid = $("#region-grid"), out = $("#region-out"), form = $("#coord-form");
  if (!grid) return;
  const cells = [];
  const fmtK = v => (v / 1000).toLocaleString("en") + "k";
  const describe = (r, c) => {
    const x1 = REGION.origin + c * REGION.size, z1 = REGION.origin + r * REGION.size, cat = REGION.cats[r][c];
    return "Region " + REGION.ids[r][c] + " · X " + fmtK(x1) + " to " + fmtK(x1 + REGION.size) + ", Z " + fmtK(z1) + " to " + fmtK(z1 + REGION.size) +
      (cat >= 0 ? " · " + REGION.names[cat] : "");
  };
  const select = (r, c, yaw) => {
    cells.forEach(x => x.classList.remove("here"));
    const cell = cells[r * 9 + c];
    cell.classList.add("here");
    cell.style.setProperty("--yaw", (yaw || 0) + "deg");
    out.textContent = describe(r, c);
  };
  for (let r = 0; r < 9; r++) {
    for (let c = 0; c < 9; c++) {
      const cat = REGION.cats[r][c];
      const cell = el("button", { class: "cell" + (cat >= 0 ? " c" + cat : ""), type: "button", role: "gridcell", "aria-label": describe(r, c),
        onclick: () => {
          select(r, c, 0);
          $("#coord-x").value = String(REGION.origin + c * REGION.size + REGION.size / 2);
          $("#coord-z").value = String(REGION.origin + r * REGION.size + REGION.size / 2);
        } }, String(REGION.ids[r][c]));
      cell.style.setProperty("animation-delay", (r + c) * 0.03 + "s");
      cells.push(cell);
      grid.append(cell);
    }
  }
  const lookup = () => {
    const x = Number($("#coord-x").value.replace(/[\s,_]/g, "")), z = Number($("#coord-z").value.replace(/[\s,_]/g, ""));
    if (!Number.isFinite(x) || !Number.isFinite(z)) { out.textContent = "Enter whole numbers for X and Z, for example -35000 and 28000."; return; }
    const c = Math.floor((x - REGION.origin) / REGION.size), r = Math.floor((z - REGION.origin) / REGION.size);
    if (c < 0 || c > 8 || r < 0 || r > 8) { out.textContent = "That's outside the map: DonutSMP regions run from -225,000 to 225,000 on both axes."; return; }
    select(r, c, 0);
  };
  form.addEventListener("submit", e => { e.preventDefault(); lookup(); });
  lookup();
}

/* ------------------------------------------------------------------ Spawner Finder radar */
const MOB_COLORS = { Zombie: "#22c55e", Skeleton: "#e5e7eb", Spider: "#dc2626", "Cave Spider": "#0ea5e9", Blaze: "#f59e0b" };
function radar() {
  const c = $("#radar"), list = $("#radar-list");
  if (!c) return;
  const ctx = c.getContext("2d");
  const W = c.width, H = c.height, R = W / 2 - 8;
  const spawners = [
    { t: "Cave Spider", a: 0.6, d: 0.22 }, { t: "Skeleton", a: 2.4, d: 0.55 }, { t: "Zombie", a: 4.1, d: 0.72 },
    { t: "Cave Spider", a: 5.3, d: 0.38 }, { t: "Blaze", a: 3.2, d: 0.9 }, { t: "Spider", a: 1.4, d: 0.83 }];
  const range = 160;
  list.replaceChildren(...[...spawners].sort((x, y) => x.d - y.d).slice(0, 4).map(s => {
    const i = el("i");
    i.style.setProperty("--c", MOB_COLORS[s.t]);
    return el("li", null, i, el("span", null, s.t + " spawner"), el("b", { class: "tab" }, Math.round(s.d * range) + "m"));
  }));
  let raf = 0, visible = false;
  const draw = now => {
    const t = now / 1000, acc = THEMES[themeIndex].c;
    ctx.clearRect(0, 0, W, H);
    ctx.save(); ctx.translate(W / 2, H / 2);
    ctx.fillStyle = "rgba(20,17,28,0.9)"; ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "rgba(233,213,255,0.12)"; ctx.lineWidth = 1;
    for (const k of [0.33, 0.66, 1]) { ctx.beginPath(); ctx.arc(0, 0, R * k, 0, Math.PI * 2); ctx.stroke(); }
    ctx.beginPath(); ctx.moveTo(-R, 0); ctx.lineTo(R, 0); ctx.moveTo(0, -R); ctx.lineTo(0, R); ctx.stroke();
    const sweep = reduceMotion ? 1 : t * 1.4;
    const g = ctx.createConicGradient ? ctx.createConicGradient(sweep - 0.9, 0, 0) : null;
    if (g) {
      g.addColorStop(0, "rgba(0,0,0,0)"); g.addColorStop(0.14, hexA(acc, 0.35)); g.addColorStop(0.15, "rgba(0,0,0,0)");
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.fill();
    }
    ctx.strokeStyle = acc; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.stroke();
    for (const s of spawners) {
      const x = Math.cos(s.a) * s.d * R, y = Math.sin(s.a) * s.d * R;
      const since = ((sweep - s.a) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
      const glow = reduceMotion ? 1 : Math.max(0.35, 1 - since / 4);
      ctx.fillStyle = hexA(MOB_COLORS[s.t], glow); ctx.shadowColor = MOB_COLORS[s.t]; ctx.shadowBlur = 10 * glow;
      ctx.fillRect(x - 4, y - 4, 8, 8);
      ctx.shadowBlur = 0;
    }
    // you: arrow in the middle
    ctx.rotate(reduceMotion ? 0 : Math.sin(t * 0.6) * 0.5);
    ctx.fillStyle = "#fff"; ctx.beginPath(); ctx.moveTo(0, -9); ctx.lineTo(6, 7); ctx.lineTo(0, 3); ctx.lineTo(-6, 7); ctx.closePath(); ctx.fill();
    ctx.restore();
    if (visible && !reduceMotion) raf = requestAnimationFrame(draw);
  };
  new IntersectionObserver(([e]) => { visible = e.isIntersecting; cancelAnimationFrame(raf); if (visible) raf = requestAnimationFrame(draw); }).observe(c);
  draw(0);
}

function hexA(hex, a) {
  const v = parseInt(hex.slice(1), 16);
  return "rgba(" + ((v >> 16) & 255) + "," + ((v >> 8) & 255) + "," + (v & 255) + "," + a + ")";
}

/* ------------------------------------------------------------------ sky switcher (drives the site theme) */
function skySwitcher() {
  const chips = $("#theme-chips"), img = $("#sky-img");
  if (!chips) return;
  THEMES.forEach((t, i) => {
    const i2 = el("i");
    i2.style.setProperty("--c", t.c);
    const b = el("button", { class: "chip", type: "button", "aria-pressed": "false", title: t.skyName + " sky", onclick: () => setTheme(i) }, i2, t.n);
    b.style.setProperty("--c", t.c);
    chips.append(b);
  });
  // preload the skies after the page has loaded, so switching is instant
  window.addEventListener("load", () => THEMES.forEach(t => { const im = new Image(); im.src = "assets/shots/sky-" + t.sky + ".jpg"; }), { once: true });
  themeListeners.push(t => {
    $$(".chip", chips).forEach((b, i) => b.setAttribute("aria-pressed", String(i === themeIndex)));
    const src = "assets/shots/sky-" + t.sky + ".jpg";
    if (img.getAttribute("src") === src) return;
    img.classList.add("swap");
    setTimeout(() => { img.src = src; img.alt = t.skyName + " sky design in game"; img.classList.remove("swap"); }, reduceMotion ? 0 : 220);
  });
}

/* ------------------------------------------------------------------ HUD editor playground */
const HUD_DEFAULT = { fps: [4, 6], ping: [4, 20], xyz: [4, 34], radar: [72, 4], list: [97, 6], brand: [96, 82] };
function hudPlayground() {
  const stage = $("#hud-stage");
  if (!stage) return;
  const els = $$(".hud-el", stage);
  const place = (n, px, py) => {
    // px/py in % of the stage; right-anchored elements keep their right edge
    n.style.setProperty("left", "calc(" + px + "% - " + (px > 60 ? n.offsetWidth * px / 100 : 0) + "px)");
    n.style.setProperty("top", "calc(" + py + "% - " + (py > 60 ? n.offsetHeight * py / 100 : 0) + "px)");
  };
  const reset = () => els.forEach(n => place(n, ...HUD_DEFAULT[n.dataset.hud]));
  requestAnimationFrame(reset);
  $("#hud-reset").addEventListener("click", reset);
  for (const n of els) {
    n.addEventListener("pointerdown", e => {
      e.preventDefault();
      const sr = stage.getBoundingClientRect(), nr = n.getBoundingClientRect();
      const dx = e.clientX - nr.left, dy = e.clientY - nr.top;
      n.setPointerCapture(e.pointerId);
      n.classList.add("dragging");
      const move = ev => {
        const x = Math.max(0, Math.min(sr.width - nr.width, ev.clientX - sr.left - dx));
        const y = Math.max(0, Math.min(sr.height - nr.height, ev.clientY - sr.top - dy));
        n.style.setProperty("left", x + "px"); n.style.setProperty("top", y + "px");
      };
      const up = () => { n.classList.remove("dragging"); n.removeEventListener("pointermove", move); };
      n.addEventListener("pointermove", move);
      n.addEventListener("pointerup", up, { once: true });
      n.addEventListener("pointercancel", up, { once: true });
    });
    n.addEventListener("keydown", e => { // arrow keys nudge, like the HUD editor
      const step = e.shiftKey ? 10 : 2, dirs = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
      const d = dirs[e.key];
      if (!d) return;
      e.preventDefault();
      const sr = stage.getBoundingClientRect(), nr = n.getBoundingClientRect();
      n.style.setProperty("left", Math.max(0, Math.min(sr.width - nr.width, nr.left - sr.left + d[0])) + "px");
      n.style.setProperty("top", Math.max(0, Math.min(sr.height - nr.height, nr.top - sr.top + d[1])) + "px");
    });
  }
  // the little radar HUD: two "players" circling
  const rc = $("#hud-radar");
  if (rc) {
    const x = rc.getContext("2d");
    const tick = now => {
      const t = now / 1000, acc = THEMES[themeIndex].c;
      x.clearRect(0, 0, 96, 96);
      x.fillStyle = "rgba(15,13,22,0.85)"; x.beginPath(); x.arc(48, 48, 44, 0, Math.PI * 2); x.fill();
      x.strokeStyle = acc; x.lineWidth = 2; x.stroke();
      x.strokeStyle = "rgba(255,255,255,0.12)"; x.lineWidth = 1; x.beginPath(); x.arc(48, 48, 26, 0, Math.PI * 2); x.stroke();
      [[0.9, 30, "#ef4444"], [-1.4, 20, "#38bdf8"]].forEach(([sp, rad, col], i) => {
        const a = t * sp * (reduceMotion ? 0 : 1) + i * 2;
        x.fillStyle = col; x.fillRect(48 + Math.cos(a) * rad - 3, 48 + Math.sin(a) * rad - 3, 6, 6);
      });
      x.fillStyle = "#fff"; x.beginPath(); x.moveTo(48, 41); x.lineTo(52, 52); x.lineTo(48, 49); x.lineTo(44, 52); x.closePath(); x.fill();
      if (!reduceMotion && visible) requestAnimationFrame(tick);
    };
    let visible = false;
    new IntersectionObserver(([e]) => { const was = visible; visible = e.isIntersecting; if (visible && !was) requestAnimationFrame(tick); }).observe(rc);
    tick(0);
  }
}

/* ------------------------------------------------------------------ knives */
const KNIVES = [
  { k: "karambit", n: "Karambit", f: "Nebula" }, { k: "butterfly", n: "Butterfly", f: "Damascus" }, { k: "bayonet", n: "Bayonet", f: "Ember" },
  { k: "stella-dagger", n: "Stella Dagger", f: "Sunset" }, { k: "kunai", n: "Kunai", f: "Galaxy" }, { k: "tanto", n: "Tanto", f: "Chrome" }];
function knives() {
  const tabs = $("#knife-tabs"), img = $("#knife-img");
  if (!tabs) return;
  let cur = 0, timer = 0;
  const show = i => {
    cur = (i + KNIVES.length) % KNIVES.length;
    const kn = KNIVES[cur];
    $$("button", tabs).forEach((b, j) => { b.setAttribute("aria-selected", String(j === cur)); b.tabIndex = j === cur ? 0 : -1; });
    img.classList.add("swap");
    setTimeout(() => { img.src = "assets/shots/knife-" + kn.k + ".jpg"; img.alt = kn.n + " knife skin, " + kn.f + " finish, during the inspect animation"; img.classList.remove("swap"); }, reduceMotion ? 0 : 180);
  };
  KNIVES.forEach((kn, i) => tabs.append(el("button", { type: "button", role: "tab", "aria-selected": String(i === 0), tabindex: i === 0 ? "0" : "-1", title: kn.f + " finish",
    onclick: () => { show(i); restart(); },
    onkeydown: e => { if (e.key === "ArrowRight" || e.key === "ArrowLeft") { e.preventDefault(); show(cur + (e.key === "ArrowRight" ? 1 : -1)); $$("button", tabs)[cur].focus(); restart(); } } }, kn.n)));
  window.addEventListener("load", () => KNIVES.forEach(kn => { const im = new Image(); im.src = "assets/shots/knife-" + kn.k + ".jpg"; }), { once: true });
  const restart = () => { clearInterval(timer); if (!reduceMotion) timer = setInterval(() => show(cur + 1), 3800); };
  restart();
}

/* ------------------------------------------------------------------ cost over a year (drawn to scale) */
function costChart() {
  const host = $("#cost-chart");
  if (!host) return;
  const ns = "http://www.w3.org/2000/svg";
  const W = 760, H = 230, L = 44, B = 28, T = 16, Rm = 12;
  const max = 100, x = m => L + (m - 0.5) * (W - L - Rm) / 12, y = v => H - B - v * (H - B - T) / max;
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", "0 0 " + W + " " + H);
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", "Total cost over 12 months: Monthly reaches 96 euro, Lifetime stays at 15 euro. Lifetime is cheaper from month two.");
  const add = (tag, attrs, text) => {
    const n = document.createElementNS(ns, tag);
    for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, String(v));
    if (text !== undefined) n.textContent = text;
    svg.append(n);
    return n;
  };
  for (const v of [0, 25, 50, 75, 100]) {
    add("line", { class: "grid", x1: L, x2: W - Rm, y1: y(v), y2: y(v) });
    add("text", { x: L - 8, y: y(v) + 4, "text-anchor": "end" }, "€" + v);
  }
  const bw = (W - L - Rm) / 12 * 0.56;
  for (let m = 1; m <= 12; m++) {
    const v = 8 * m;
    // drawn at full height; CSS grows them from the baseline only when the chart scrolls into view
    const r = add("rect", { class: "bar-m" + (v > 15 ? " over" : ""), x: x(m) - bw / 2, width: bw, y: y(v), height: y(0) - y(v), rx: 3 });
    r.style.setProperty("transition-delay", m * 0.05 + "s");
    add("text", { x: x(m), y: H - 8, "text-anchor": "middle" }, String(m));
    if (m === 2 || m === 12) add("text", { class: "val", x: x(m), y: y(v) - 7, "text-anchor": "middle" }, "€" + v);
  }
  add("line", { class: "line-l", x1: L, x2: W - Rm, y1: y(15), y2: y(15) });
  add("text", { class: "val", x: W - Rm, y: y(15) - 7, "text-anchor": "end" }, "Lifetime €15");
  host.append(svg);
  if (reduceMotion || host.getBoundingClientRect().top < window.innerHeight) return; // already on screen: leave it drawn
  svg.classList.add("pending");
  new IntersectionObserver((es, io) => {
    if (es.some(e => e.isIntersecting)) { svg.classList.remove("pending"); io.disconnect(); }
  }, { threshold: 0.3 }).observe(host);
}

/* ------------------------------------------------------------------ gallery: previous / next, arrow keys, swipe */
function galleryNav() {
  const box = $("#lightbox");
  if (!box) return;
  const shots = $$(".shot");
  const current = () => shots.findIndex(s => s.dataset.src === $("#lightbox-img").getAttribute("src"));
  const go = d => {
    if (box.hidden) return;
    const i = (current() + d + shots.length) % shots.length, s = shots[i];
    $("#lightbox-img").src = s.dataset.src;
    $("#lightbox-img").alt = $("img", s).alt;
    $("#lightbox-cap").textContent = $("span", s).textContent;
  };
  $("#lightbox-prev").addEventListener("click", e => { e.stopPropagation(); go(-1); });
  $("#lightbox-next").addEventListener("click", e => { e.stopPropagation(); go(1); });
  document.addEventListener("keydown", e => { if (e.key === "ArrowLeft") go(-1); else if (e.key === "ArrowRight") go(1); });
  let sx = null;
  box.addEventListener("touchstart", e => { sx = e.touches[0].clientX; }, { passive: true });
  box.addEventListener("touchend", e => { if (sx === null) return; const dx = e.changedTouches[0].clientX - sx; if (Math.abs(dx) > 50) go(dx < 0 ? 1 : -1); sx = null; });
}

/* ------------------------------------------------------------------ start (this is the last script) */
if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start); else start();
