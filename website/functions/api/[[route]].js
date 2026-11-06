/**
 * Stella dashboard API (Cloudflare Pages Function, handles every /api/* request).
 *
 *   POST   /api/session            { key }            sign in with a licence key (sets an HttpOnly cookie)
 *   DELETE /api/session                               sign out
 *   GET    /api/me                                    plan, masked email, latest release
 *   GET    /api/download                              the latest Stella jar (valid licence only)
 *   GET    /api/suggestions                           buyers' suggestions with vote counts
 *   POST   /api/suggestions        { title, body }
 *   POST   /api/suggestions/vote   { id }             toggles your vote
 *   GET    /api/reviews                               approved reviews (+ whether you've written one)
 *   POST   /api/reviews            { rating, text, name }
 *
 * Bindings: DB (D1), RELEASES (R2).
 * Variables: LS_STORE_ID, LS_PRODUCT_ID, LS_VARIANT_MONTHLY, LS_VARIANT_LIFETIME,
 *            RELEASE_KEY, RELEASE_VERSION, RELEASE_SHA256, PORTAL_URL (optional).
 * Secrets:   SESSION_KEY (32 random bytes, base64), PEPPER (random string).
 *
 * Security: licence keys are checked with Lemon Squeezy on sign-in and re-checked every 6 hours;
 * sessions are random 256-bit tokens in a Secure, HttpOnly, SameSite=Strict cookie and only their
 * hash is stored; the licence key is stored AES-GCM encrypted; every state-changing request must come
 * from this site (Origin check + custom header); inputs are length-checked; actions are rate-limited.
 */

const COOKIE = "__Host-stella_session";
const SESSION_DAYS = 7;
const RECHECK_SECONDS = 6 * 3600;
const LS_VALIDATE = "https://api.lemonsqueezy.com/v1/licenses/validate";

const LIMITS = {
  signin: [10, 15 * 60],      // 10 attempts per 15 minutes per IP
  suggest: [5, 3600],         // 5 suggestions per hour per licence
  vote: [60, 3600],
  review: [5, 24 * 3600],
  read: [240, 60],            // generous read limit per IP
};

export async function onRequest(ctx) {
  try {
    return withHeaders(await route(ctx));
  } catch (e) {
    if (e instanceof HttpError) return withHeaders(json({ error: e.message }, e.status));
    console.error("api error", e && e.stack ? e.stack : e);
    return withHeaders(json({ error: "Something went wrong on our side. Try again in a minute." }, 500));
  }
}

/* ------------------------------------------------------------------ routing */
async function route({ request, env }) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "");
  const method = request.method;
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";

  if (method !== "GET" && method !== "HEAD") checkSameSite(request, url);
  else await limit(env, "read:" + await hash(ip, env), LIMITS.read);

  if (path === "/api/session" && method === "POST") return signIn(request, env, ip);
  if (path === "/api/session" && method === "DELETE") return signOut(request, env);
  if (path === "/api/me" && method === "GET") return me(await session(request, env), env);
  if (path === "/api/download" && method === "GET") return download(await session(request, env), env);
  if (path === "/api/suggestions" && method === "GET") return listSuggestions(await session(request, env), env);
  if (path === "/api/suggestions" && method === "POST") return addSuggestion(request, await session(request, env), env);
  if (path === "/api/suggestions/vote" && method === "POST") return vote(request, await session(request, env), env);
  if (path === "/api/reviews" && method === "GET") return listReviews(request, env);
  if (path === "/api/reviews" && method === "POST") return addReview(request, await session(request, env), env);
  throw new HttpError(404, "Not found");
}

/* ------------------------------------------------------------------ sign in / out */
async function signIn(request, env, ip) {
  await limit(env, "signin:" + await hash(ip, env), LIMITS.signin);
  const body = await readJson(request);
  const key = String(body.key || "").trim();
  if (!/^[A-Za-z0-9-]{16,64}$/.test(key)) throw new HttpError(400, "That doesn't look like a licence key. Copy it from your purchase email.");

  const lic = await validateLicense(key, env);
  if (!lic.ok) throw new HttpError(401, lic.reason);

  const token = randomToken();
  const now = nowSec();
  await env.DB.prepare(
    "INSERT INTO sessions (id_hash, license_hash, license_enc, plan, email_masked, renews_at, expires_at, checked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
  ).bind(await hash(token, env), await hash(key, env), await encrypt(key, env), lic.plan, lic.email, lic.renews, now + SESSION_DAYS * 86400, now).run();
  // housekeeping: forget expired sessions
  await env.DB.prepare("DELETE FROM sessions WHERE expires_at < ?").bind(now).run();

  return json({ ok: true }, 200, {
    "Set-Cookie": `${COOKIE}=${token}; Path=/; Max-Age=${SESSION_DAYS * 86400}; HttpOnly; Secure; SameSite=Strict`,
  });
}

async function signOut(request, env) {
  const token = readCookie(request);
  if (token) await env.DB.prepare("DELETE FROM sessions WHERE id_hash = ?").bind(await hash(token, env)).run();
  return json({ ok: true }, 200, { "Set-Cookie": `${COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict` });
}

/** The signed-in buyer, re-checking the licence with the store every few hours. Throws 401 if none. */
async function session(request, env) {
  const token = readCookie(request);
  if (!token) throw new HttpError(401, "Sign in with your licence key.");
  const row = await env.DB.prepare("SELECT * FROM sessions WHERE id_hash = ?").bind(await hash(token, env)).first();
  const now = nowSec();
  if (!row || row.expires_at < now) throw new HttpError(401, "Your session has ended. Sign in again.");
  if (now - row.checked_at > RECHECK_SECONDS) {
    const lic = await validateLicense(await decrypt(row.license_enc, env), env);
    if (!lic.ok) {
      await env.DB.prepare("DELETE FROM sessions WHERE id_hash = ?").bind(row.id_hash).run();
      throw new HttpError(401, lic.reason);
    }
    await env.DB.prepare("UPDATE sessions SET checked_at = ?, plan = ?, renews_at = ? WHERE id_hash = ?").bind(now, lic.plan, lic.renews, row.id_hash).run();
    row.plan = lic.plan; row.renews_at = lic.renews;
  }
  return row;
}

/** Asks Lemon Squeezy whether the key is a valid licence for this product. */
async function validateLicense(key, env) {
  const res = await fetch(LS_VALIDATE, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ license_key: key }).toString(),
  });
  let data = {};
  try { data = await res.json(); } catch { /* treated as invalid below */ }
  const meta = data.meta || {}, lk = data.license_key || {};
  if (!data.valid) return { ok: false, reason: lk.status === "expired" ? "This licence has expired. Renew your Monthly plan to download again." : "We couldn't find that licence key. Copy it from your purchase email." };
  if (String(meta.store_id) !== String(env.LS_STORE_ID) || String(meta.product_id) !== String(env.LS_PRODUCT_ID))
    return { ok: false, reason: "That licence key is for a different product." };
  const variant = String(meta.variant_id);
  const plan = variant === String(env.LS_VARIANT_LIFETIME) ? "lifetime" : variant === String(env.LS_VARIANT_MONTHLY) ? "monthly" : null;
  if (!plan) return { ok: false, reason: "That licence key is for a different product." };
  return { ok: true, plan, email: maskEmail(meta.customer_email), renews: plan === "monthly" && lk.expires_at ? String(lk.expires_at).slice(0, 10) : null };
}

/* ------------------------------------------------------------------ dashboard data */
function me(s, env) {
  return json({
    plan: s.plan,
    email: s.email_masked,
    renews: s.renews_at,
    portal: safeHttps(env.PORTAL_URL),
    release: { version: env.RELEASE_VERSION || "", sha256: env.RELEASE_SHA256 || "", minecraft: "1.21.11" },
  });
}

async function download(s, env) {
  const obj = await env.RELEASES.get(env.RELEASE_KEY);
  if (!obj) throw new HttpError(503, "The download isn't available right now. Try again soon.");
  const name = "stella-client-" + String(env.RELEASE_VERSION || "latest").replace(/[^\w.-]/g, "") + ".jar";
  return new Response(obj.body, {
    headers: {
      "Content-Type": "application/java-archive",
      "Content-Disposition": `attachment; filename="${name}"`,
      "Cache-Control": "private, no-store",
      ...(obj.size ? { "Content-Length": String(obj.size) } : {}),
    },
  });
}

async function listSuggestions(s, env) {
  const rows = (await env.DB.prepare(
    `SELECT s.id, s.title, s.body, s.status, s.created_at,
            (SELECT COUNT(*) FROM votes v WHERE v.suggestion_id = s.id) AS votes,
            EXISTS (SELECT 1 FROM votes v WHERE v.suggestion_id = s.id AND v.license_hash = ?) AS voted
       FROM suggestions s WHERE s.status != 'hidden' ORDER BY votes DESC, s.created_at DESC LIMIT 100`
  ).bind(s.license_hash).all()).results || [];
  return json({ items: rows.map(r => ({ id: r.id, title: r.title, body: r.body, status: r.status, votes: Number(r.votes), voted: !!r.voted, created: r.created_at })) });
}

async function addSuggestion(request, s, env) {
  await limit(env, "suggest:" + s.license_hash, LIMITS.suggest);
  const b = await readJson(request);
  const title = cleanText(b.title, 4, 80, "title"), body = cleanText(b.body, 0, 600, "details");
  const r = await env.DB.prepare("INSERT INTO suggestions (license_hash, title, body, created_at) VALUES (?, ?, ?, ?)")
    .bind(s.license_hash, title, body, nowSec()).run();
  // vote for your own suggestion
  const id = r.meta && r.meta.last_row_id;
  if (id) await env.DB.prepare("INSERT OR IGNORE INTO votes (suggestion_id, license_hash) VALUES (?, ?)").bind(id, s.license_hash).run();
  return json({ ok: true, id }, 201);
}

async function vote(request, s, env) {
  await limit(env, "vote:" + s.license_hash, LIMITS.vote);
  const b = await readJson(request);
  const id = Number(b.id);
  if (!Number.isInteger(id) || id < 1) throw new HttpError(400, "Unknown suggestion.");
  const exists = await env.DB.prepare("SELECT id FROM suggestions WHERE id = ? AND status != 'hidden'").bind(id).first();
  if (!exists) throw new HttpError(404, "That suggestion no longer exists.");
  const had = await env.DB.prepare("SELECT 1 AS x FROM votes WHERE suggestion_id = ? AND license_hash = ?").bind(id, s.license_hash).first();
  if (had) await env.DB.prepare("DELETE FROM votes WHERE suggestion_id = ? AND license_hash = ?").bind(id, s.license_hash).run();
  else await env.DB.prepare("INSERT INTO votes (suggestion_id, license_hash) VALUES (?, ?)").bind(id, s.license_hash).run();
  return json({ ok: true, voted: !had });
}

async function listReviews(request, env) {
  const rows = (await env.DB.prepare("SELECT rating, text, name, created_at FROM reviews WHERE status = 'approved' ORDER BY created_at DESC LIMIT 50").all()).results || [];
  let mine = false;
  const token = readCookie(request);
  if (token) {
    const srow = await env.DB.prepare("SELECT license_hash FROM sessions WHERE id_hash = ? AND expires_at > ?").bind(await hash(token, env), nowSec()).first();
    if (srow) mine = !!(await env.DB.prepare("SELECT 1 AS x FROM reviews WHERE license_hash = ?").bind(srow.license_hash).first());
  }
  return json({ items: rows.map(r => ({ rating: r.rating, text: r.text, name: r.name, created: r.created_at })), mine });
}

async function addReview(request, s, env) {
  await limit(env, "review:" + s.license_hash, LIMITS.review);
  const b = await readJson(request);
  const rating = Number(b.rating);
  if (!Number.isInteger(rating) || rating < 1 || rating > 5) throw new HttpError(400, "Pick a rating from 1 to 5 stars.");
  const text = cleanText(b.text, 10, 600, "review");
  const name = cleanText(b.name || "Stella user", 2, 24, "name");
  const existing = await env.DB.prepare("SELECT id FROM reviews WHERE license_hash = ?").bind(s.license_hash).first();
  if (existing) {
    await env.DB.prepare("UPDATE reviews SET rating = ?, text = ?, name = ?, status = 'pending', created_at = ? WHERE id = ?").bind(rating, text, name, nowSec(), existing.id).run();
  } else {
    await env.DB.prepare("INSERT INTO reviews (license_hash, rating, text, name, created_at) VALUES (?, ?, ?, ?, ?)").bind(s.license_hash, rating, text, name, nowSec()).run();
  }
  return json({ ok: true, pending: true }, 201);
}

/* ------------------------------------------------------------------ security helpers */
class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }

/** State-changing requests must come from this site's own pages. */
function checkSameSite(request, url) {
  const origin = request.headers.get("Origin");
  if (!origin || origin !== url.origin) throw new HttpError(403, "Requests must come from the Stella website.");
  if (request.headers.get("X-Stella") !== "1") throw new HttpError(403, "Requests must come from the Stella website.");
}

async function readJson(request) {
  const type = request.headers.get("Content-Type") || "";
  if (!type.startsWith("application/json")) throw new HttpError(415, "Send JSON.");
  const text = await request.text();
  if (text.length > 4096) throw new HttpError(413, "That's too long.");
  try { const v = JSON.parse(text); if (v && typeof v === "object" && !Array.isArray(v)) return v; } catch { /* below */ }
  throw new HttpError(400, "Send a JSON object.");
}

/** Trims, removes control characters and checks the length. Output is plain text (the page shows it as text). */
function cleanText(v, min, max, label) {
  // eslint-disable-next-line no-control-regex
  const t = String(v ?? "").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F​-‏‪-‮⁦-⁩]/g, "").replace(/\s+\n/g, "\n").trim();
  if (t.length < min) throw new HttpError(400, `The ${label} is too short.`);
  if (t.length > max) throw new HttpError(400, `The ${label} can be at most ${max} characters.`);
  return t;
}

async function limit(env, bucket, [max, windowSec]) {
  const now = nowSec(), start = now - (now % windowSec);
  const row = await env.DB.prepare("SELECT window_start, count FROM rate_limits WHERE bucket = ?").bind(bucket).first();
  if (!row || row.window_start !== start) {
    await env.DB.prepare("INSERT OR REPLACE INTO rate_limits (bucket, window_start, count) VALUES (?, ?, 1)").bind(bucket, start).run();
    return;
  }
  if (row.count >= max) throw new HttpError(429, "Too many requests. Wait a little and try again.");
  await env.DB.prepare("UPDATE rate_limits SET count = count + 1 WHERE bucket = ?").bind(bucket).run();
}

function readCookie(request) {
  const raw = request.headers.get("Cookie") || "";
  for (const part of raw.split(";")) {
    const [k, ...v] = part.trim().split("=");
    if (k === COOKIE) { const t = v.join("="); return /^[A-Za-z0-9_-]{43}$/.test(t) ? t : null; }
  }
  return null;
}

function randomToken() {
  const b = new Uint8Array(32);
  crypto.getRandomValues(b);
  return b64url(b);
}

async function hash(value, env) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(String(env.PEPPER || "") + ":" + value));
  return b64url(new Uint8Array(d));
}

async function aesKey(env) {
  const raw = Uint8Array.from(atob(String(env.SESSION_KEY || "")), c => c.charCodeAt(0));
  if (raw.length !== 32) throw new Error("SESSION_KEY must be 32 random bytes, base64-encoded");
  return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
}

async function encrypt(text, env) {
  const iv = new Uint8Array(12);
  crypto.getRandomValues(iv);
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await aesKey(env), new TextEncoder().encode(text)));
  return b64url(iv) + "." + b64url(ct);
}

async function decrypt(blob, env) {
  const [iv, ct] = String(blob).split(".").map(fromB64url);
  return new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv }, await aesKey(env), ct));
}

function b64url(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64url(s) {
  const p = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  return Uint8Array.from(atob(p), c => c.charCodeAt(0));
}

function maskEmail(e) {
  const m = /^([^@]{1,64})@(.+)$/.exec(String(e || ""));
  if (!m) return "";
  return m[1].slice(0, 1) + "***@" + m[2];
}

function safeHttps(u) {
  try { const x = new URL(u); return x.protocol === "https:" ? x.href : null; } catch { return null; }
}

const nowSec = () => Math.floor(Date.now() / 1000);

function json(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json; charset=utf-8", ...extra } });
}

function withHeaders(res) {
  const h = new Headers(res.headers);
  h.set("Cache-Control", h.get("Cache-Control") || "no-store");
  h.set("X-Content-Type-Options", "nosniff");
  h.set("Referrer-Policy", "no-referrer");
  h.set("X-Frame-Options", "DENY");
  h.set("Cross-Origin-Resource-Policy", "same-origin");
  h.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
  return new Response(res.body, { status: res.status, headers: h });
}
