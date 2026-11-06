/*
 * Stella Client website.
 * Security notes: no third-party code, no inline scripts, no innerHTML (the page runs with
 * Trusted Types enforced), checkout links must be https and on an allowed payment host.
 */
"use strict";

/* ------------------------------------------------------------------ settings to fill in */
const CONFIG = Object.freeze({
  // Paste your checkout links here (Lemon Squeezy, Stripe Payment Links, Paddle or Tebex).
  checkout: Object.freeze({
    monthly: "",   // e.g. "https://yourstore.lemonsqueezy.com/buy/xxxxxxxx"
    lifetime: "",  // e.g. "https://buy.stripe.com/xxxxxxxx"
  }),
  // Only these payment hosts are accepted for the buttons above.
  allowedCheckoutHosts: ["buy.stripe.com", "checkout.stripe.com", "lemonsqueezy.com", "paddle.com", "tebex.io"],
  // Shown in the Terms and Cookie Policy. Replace before going live.
  owner: "[your name or business name]",
  email: "[your support email]",
  country: "[your country]",
  updated: "6 November 2026",
});

/* ------------------------------------------------------------------ helpers */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/** Builds an element: el("div", {class: "x", onclick: fn}, child, "text", ...). Text is always set as text. */
function el(tag, attrs, ...children) {
  const node = document.createElement(tag);
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v === undefined || v === null || v === false) continue;
      if (k.startsWith("on") && typeof v === "function") node.addEventListener(k.slice(2), v);
      else if (k === "class") node.className = v;
      else if (k === "style") for (const [p, val] of Object.entries(v)) node.style.setProperty(p, val);
      else node.setAttribute(k, v === true ? "" : String(v));
    }
  }
  for (const c of children.flat(Infinity)) {
    if (c === null || c === undefined || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

function svgIcon(kind) {
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  const p = document.createElementNS(ns, "path");
  if (kind === "gear") {
    p.setAttribute("d", "M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8zm8.5 4.6v-2.4l-2.3-.5a6.9 6.9 0 0 0-.7-1.6l1.3-2-1.7-1.7-2 1.3a6.9 6.9 0 0 0-1.6-.7L13.2 3.5h-2.4l-.5 2.3a6.9 6.9 0 0 0-1.6.7l-2-1.3-1.7 1.7 1.3 2a6.9 6.9 0 0 0-.7 1.6l-2.3.5v2.4l2.3.5c.2.6.4 1.1.7 1.6l-1.3 2 1.7 1.7 2-1.3c.5.3 1 .5 1.6.7l.5 2.3h2.4l.5-2.3c.6-.2 1.1-.4 1.6-.7l2 1.3 1.7-1.7-1.3-2c.3-.5.5-1 .7-1.6z");
    p.setAttribute("fill", "currentColor");
  } else {
    p.setAttribute("d", "M12 1.8l2.9 6.6 7.2.7-5.4 4.8 1.6 7.1L12 17.3 5.7 21l1.6-7.1L1.9 9.1l7.2-.7z");
    p.setAttribute("fill", "currentColor");
  }
  svg.append(p);
  return svg;
}

/* ------------------------------------------------------------------ views (home / updates / terms / cookies) */
const VIEWS = ["updates", "dashboard", "privacy", "terms", "cookies"];

function route() {
  const hash = decodeURIComponent(location.hash.replace(/^#/, "")).slice(0, 40);
  // a hash names a view ("terms") or an element; an element inside a view (e.g. "t-3") opens that view
  const target = /^[\w-]+$/.test(hash) ? document.getElementById(hash) : null;
  const holder = target ? target.closest("[data-view]") : null;
  const view = VIEWS.includes(hash) ? hash : holder ? holder.dataset.view : "home";
  for (const v of $$("[data-view]")) v.hidden = v.dataset.view !== view;
  for (const a of $$(".nav-links a")) {
    if (a.getAttribute("href") === "#" + hash) a.setAttribute("aria-current", "page");
    else a.removeAttribute("aria-current");
  }
  $("#nav-links").classList.remove("open");
  $("#menu-toggle").setAttribute("aria-expanded", "false");
  if (VIEWS.includes(hash)) { window.scrollTo({ top: 0, behavior: "auto" }); return; }
  if (target && hash !== "home") requestAnimationFrame(() => target.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth" }));
  else if (hash === "home") window.scrollTo({ top: 0, behavior: reduceMotion ? "auto" : "smooth" });
}

/* ------------------------------------------------------------------ legal placeholders */
function fillPlaceholders() {
  for (const node of $$("[data-fill]")) {
    const key = node.dataset.fill;
    const value = CONFIG[key];
    if (typeof value !== "string") continue;
    node.textContent = value;
    node.classList.toggle("set", !value.startsWith("["));
  }
  const y = $("#year");
  if (y) y.textContent = String(new Date().getFullYear());
}

/* ------------------------------------------------------------------ checkout */
function safeCheckoutUrl(raw) {
  if (!raw) return null;
  let u;
  try { u = new URL(raw); } catch { return null; }
  if (u.protocol !== "https:" || u.username || u.password) return null;
  const host = u.hostname.toLowerCase();
  const ok = CONFIG.allowedCheckoutHosts.some(h => host === h || host.endsWith("." + h));
  return ok ? u.href : null;
}

function setupCheckout() {
  const notice = $("#checkout-notice");
  for (const btn of $$("[data-plan]")) {
    btn.addEventListener("click", () => {
      const plan = btn.dataset.plan;
      const url = safeCheckoutUrl(CONFIG.checkout[plan]);
      if (url) {
        const a = el("a", { href: url, rel: "noopener noreferrer", target: "_blank" });
        a.click();
        return;
      }
      notice.textContent = "Checkout isn't connected yet. The " + (plan === "monthly" ? "Monthly (€8 / month)" : "Lifetime (€15)") +
        " button will open the secure payment page once the store link is added.";
      notice.hidden = false;
    });
  }
}

/* ------------------------------------------------------------------ nav */
function setupNav() {
  const nav = $(".nav");
  const toggle = $("#menu-toggle");
  const links = $("#nav-links");
  toggle.addEventListener("click", () => {
    const open = links.classList.toggle("open");
    toggle.setAttribute("aria-expanded", String(open));
  });
  const onScroll = () => nav.classList.toggle("scrolled", window.scrollY > 12);
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();
}

/* ------------------------------------------------------------------ hero: title letters + night sky */
function heroTitle() {
  const h = $("#hero-title");
  if (h && !reduceMotion) h.classList.add("enter");
}

/** Animated night sky like Stella's menus: twinkling parallax stars, nebula glow, petals, shooting stars, tiny lightning. */
function sky() {
  const c = $("#sky");
  if (!c) return;
  const ctx = c.getContext("2d");
  let w = 0, h = 0, dpr = 1, stars = [], petals = [], bolts = [], shooting = null, mx = 0, my = 0, px = 0, py = 0;
  const rnd = (a, b) => a + Math.random() * (b - a);
  function resize() {
    dpr = Math.min(2, window.devicePixelRatio || 1);
    w = c.clientWidth; h = c.clientHeight;
    c.width = Math.round(w * dpr); c.height = Math.round(h * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const n = Math.round((w * h) / 2600);
    stars = Array.from({ length: n }, () => ({ x: Math.random(), y: Math.random(), r: rnd(0.3, 1.5), p: rnd(0, 6.28), d: Math.floor(rnd(0, 3)), b: rnd(0.3, 1) }));
    petals = Array.from({ length: Math.round(w / 70) }, () => newPetal(true));
  }
  function newPetal(anywhere) {
    return { x: anywhere ? rnd(0, w) : rnd(-40, w), y: anywhere ? rnd(0, h) : -12, vx: rnd(10, 26), vy: rnd(12, 26), rot: rnd(0, 6.28), vr: rnd(-1.2, 1.2), flip: rnd(0, 6.28), vf: rnd(1.5, 3.5), s: rnd(2.2, 4.4), sway: rnd(0.8, 2), ph: rnd(0, 10),
      col: ["#ffb7d5", "#f9a8d4", "#fbcfe8", "#d8b4fe"][Math.floor(rnd(0, 4))] };
  }
  function bolt(x1, y1, x2, y2) {
    const n = 8, pts = [];
    const dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy), nx = -dy / len, ny = dx / len;
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1), off = i === 0 || i === n - 1 ? 0 : rnd(-0.14, 0.14) * len;
      pts.push([x1 + dx * t + nx * off, y1 + dy * t + ny * off]);
    }
    bolts.push({ pts, age: 0, life: rnd(0.25, 0.45) });
  }
  let last = performance.now(), nextBolt = 2, nextShoot = 3;
  function frame(now) {
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    px += (mx - px) * Math.min(1, dt * 3); py += (my - py) * Math.min(1, dt * 3);
    const t = now / 1000;
    ctx.clearRect(0, 0, w, h);
    // nebula
    const g1 = ctx.createRadialGradient(w * 0.78 - px * 10, h * 0.3 - py * 6, 0, w * 0.78 - px * 10, h * 0.3 - py * 6, Math.max(w, h) * 0.55);
    const th = THEMES[themeIndex];
    g1.addColorStop(0, hexA(th.c, 0.28)); g1.addColorStop(1, hexA(th.c, 0));
    ctx.fillStyle = g1; ctx.fillRect(0, 0, w, h);
    const g2 = ctx.createRadialGradient(w * 0.15 - px * 6, h * 0.85, 0, w * 0.15, h * 0.85, Math.max(w, h) * 0.45);
    g2.addColorStop(0, hexA(th.c2, 0.16)); g2.addColorStop(1, hexA(th.c2, 0));
    ctx.fillStyle = g2; ctx.fillRect(0, 0, w, h);
    // stars
    for (const s of stars) {
      const tw = 0.5 + 0.5 * Math.sin(t * (1.1 + s.d * 0.5) + s.p);
      const x = ((s.x + t * 0.002 * (s.d + 1)) % 1) * w - px * (3 + s.d * 7), y = s.y * h - py * (2 + s.d * 5);
      ctx.globalAlpha = 0.25 + 0.75 * tw * s.b;
      ctx.fillStyle = "#f3e8ff";
      ctx.fillRect(x, y, s.r, s.r);
      if (s.d === 2 && s.b > 0.85 && tw > 0.75) {
        const g = (tw - 0.75) * 12;
        ctx.globalAlpha *= 0.5;
        ctx.fillRect(x - g + s.r / 2, y + s.r / 2 - 0.25, g * 2, 0.5);
        ctx.fillRect(x + s.r / 2 - 0.25, y - g + s.r / 2, 0.5, g * 2);
      }
    }
    ctx.globalAlpha = 1;
    // shooting star
    nextShoot -= dt;
    if (!shooting && nextShoot <= 0) { shooting = { x: rnd(w * 0.3, w), y: rnd(0, h * 0.4), vx: -rnd(500, 800), vy: rnd(120, 260), age: 0, life: rnd(0.6, 1) }; nextShoot = rnd(3, 7); }
    if (shooting) {
      const s = shooting; s.age += dt;
      const x = s.x + s.vx * s.age, y = s.y + s.vy * s.age, a = Math.sin(Math.PI * Math.min(1, s.age / s.life));
      const grad = ctx.createLinearGradient(x, y, x - s.vx * 0.12, y - s.vy * 0.12);
      grad.addColorStop(0, `rgba(255,255,255,${a})`); grad.addColorStop(1, "rgba(216,180,254,0)");
      ctx.strokeStyle = grad; ctx.lineWidth = 1.6; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x - s.vx * 0.12, y - s.vy * 0.12); ctx.stroke();
      if (s.age > s.life) shooting = null;
    }
    // petals (tumble in 3D: width follows cos(flip))
    for (const p of petals) {
      p.ph += dt; p.x += (p.vx + Math.sin(p.ph * p.sway) * 14) * dt; p.y += p.vy * dt; p.rot += p.vr * dt; p.flip += p.vf * dt;
      if (p.y > h + 12 || p.x > w + 20) Object.assign(p, newPetal(false));
      const flat = Math.abs(Math.cos(p.flip));
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot);
      ctx.globalAlpha = 0.55 + 0.4 * flat; ctx.fillStyle = p.col;
      ctx.beginPath(); ctx.ellipse(0, 0, p.s * 1.6, Math.max(0.4, p.s * (0.25 + 0.75 * flat)), 0, 0, Math.PI * 2); ctx.fill();
      ctx.restore();
    }
    ctx.globalAlpha = 1;
    // small lightning near the title
    nextBolt -= dt;
    if (nextBolt <= 0) {
      const ox = Math.min(w * 0.42, 520), oy = Math.min(h * 0.35, 230), a = rnd(0, 6.28), r = rnd(40, 90);
      bolt(ox + Math.cos(a) * 30, oy + Math.sin(a) * 20, ox + Math.cos(a) * r, oy + Math.sin(a) * r * 0.7);
      nextBolt = Math.random() < 0.3 ? 0.14 : rnd(2.2, 4.5);
    }
    for (let i = bolts.length - 1; i >= 0; i--) {
      const b = bolts[i]; b.age += dt;
      if (b.age > b.life) { bolts.splice(i, 1); continue; }
      const k = b.age / b.life, f = k < 0.15 ? 1 : k < 0.3 ? 0.35 : k < 0.45 ? 0.9 : (1 - k) / 0.55;
      const bc = THEMES[themeIndex].c;
      for (const [lw, col] of [[5, hexA(bc, 0.25 * f)], [2, hexA(mix(bc, "#ffffff", 0.55), 0.8 * f)], [0.8, `rgba(255,255,255,${f})`]]) {
        ctx.strokeStyle = col; ctx.lineWidth = lw; ctx.lineJoin = "round"; ctx.beginPath();
        b.pts.forEach(([x, y], j) => (j ? ctx.lineTo(x, y) : ctx.moveTo(x, y))); ctx.stroke();
      }
    }
    if (!reduceMotion && visible) requestAnimationFrame(frame); else running = false;
  }
  let visible = true, running = false;
  const start = () => { if (!running) { running = true; last = performance.now(); requestAnimationFrame(frame); } };
  new IntersectionObserver(([e]) => { visible = e.isIntersecting; if (visible) start(); }).observe(c);
  window.addEventListener("pointermove", e => { mx = e.clientX / window.innerWidth * 2 - 1; my = e.clientY / window.innerHeight * 2 - 1; }, { passive: true });
  new ResizeObserver(resize).observe(c);
  resize();
  start();
}

/* ------------------------------------------------------------------ tilt, reveal, counters */
function tilt() {
  if (reduceMotion || !window.matchMedia("(hover: hover)").matches) return;
  const screen = $("#screen"), inner = $("#screen-inner");
  if (screen && inner) {
    screen.addEventListener("pointermove", e => {
      const r = screen.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
      inner.style.setProperty("transform", `rotateY(${-9 + x * 14}deg) rotateX(${4 - y * 10}deg)`);
    });
    screen.addEventListener("pointerleave", () => inner.style.removeProperty("transform"));
  }
  for (const card of $$(".tilt")) {
    card.addEventListener("pointermove", e => {
      const r = card.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width, y = (e.clientY - r.top) / r.height;
      card.style.setProperty("--mx", (x * 100) + "%");
      card.style.setProperty("--my", (y * 100) + "%");
      card.style.setProperty("transform", `perspective(900px) rotateY(${(x - 0.5) * 6}deg) rotateX(${(0.5 - y) * 6}deg) translateY(-3px)`);
    });
    card.addEventListener("pointerleave", () => card.style.removeProperty("transform"));
  }
}

/** Elements below the first screen slide in when they scroll into view (anything visible at load stays put). */
function reveal() {
  if (reduceMotion || !("IntersectionObserver" in window)) return;
  const items = $$(".section-head, .feature, .shot, .plan, .faq details, .demo-frame");
  const io = new IntersectionObserver(entries => {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      e.target.classList.remove("pre");
      e.target.classList.add("in");
      io.unobserve(e.target);
    }
  }, { rootMargin: "0px 0px -8% 0px" });
  items.forEach((it, i) => {
    if (it.getBoundingClientRect().top < window.innerHeight) return;
    it.classList.add("pre");
    it.style.setProperty("animation-delay", ((i % 4) * 0.07) + "s");
    io.observe(it);
  });
}

function counters() {
  if (reduceMotion) return;
  const io = new IntersectionObserver(entries => {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      const node = e.target, end = Number(node.dataset.count), t0 = performance.now();
      const step = now => {
        const k = Math.min(1, (now - t0) / 1100), v = Math.round(end * (1 - Math.pow(1 - k, 3)));
        node.textContent = String(v);
        if (k < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
      io.unobserve(node);
    }
  });
  $$("[data-count]").forEach(n => io.observe(n));
}

/* ------------------------------------------------------------------ gallery lightbox */
function gallery() {
  const box = $("#lightbox"), img = $("#lightbox-img"), cap = $("#lightbox-cap");
  let lastFocus = null;
  const close = () => { box.hidden = true; if (lastFocus) lastFocus.focus(); };
  for (const shot of $$(".shot")) {
    shot.addEventListener("click", () => {
      lastFocus = shot;
      const src = shot.dataset.src;
      if (!/^assets\/shots\/[\w-]+\.jpg$/.test(src)) return;
      img.src = src;
      img.alt = $("img", shot).alt;
      cap.textContent = $("span", shot).textContent;
      box.hidden = false;
      $("#lightbox-close").focus();
    });
  }
  $("#lightbox-close").addEventListener("click", close);
  box.addEventListener("click", e => { if (e.target === box) close(); });
  document.addEventListener("keydown", e => { if (e.key === "Escape" && !box.hidden) close(); });
}

/* ------------------------------------------------------------------ the ClickGUI demo */
/** GUI themes from the client, each with its in-game sky (the whole site follows the chosen theme). */
const THEMES = [
  { n: "Stella", c: "#a855f7", c2: "#f472b6", sky: "stella", skyName: "Cosmic Stella" },
  { n: "Pink", c: "#f472b6", c2: "#fda4af", sky: "pink", skyName: "Sakura Dawn" },
  { n: "Ocean", c: "#0ea5e9", c2: "#2dd4bf", sky: "ocean", skyName: "Deep Ocean" },
  { n: "Emerald", c: "#34d399", c2: "#a3e635", sky: "emerald", skyName: "Emerald Aurora" },
  { n: "Sunset", c: "#fb923c", c2: "#facc15", sky: "sunset", skyName: "Golden Hour" },
  { n: "Crimson", c: "#ef4444", c2: "#fb7185", sky: "red", skyName: "Blood Moon" },
  { n: "Frost", c: "#93c5fd", c2: "#22d3ee", sky: "frost", skyName: "Arctic Aurora" },
  { n: "Galaxy", c: "#c084fc", c2: "#f472b6", sky: "galaxy", skyName: "Spiral Galaxy" },
  { n: "Liquid Glass", c: "#9ad8ff", c2: "#f0abfc", sky: "liquid-glass", skyName: "Prism Glass", glass: true },
];
let themeIndex = 0;
const themeListeners = [];

/** Recolours the whole site (and every ClickGUI) with a theme; colours fade across thanks to @property. */
function setTheme(i) {
  themeIndex = ((i % THEMES.length) + THEMES.length) % THEMES.length;
  const t = THEMES[themeIndex];
  const html = document.documentElement;
  html.style.setProperty("--accent", t.c);
  html.style.setProperty("--accent-soft", mix(t.c, "#ffffff", 0.55));
  html.style.setProperty("--pink", t.c2);
  html.style.setProperty("--on-accent", luminance(t.c) > 0.55 ? "#0b0a10" : "#ffffff");
  for (const g of $$(".gui")) {
    g.style.setProperty("--g-accent", t.c);
    g.style.setProperty("--g-soft", mix(t.c, "#ffffff", 0.55));
    g.classList.toggle("glass", !!t.glass);
  }
  $$(".pill").forEach(p => p.style.setProperty("--g-accent", t.c));
  const dot = $("#theme-cycle");
  if (dot) { dot.title = "Theme: " + t.n; dot.setAttribute("aria-label", "Change theme (" + t.n + ")"); }
  themeListeners.forEach(fn => fn(t));
  GUI_INSTANCES.forEach(g => g.render());
}

function luminance(hex) {
  const v = parseInt(hex.slice(1), 16);
  const ch = x => { x /= 255; return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4); };
  return 0.2126 * ch((v >> 16) & 255) + 0.7152 * ch((v >> 8) & 255) + 0.0722 * ch(v & 255);
}
const CATEGORY_ORDER = ["DonutSMP", "Combat", "Visuals", "HUD", "World", "Misc", "Cosmetics", "Client", "Settings"];
const CATEGORY_INFO = {
  DonutSMP: "Base hunting, spawners, ESP and server tools",
  Combat: "PvP helpers and alerts",
  Visuals: "Skies, rendering and screen effects",
  HUD: "Movable on-screen elements",
  World: "World overlays and helpers",
  Misc: "Quality of life",
  Cosmetics: "Only you see these",
  Client: "Menus, GUI and performance",
  Settings: "Client settings",
};

/** Module state shared by every ClickGUI on the page (the demo and the Right Shift overlay). */
let MODS = null;
const GUI_INSTANCES = [];
function sharedMods() {
  if (MODS) return MODS;
  const data = Array.isArray(window.STELLA_MODULES) ? window.STELLA_MODULES : [];
  MODS = data.map(m => ({ ...m, on: !!m.on, s: (m.s || []).map(s => ({ ...s, cur: s.v })) }));
  // a few modules start switched on, like a fresh install plus the ones people turn on first
  for (const n of ["Spawner Finder", "Storage ESP", "Radar HUD", "Region Map HUD", "Module List HUD", "Fullbright", "Custom Sky"]) {
    const m = MODS.find(x => x.n === n);
    if (m) m.on = true;
  }
  return MODS;
}

/**
 * One ClickGUI rendered into root. opts: search input and the window / dropdown view buttons.
 * Every instance shares the module state, so a switch flipped in one shows in the others.
 */
function clickGui(root, opts) {
  const mods = sharedMods();
  const cats = CATEGORY_ORDER.filter(c => mods.some(m => m.c === c));
  const state = { view: "window", cat: cats[0], query: "", open: null, expanded: new Set(), panelPos: {} };
  const searchEl = opts.search;
  const renderAll = () => GUI_INSTANCES.forEach(g => g.render());

  const matches = m => !state.query || m.n.toLowerCase().includes(state.query) || m.d.toLowerCase().includes(state.query);

  function settingRow(mod, s, rerender) {
    if (s.t === "bool") {
      const sw = el("button", { class: "switch", type: "button", role: "switch", "aria-checked": String(!!s.cur), "aria-label": s.n,
        onclick: () => { s.cur = !s.cur; sw.setAttribute("aria-checked", String(s.cur)); } });
      return el("div", { class: "row", title: s.d }, el("span", null, s.n), sw);
    }
    if (s.t === "num") {
      const id = "s-" + mod.n.replace(/\W/g, "") + "-" + s.n.replace(/\W/g, "");
      const val = el("span", { class: "val tab" }, fmt(s.cur, s.step));
      const input = el("input", { id, type: "range", min: s.min, max: s.max, step: s.step, value: s.cur, "aria-label": s.n,
        oninput: e => { s.cur = Number(e.target.value); val.textContent = fmt(s.cur, s.step); } });
      return el("div", { class: "row slider", title: s.d }, el("label", { for: id }, s.n), val, input);
    }
    if (s.t === "mode") {
      const btn = el("button", { class: "mode", type: "button", "aria-label": s.n + ": " + s.cur,
        onclick: () => { const i = s.o.indexOf(s.cur); s.cur = s.o[(i + 1) % s.o.length]; btn.textContent = "‹ " + s.cur + " ›"; btn.setAttribute("aria-label", s.n + ": " + s.cur); } }, "‹ " + s.cur + " ›");
      return el("div", { class: "row", title: s.d }, el("span", null, s.n), btn);
    }
    const box = el("span", { class: "colorbox", role: "img", "aria-label": s.n + " " + s.cur });
    box.style.setProperty("--c", s.cur);
    return el("div", { class: "row", title: s.d }, el("span", null, s.n), box);
  }

  function settingsWindow(mod, anchor) {
    const rows = mod.s.length ? mod.s.map(s => settingRow(mod, s)) : [el("p", { class: "empty" }, "No settings: just switch it on.")];
    const win = el("div", { class: "settings", role: "dialog", "aria-label": mod.n + " settings" },
      el("header", null,
        el("div", null, el("b", null, mod.n), el("p", null, mod.d)),
        el("button", { class: "x", type: "button", "aria-label": "Close settings", onclick: () => { state.open = null; render(); } }, "✕")),
      el("div", { class: "rows" }, rows),
      el("button", { class: "reset", type: "button", onclick: () => { mod.s.forEach(s => (s.cur = s.v)); render(); } }, "Reset module settings"));
    if (anchor) {
      const stage = root.getBoundingClientRect(), r = anchor.getBoundingClientRect();
      const winBox = $(".win", root).getBoundingClientRect();
      let left = winBox.right - stage.left + 12, top = r.top - stage.top - 10;
      if (left + 270 > stage.width) left = winBox.right - stage.left - 270 - 8; // no room beside: inside the right edge
      win.style.setProperty("left", left + "px");
      win.style.setProperty("top", Math.max(12, Math.min(top, stage.height - 360)) + "px");
    }
    return win;
  }

  function moduleSwitch(mod, onChange) {
    return el("button", { class: "switch", type: "button", role: "switch", "aria-checked": String(mod.on), "aria-label": "Toggle " + mod.n,
      onclick: e => { e.stopPropagation(); mod.on = !mod.on; onChange(); } });
  }

  function renderWindow() {
    const list = mods.filter(m => (state.query ? true : m.c === state.cat) && matches(m));
    const side = el("div", { class: "side" },
      el("div", { class: "logo" }, svgIcon("star"), "STELLA"),
      cats.map(c => {
        const inCat = mods.filter(m => m.c === c);
        return el("button", { class: "cat" + (c === state.cat && !state.query ? " on" : ""), type: "button", "aria-pressed": String(c === state.cat),
          onclick: () => { state.cat = c; state.query = ""; searchEl.value = ""; state.open = null; render(); } },
          c, el("small", { class: "tab" }, inCat.filter(m => m.on).length + "/" + inCat.length));
      }),
      el("div", { class: "spacer" }),
      el("div", { class: "themes", role: "group", "aria-label": "Theme" }, THEMES.map((t, i) => {
        const b = el("button", { class: "swatch" + (i === themeIndex ? " on" : ""), type: "button", title: t.n, "aria-label": "Theme: " + t.n, "aria-pressed": String(i === themeIndex),
          onclick: () => setTheme(i) });
        b.style.setProperty("--c", t.c);
        return b;
      })));
    let anchor = null;
    const cards = list.length ? list.map((m, i) => {
      const gear = el("button", { class: "gear", type: "button", "aria-label": m.n + " settings", "aria-expanded": String(state.open === m.n),
        onclick: e => { e.stopPropagation(); state.open = state.open === m.n ? null : m.n; render(); } }, svgIcon("gear"));
      const card = el("div", { class: "card" + (m.on ? " on" : ""), title: m.d },
        el("div", { class: "t" }, el("b", null, m.n), el("span", null, m.d)), gear, moduleSwitch(m, renderAll));
      card.style.setProperty("animation-delay", Math.min(i, 14) * 0.025 + "s");
      if (state.open === m.n) anchor = card;
      return card;
    }) : [el("p", { class: "empty" }, "No modules match “" + state.query + "”.")];
    const main = el("div", { class: "main" },
      el("div", { class: "main-head" }, el("h3", null, state.query ? "Search" : state.cat), el("p", null, state.query ? list.length + " modules" : CATEGORY_INFO[state.cat] || "")),
      el("div", { class: "cards" }, cards));
    const win = el("div", { class: "win" }, side, main);
    root.classList.toggle("with-settings", !!anchor);
    root.replaceChildren(win);
    const open = mods.find(m => m.n === state.open);
    if (open && anchor) root.append(settingsWindow(open, anchor));
  }

  function renderDropdown() {
    const wrap = el("div", { class: "panels" });
    const stageW = root.clientWidth - 32;
    const perRow = Math.max(1, Math.floor((stageW + 12) / 202));
    cats.forEach((c, i) => {
      const list = mods.filter(m => m.c === c && matches(m));
      const pos = state.panelPos[c] || { x: (i % perRow) * 202, y: Math.floor(i / perRow) * 250 + (i % 2) * 14 };
      const rows = list.map(m => {
        const row = el("button", { class: "prow" + (m.on ? " on" : ""), type: "button", "aria-pressed": String(m.on), title: m.d + " (right-click for settings)",
          onclick: () => { m.on = !m.on; renderAll(); },
          oncontextmenu: e => { e.preventDefault(); toggleExpand(m); } },
          el("span", null, m.n),
          el("span", { class: "more", role: "button", tabindex: "0", "aria-label": m.n + " settings",
            onclick: e => { e.stopPropagation(); toggleExpand(m); },
            onkeydown: e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); e.stopPropagation(); toggleExpand(m); } } },
            state.expanded.has(m.n) ? "−" : "+"));
        const out = [row];
        if (state.expanded.has(m.n)) out.push(el("div", { class: "pset" }, m.s.length ? m.s.map(s => settingRow(m, s)) : el("p", { class: "empty" }, "No settings")));
        return out;
      });
      const head = el("header", null, el("b", null, c), el("small", { class: "tab" }, list.filter(m => m.on).length + "/" + list.length));
      const panel = el("div", { class: "panel" }, head, el("div", { class: "plist" }, rows.length ? rows : el("p", { class: "empty" }, "—")));
      panel.style.setProperty("left", pos.x + "px");
      panel.style.setProperty("top", pos.y + "px");
      panel.style.setProperty("animation-delay", i * 0.05 + "s");
      drag(panel, head, c);
      wrap.append(panel);
    });
    const rowsUsed = Math.ceil(cats.length / perRow);
    wrap.style.setProperty("min-height", Math.max(470, rowsUsed * 250 + 120) + "px");
    root.replaceChildren(wrap);
  }

  function toggleExpand(m) {
    if (state.expanded.has(m.n)) state.expanded.delete(m.n); else state.expanded.add(m.n);
    render();
  }

  function drag(panel, handle, key) {
    handle.addEventListener("pointerdown", e => {
      if (window.matchMedia("(max-width: 760px)").matches) return;
      const sx = e.clientX, sy = e.clientY, ox = panel.offsetLeft, oy = panel.offsetTop;
      handle.setPointerCapture(e.pointerId);
      panel.style.setProperty("z-index", "4");
      const move = ev => {
        const x = ox + ev.clientX - sx, y = Math.max(0, oy + ev.clientY - sy);
        panel.style.setProperty("left", x + "px"); panel.style.setProperty("top", y + "px");
        state.panelPos[key] = { x, y };
      };
      const up = () => { handle.removeEventListener("pointermove", move); panel.style.removeProperty("z-index"); };
      handle.addEventListener("pointermove", move);
      handle.addEventListener("pointerup", up, { once: true });
      handle.addEventListener("pointercancel", up, { once: true });
    });
  }

  function render() {
    if (state.view === "window") renderWindow(); else renderDropdown();
  }

  searchEl.addEventListener("input", e => { state.query = e.target.value.trim().toLowerCase().slice(0, 40); state.open = null; render(); });
  const setView = v => {
    state.view = v; state.open = null;
    if (opts.windowBtn) { opts.windowBtn.classList.toggle("on", v === "window"); opts.windowBtn.setAttribute("aria-pressed", String(v === "window")); }
    if (opts.dropdownBtn) { opts.dropdownBtn.classList.toggle("on", v === "dropdown"); opts.dropdownBtn.setAttribute("aria-pressed", String(v === "dropdown")); }
    render();
  };
  if (opts.windowBtn) opts.windowBtn.addEventListener("click", () => setView("window"));
  if (opts.dropdownBtn) opts.dropdownBtn.addEventListener("click", () => setView("dropdown"));
  let rw = root.clientWidth;
  new ResizeObserver(() => { if (Math.abs(root.clientWidth - rw) > 40) { rw = root.clientWidth; if (state.view === "dropdown") { state.panelPos = {}; render(); } } }).observe(root);
  const api = { render, closeSettings: () => { if (!state.open) return false; state.open = null; render(); return true; } };
  GUI_INSTANCES.push(api);
  render();
  return api;
}

function fmt(v, step) { return step >= 1 ? String(Math.round(v)) : Number(v).toFixed(step >= 0.1 ? 1 : 2); }
function mix(a, b, t) {
  const pa = parseInt(a.slice(1), 16), pb = parseInt(b.slice(1), 16);
  const ch = s => [(s >> 16) & 255, (s >> 8) & 255, s & 255];
  const [r1, g1, b1] = ch(pa), [r2, g2, b2] = ch(pb);
  const m = (x, y) => Math.round(x + (y - x) * t).toString(16).padStart(2, "0");
  return "#" + m(r1, r2) + m(g1, g2) + m(b1, b2);
}

/* ------------------------------------------------------------------ start */
function start() {
  fillPlaceholders();
  setupNav();
  setupCheckout();
  heroTitle();
  sky();
  clickGui($("#gui"), { search: $("#gui-search"), windowBtn: $("#view-window"), dropdownBtn: $("#view-dropdown") });
  setupOverlay();
  setTheme(0);
  const cycle = $("#theme-cycle");
  if (cycle) cycle.addEventListener("click", () => setTheme(themeIndex + 1));
  if (typeof setupDemos === "function") setupDemos();
  tilt();
  gallery();
  route();
  reveal();
  counters();
  window.addEventListener("hashchange", route);
}
// start() runs from the end of demos.js, the last script, so every function is defined first
