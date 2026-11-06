/*
 * Stella website: the buyer dashboard (#dashboard) and verified-buyer reviews on the home page.
 * Talks only to this site's own /api (see functions/api/[[route]].js). Everything people write is shown
 * as text (textContent), never as HTML.
 */
"use strict";

const DASH = { me: null, example: false, suggestions: [], reviews: [], myReview: false };

async function api(method, path, body) {
  try {
    const res = await fetch(path, {
      method, credentials: "same-origin", cache: "no-store",
      headers: body ? { "Content-Type": "application/json", "X-Stella": "1" } : { "X-Stella": "1" },
      body: body ? JSON.stringify(body) : undefined,
    });
    let data = null;
    try { data = await res.json(); } catch { /* not JSON: no backend here */ }
    return { status: res.status, data, ok: res.ok && data !== null };
  } catch {
    return { status: 0, data: null, ok: false };
  }
}

function setupDashboard() {
  loadPublicReviews();
  let loaded = false;
  const onView = () => {
    if (location.hash === "#dashboard" && !loaded) { loaded = true; loadDashboard(); }
  };
  window.addEventListener("hashchange", onView);
  onView();
}

async function loadDashboard() {
  const root = $("#dash-root");
  root.replaceChildren(el("div", { class: "dash-loading" }, el("span", { class: "spinner", "aria-hidden": "true" }), "Loading your dashboard..."));
  const r = await api("GET", "/api/me");
  if (r.ok) { DASH.me = r.data; DASH.example = false; await refreshLists(); renderDashboard(); return; }
  if (r.status === 401) { renderSignIn(); return; }
  renderNotConnected();
}

/* ------------------------------------------------------------------ signed out */
function renderSignIn(message) {
  const root = $("#dash-root");
  const input = el("input", { id: "license-key", class: "field", autocomplete: "off", spellcheck: "false", maxlength: "64", placeholder: "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX", "aria-describedby": "key-help" });
  const err = el("p", { class: "form-error", role: "alert" }, message || "");
  const btn = el("button", { class: "btn", type: "submit" }, "Open my dashboard");
  const form = el("form", { class: "dash-signin", novalidate: true },
    el("label", { for: "license-key" }, "Licence key"),
    input,
    el("p", { id: "key-help", class: "muted small" }, "It's in the receipt email you got after checkout. Your key stays private: we only use it to check your purchase."),
    err, btn);
  form.addEventListener("submit", async e => {
    e.preventDefault();
    const key = input.value.trim();
    if (!/^[A-Za-z0-9-]{16,64}$/.test(key)) { err.textContent = "Paste the full licence key from your purchase email."; input.focus(); return; }
    btn.disabled = true; btn.textContent = "Checking your licence...";
    const r = await api("POST", "/api/session", { key });
    btn.disabled = false; btn.textContent = "Open my dashboard";
    if (r.ok) { loadDashboard(); return; }
    err.textContent = (r.data && r.data.error) || "We couldn't reach the server. Check your connection and try again.";
  });
  root.replaceChildren(el("div", { class: "dash-card dash-auth" },
    el("h2", null, "Sign in with your licence key"),
    el("p", { class: "muted" }, "Download Stella, see your plan, suggest features and leave a review."),
    form,
    el("p", { class: "muted small" }, "Haven't bought Stella yet? ", el("a", { href: "#pricing" }, "See the plans"), ".")));
  input.focus({ preventScroll: true });
}

function renderNotConnected() {
  $("#dash-root").replaceChildren(el("div", { class: "dash-card dash-auth" },
    el("h2", null, "The dashboard isn't connected yet"),
    el("p", { class: "muted" }, "Buyers sign in here with their licence key once the Stella backend is set up on the live site (see the website README). You can look around with example data in the meantime."),
    el("button", { class: "btn", type: "button", onclick: showExample }, "Show the example dashboard")));
}

function showExample() {
  DASH.example = true;
  DASH.me = { plan: "lifetime", email: "y***@example.com", renews: null, portal: null,
    release: { version: "1.0.143", sha256: "62f3077628ded5710f0e964b5428436c4bbe79605cff307df5a85d39c038443b", minecraft: "1.21.11" } };
  DASH.suggestions = [
    { id: 1, title: "Price history in Auction Helper", body: "Show how an item's price changed over the last week.", status: "planned", votes: 14, voted: true },
    { id: 2, title: "Waypoints for spawners", body: "Save a spawner from Spawner Finder as a waypoint.", status: "open", votes: 9, voted: false },
    { id: 3, title: "More knife finishes", body: "", status: "done", votes: 6, voted: false },
  ];
  DASH.reviews = [];
  DASH.myReview = false;
  renderDashboard();
}

async function refreshLists() {
  const [s, r] = await Promise.all([api("GET", "/api/suggestions"), api("GET", "/api/reviews")]);
  DASH.suggestions = s.ok ? s.data.items : [];
  DASH.reviews = r.ok ? r.data.items : [];
  DASH.myReview = r.ok ? !!r.data.mine : false;
}

/* ------------------------------------------------------------------ signed in */
function renderDashboard() {
  const me = DASH.me;
  const lifetime = me.plan === "lifetime";
  const root = $("#dash-root");
  const parts = [];
  if (DASH.example) parts.push(el("p", { class: "example-banner", role: "note" }, "Example data. Nothing here is a real account, order or suggestion."));

  // top: plan + account
  parts.push(el("div", { class: "dash-top" },
    el("div", null,
      el("span", { class: "plan-badge " + (lifetime ? "life" : "month") }, lifetime ? "Lifetime" : "Monthly"),
      el("span", { class: "muted" }, me.email ? " Signed in as " + me.email : "")),
    DASH.example ? el("button", { class: "btn small ghost", type: "button", onclick: renderNotConnected }, "Close example")
      : el("button", { class: "btn small ghost", type: "button", onclick: async () => { await api("DELETE", "/api/session"); renderSignIn("You're signed out."); } }, "Sign out")));

  const grid = el("div", { class: "dash-grid" });

  // download
  const sha = me.release.sha256 || "";
  const copyBtn = el("button", { class: "btn small ghost", type: "button", onclick: async () => {
    try { await navigator.clipboard.writeText(sha); copyBtn.textContent = "Copied"; }
    catch { const sel = window.getSelection(), range = document.createRange(); range.selectNodeContents(shaCode); sel.removeAllRanges(); sel.addRange(range); copyBtn.textContent = "Selected: press Ctrl+C"; }
    setTimeout(() => (copyBtn.textContent = "Copy hash"), 2200);
  } }, "Copy hash");
  const shaCode = el("code", { class: "sha" }, sha);
  const dl = DASH.example
    ? el("button", { class: "btn big", type: "button", disabled: true, title: "Downloads work on the live site" }, "Download Stella " + me.release.version)
    : el("a", { class: "btn big", href: "/api/download" }, "Download Stella " + me.release.version);
  grid.append(el("article", { class: "dash-card dl span2" },
    el("p", { class: "k" }, "Latest version"),
    el("h2", null, "Stella " + me.release.version),
    el("p", { class: "muted" }, "Minecraft " + (me.release.minecraft || "1.21.11") + " · Fabric Loader 0.17.3+ · Fabric API"),
    el("div", { class: "dl-row" }, dl, DASH.example ? el("span", { class: "muted small" }, "Downloads work on the live site.") : null),
    el("div", { class: "sha-row" }, el("span", { class: "muted small" }, "SHA-256"), shaCode, copyBtn),
    el("p", { class: "muted small" }, "Passed the in-game test and the malware scan before release. Check the hash matches the file you downloaded.")));

  // plan
  const planCard = el("article", { class: "dash-card" },
    el("p", { class: "k" }, "Your plan"),
    el("h3", null, lifetime ? "Lifetime" : "Monthly · €8 / month"),
    el("p", { class: "muted" }, lifetime ? "Paid once. Every update is yours." : me.renews ? "Renews on " + formatDate(me.renews) + ". Cancel any time and keep access until then." : "Renews monthly."));
  const portal = safeUrl(me.portal);
  if (portal) planCard.append(el("a", { class: "btn small ghost", href: portal, target: "_blank", rel: "noopener noreferrer" }, lifetime ? "Billing and receipts" : "Manage subscription"));
  grid.append(planCard);

  // install
  grid.append(el("article", { class: "dash-card" },
    el("p", { class: "k" }, "Install"),
    el("ol", { class: "mini-steps" },
      el("li", null, "Install Fabric Loader for 1.21.11 and Fabric API."),
      el("li", null, "Put the Stella jar in .minecraft/mods (remove older Stella jars)."),
      el("li", null, "Launch and press ", el("span", { class: "kbd" }, "Right Shift"), "."))));

  // what's new (from the update log on this page)
  const news = $$("#updates .release").slice(0, 3).map(r => el("li", null, el("b", null, $("h2", r).textContent), " ", $("li", r).textContent));
  grid.append(el("article", { class: "dash-card span2" }, el("p", { class: "k" }, "What's new"), el("ul", { class: "news" }, news), el("a", { href: "#updates" }, "Full update log")));

  grid.append(suggestionsCard());
  grid.append(reviewCard());
  parts.push(grid);
  root.replaceChildren(...parts);
}

function suggestionsCard() {
  const list = el("ul", { class: "sugg-list" });
  const statusName = { open: "Open", planned: "Planned", done: "Done" };
  const draw = () => {
    if (!DASH.suggestions.length) { list.replaceChildren(el("li", { class: "empty" }, "No suggestions yet. Be the first.")); return; }
    list.replaceChildren(...DASH.suggestions.map(s => {
      const vb = el("button", { class: "vote" + (s.voted ? " on" : ""), type: "button", "aria-pressed": String(s.voted), "aria-label": (s.voted ? "Remove your vote for " : "Vote for ") + s.title,
        onclick: async () => {
          if (DASH.example) { s.voted = !s.voted; s.votes += s.voted ? 1 : -1; draw(); return; }
          const r = await api("POST", "/api/suggestions/vote", { id: s.id });
          if (r.ok) { s.voted = r.data.voted; s.votes += s.voted ? 1 : -1; draw(); } else msg.textContent = (r.data && r.data.error) || "Couldn't save your vote.";
        } }, el("span", { "aria-hidden": "true" }, "▲"), el("b", { class: "tab" }, String(s.votes)));
      return el("li", null, vb, el("div", { class: "sugg-text" }, el("p", { class: "t" }, s.title, " ", el("span", { class: "status " + s.status }, statusName[s.status] || "Open")), s.body ? el("p", { class: "muted small" }, s.body) : null));
    }));
  };
  draw();
  const title = el("input", { id: "sugg-title", class: "field", maxlength: "80", placeholder: "Your idea in a few words", required: true });
  const body = el("textarea", { id: "sugg-body", class: "field", maxlength: "600", rows: "3", placeholder: "Details (optional)" });
  const msg = el("p", { class: "form-msg", role: "status" });
  const form = el("form", { class: "dash-form" }, el("label", { for: "sugg-title" }, "Suggest a feature"), title, body, el("button", { class: "btn small", type: "submit" }, "Post suggestion"), msg);
  form.addEventListener("submit", async e => {
    e.preventDefault();
    const t = title.value.trim(), b = body.value.trim();
    if (t.length < 4) { msg.textContent = "Give your idea a title of at least 4 characters."; title.focus(); return; }
    if (DASH.example) { DASH.suggestions.unshift({ id: Date.now(), title: t, body: b, status: "open", votes: 1, voted: true }); draw(); form.reset(); msg.textContent = "Posted (example only)."; return; }
    const r = await api("POST", "/api/suggestions", { title: t, body: b });
    if (!r.ok) { msg.textContent = (r.data && r.data.error) || "Couldn't post your suggestion. Try again."; return; }
    form.reset(); msg.textContent = "Posted. Thanks!";
    const s = await api("GET", "/api/suggestions");
    if (s.ok) { DASH.suggestions = s.data.items; draw(); }
  });
  return el("article", { class: "dash-card span2" }, el("p", { class: "k" }, "Suggestions"), el("h3", null, "Shape what comes next"),
    el("p", { class: "muted" }, "Vote on ideas from other players. The most wanted ones get built first."), form, list);
}

function reviewCard() {
  let rating = 0;
  const stars = el("div", { class: "stars-input", role: "radiogroup", "aria-label": "Rating" });
  const paint = () => $$("button", stars).forEach((b, i) => { b.classList.toggle("on", i < rating); b.setAttribute("aria-checked", String(i + 1 === rating)); });
  for (let i = 1; i <= 5; i++) stars.append(el("button", { type: "button", role: "radio", "aria-checked": "false", "aria-label": i + (i === 1 ? " star" : " stars"), onclick: () => { rating = i; paint(); } }, "★"));
  const text = el("textarea", { id: "review-text", class: "field", maxlength: "600", rows: "3", placeholder: "What do you like? What could be better?" });
  const name = el("input", { id: "review-name", class: "field", maxlength: "24", placeholder: "Name to show (optional)" });
  const msg = el("p", { class: "form-msg", role: "status" }, DASH.myReview ? "You've written a review. Sending a new one replaces it." : "");
  const form = el("form", { class: "dash-form" }, el("label", null, "Your rating"), stars, el("label", { for: "review-text" }, "Your review"), text, name,
    el("button", { class: "btn small", type: "submit" }, "Send review"), msg);
  form.addEventListener("submit", async e => {
    e.preventDefault();
    if (!rating) { msg.textContent = "Pick a rating from 1 to 5 stars."; return; }
    if (text.value.trim().length < 10) { msg.textContent = "Write at least a sentence (10 characters)."; text.focus(); return; }
    if (DASH.example) { msg.textContent = "Thanks! On the live site your review appears here once it's been checked (example only)."; form.reset(); rating = 0; paint(); return; }
    const r = await api("POST", "/api/reviews", { rating, text: text.value.trim(), name: name.value.trim() || "Stella user" });
    if (!r.ok) { msg.textContent = (r.data && r.data.error) || "Couldn't send your review. Try again."; return; }
    form.reset(); rating = 0; paint();
    msg.textContent = "Thanks! Your review appears on the site once it's been checked.";
  });
  const list = DASH.reviews.length ? el("div", { class: "review-mini" }, DASH.reviews.slice(0, 3).map(reviewEl)) : el("p", { class: "muted small" }, "No reviews yet.");
  return el("article", { class: "dash-card" }, el("p", { class: "k" }, "Reviews"), el("h3", null, "Rate Stella"), form, list);
}

function reviewEl(r) {
  const n = Math.max(1, Math.min(5, Number(r.rating) || 0));
  return el("figure", { class: "review" },
    el("p", { class: "stars", "aria-label": n + " out of 5 stars" }, "★".repeat(n), el("span", { class: "dim", "aria-hidden": "true" }, "★".repeat(5 - n))),
    el("blockquote", null, r.text),
    el("figcaption", null, r.name, el("span", { class: "verified" }, "Verified buyer")));
}

/* ------------------------------------------------------------------ home page: approved reviews only */
async function loadPublicReviews() {
  const r = await api("GET", "/api/reviews");
  if (!r.ok || !Array.isArray(r.data.items) || !r.data.items.length) return; // nothing real to show: keep the section hidden
  const items = r.data.items;
  const avg = items.reduce((a, x) => a + Number(x.rating || 0), 0) / items.length;
  $("#reviews-summary").textContent = avg.toFixed(1) + " out of 5 from " + items.length + " verified buyer" + (items.length === 1 ? "" : "s") + ".";
  $("#review-grid").replaceChildren(...items.slice(0, 9).map(reviewEl));
  $("#reviews").hidden = false;
}

/* ------------------------------------------------------------------ helpers */
function safeUrl(u) {
  if (!u) return null;
  try { const x = new URL(u); return x.protocol === "https:" ? x.href : null; } catch { return null; }
}

function formatDate(iso) {
  const d = new Date(iso + "T12:00:00Z");
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });
}
