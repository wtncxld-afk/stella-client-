// Tests the dashboard API (functions/api/[[route]].js) with a real SQLite database (node:sqlite standing in
// for Cloudflare D1), a fake R2 bucket and a mocked Lemon Squeezy licence check.
// Run:  node --no-warnings website/backend/test-api.mjs
import { DatabaseSync } from "node:sqlite";
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import assert from "node:assert/strict";

const here = dirname(fileURLToPath(import.meta.url));
const tmp = mkdtempSync(join(tmpdir(), "stella-api-"));
writeFileSync(join(tmp, "api.mjs"), readFileSync(join(here, "../functions/api/[[route]].js")));
const { onRequest } = await import(pathToFileURL(join(tmp, "api.mjs")).href);

// ---- D1 shim on SQLite
const sqlite = new DatabaseSync(":memory:");
sqlite.exec(readFileSync(join(here, "schema.sql"), "utf8"));
const DB = {
  prepare(sql) {
    let args = [];
    const st = sqlite.prepare(sql);
    return {
      bind(...a) { args = a.map(v => (v === undefined ? null : v)); return this; },
      async first() { return st.get(...args) ?? null; },
      async all() { return { results: st.all(...args) }; },
      async run() { const r = st.run(...args); return { meta: { last_row_id: Number(r.lastInsertRowid), changes: r.changes } }; },
    };
  },
};
const RELEASES = { async get(k) { return k === "stella.jar" ? { body: "JARBYTES", size: 8 } : null; } };
const env = {
  DB, RELEASES, LS_STORE_ID: "11", LS_PRODUCT_ID: "22", LS_VARIANT_MONTHLY: "31", LS_VARIANT_LIFETIME: "32",
  RELEASE_KEY: "stella.jar", RELEASE_VERSION: "1.0.143", RELEASE_SHA256: "62f3077628ded5710f0e964b5428436c4bbe79605cff307df5a85d39c038443b",
  PORTAL_URL: "https://stella.lemonsqueezy.com/billing",
  SESSION_KEY: Buffer.alloc(32, 7).toString("base64"), PEPPER: "test-pepper",
};

// ---- Lemon Squeezy mock
const LICENSES = {
  "LIFETIME-KEY-0000-0001": { valid: true, license_key: { status: "active" }, meta: { store_id: 11, product_id: 22, variant_id: 32, customer_email: "jamie@example.com" } },
  "MONTHLY-KEY-0000-00002": { valid: true, license_key: { status: "active", expires_at: "2026-12-06T10:00:00Z" }, meta: { store_id: 11, product_id: 22, variant_id: 31, customer_email: "alex@example.com" } },
  "EXPIRED-KEY-0000-00003": { valid: false, license_key: { status: "expired" }, meta: { store_id: 11, product_id: 22, variant_id: 31 } },
  "OTHERSHOP-KEY-00000004": { valid: true, license_key: { status: "active" }, meta: { store_id: 99, product_id: 22, variant_id: 32 } },
};
let lsCalls = 0;
globalThis.fetch = async (url, init) => {
  assert.equal(url, "https://api.lemonsqueezy.com/v1/licenses/validate");
  lsCalls++;
  const key = new URLSearchParams(init.body).get("license_key");
  return new Response(JSON.stringify(LICENSES[key] || { valid: false, error: "license_key not found" }));
};

// ---- helpers
const ORIGIN = "https://stellaclient.example";
let ipN = 0;
async function call(method, path, { body, cookie, origin = ORIGIN, header = true, ip } = {}) {
  const headers = { "CF-Connecting-IP": ip || "10.0.0." + (++ipN % 200) };
  if (method !== "GET") { if (origin) headers.Origin = origin; if (header) headers["X-Stella"] = "1"; headers["Content-Type"] = "application/json"; }
  if (cookie) headers.Cookie = cookie;
  const res = await onRequest({ request: new Request(ORIGIN + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) }), env });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data, headers: res.headers };
}
async function signIn(key) {
  const r = await call("POST", "/api/session", { body: { key } });
  assert.equal(r.status, 200, JSON.stringify(r.data));
  const c = r.headers.get("Set-Cookie");
  assert.match(c, /^__Host-stella_session=[A-Za-z0-9_-]{43}; Path=\/; Max-Age=604800; HttpOnly; Secure; SameSite=Strict$/);
  return c.split(";")[0];
}
let passed = 0;
async function test(name, fn) { await fn(); passed++; console.log("ok -", name); }

// ---- tests
await test("rejects a malformed key without calling the store", async () => {
  const before = lsCalls;
  const r = await call("POST", "/api/session", { body: { key: "<script>" } });
  assert.equal(r.status, 400); assert.equal(lsCalls, before);
});
await test("rejects unknown, expired and other-store keys", async () => {
  assert.equal((await call("POST", "/api/session", { body: { key: "NOPE-NOPE-NOPE-NOPE-1" } })).status, 401);
  const exp = await call("POST", "/api/session", { body: { key: "EXPIRED-KEY-0000-00003" } });
  assert.equal(exp.status, 401); assert.match(exp.data.error, /expired/);
  assert.equal((await call("POST", "/api/session", { body: { key: "OTHERSHOP-KEY-00000004" } })).status, 401);
});
await test("blocks cross-site and header-less POSTs (CSRF)", async () => {
  assert.equal((await call("POST", "/api/session", { body: { key: "LIFETIME-KEY-0000-0001" }, origin: "https://evil.example" })).status, 403);
  assert.equal((await call("POST", "/api/session", { body: { key: "LIFETIME-KEY-0000-0001" }, origin: null })).status, 403);
  assert.equal((await call("POST", "/api/session", { body: { key: "LIFETIME-KEY-0000-0001" }, header: false })).status, 403);
});
await test("needs a session for private data and the download", async () => {
  for (const p of ["/api/me", "/api/download", "/api/suggestions"]) assert.equal((await call("GET", p)).status, 401);
  assert.equal((await call("GET", "/api/me", { cookie: "__Host-stella_session=" + "A".repeat(43) })).status, 401);
});

const life = await signIn("LIFETIME-KEY-0000-0001");
const month = await signIn("MONTHLY-KEY-0000-00002");

await test("dashboard data: plan, masked email, release, portal", async () => {
  const a = (await call("GET", "/api/me", { cookie: life })).data;
  assert.equal(a.plan, "lifetime"); assert.equal(a.email, "j***@example.com"); assert.equal(a.release.version, "1.0.143"); assert.equal(a.renews, null);
  const b = (await call("GET", "/api/me", { cookie: month })).data;
  assert.equal(b.plan, "monthly"); assert.equal(b.renews, "2026-12-06"); assert.equal(b.portal, "https://stella.lemonsqueezy.com/billing");
});
await test("licence key is stored encrypted, sessions only as hashes", async () => {
  const rows = sqlite.prepare("SELECT * FROM sessions").all();
  const dump = JSON.stringify(rows);
  assert.ok(!dump.includes("LIFETIME-KEY") && !dump.includes("MONTHLY-KEY"), "raw key stored");
  assert.ok(!dump.includes(life.split("=")[1]), "raw session token stored");
});
await test("download streams the jar as an attachment", async () => {
  const r = await call("GET", "/api/download", { cookie: life });
  assert.equal(r.status, 200); assert.equal(r.data, "JARBYTES");
  assert.match(r.headers.get("Content-Disposition"), /attachment; filename="stella-client-1.0.143.jar"/);
  assert.equal(r.headers.get("Cache-Control"), "private, no-store");
});
await test("suggestions: add, list, vote toggles, one vote per buyer", async () => {
  const add = await call("POST", "/api/suggestions", { cookie: life, body: { title: "Auction price history", body: "Show the last 7 days of prices." } });
  assert.equal(add.status, 201);
  let list = (await call("GET", "/api/suggestions", { cookie: month })).data.items;
  assert.equal(list.length, 1); assert.equal(list[0].votes, 1); assert.equal(list[0].voted, false);
  await call("POST", "/api/suggestions/vote", { cookie: month, body: { id: list[0].id } });
  list = (await call("GET", "/api/suggestions", { cookie: month })).data.items;
  assert.equal(list[0].votes, 2); assert.equal(list[0].voted, true);
  await call("POST", "/api/suggestions/vote", { cookie: month, body: { id: list[0].id } });
  assert.equal((await call("GET", "/api/suggestions", { cookie: month })).data.items[0].votes, 1);
  assert.equal((await call("POST", "/api/suggestions/vote", { cookie: month, body: { id: 9999 } })).status, 404);
});
await test("input checks: lengths, control characters, non-JSON, oversized bodies", async () => {
  assert.equal((await call("POST", "/api/suggestions", { cookie: life, body: { title: "x", body: "" } })).status, 400);
  assert.equal((await call("POST", "/api/suggestions", { cookie: life, body: { title: "y".repeat(81), body: "" } })).status, 400);
  assert.equal((await call("POST", "/api/suggestions", { cookie: life, body: { title: "z", body: "a".repeat(5000) } })).status, 413);
  const res = await onRequest({ request: new Request(ORIGIN + "/api/suggestions", { method: "POST", headers: { Origin: ORIGIN, "X-Stella": "1", "Content-Type": "text/plain", Cookie: life }, body: "hi" }), env });
  assert.equal(res.status, 415);
  // those four rejected attempts used up the 5-per-hour suggestion limit (failed attempts count too)
  const limited = await call("POST", "/api/suggestions", { cookie: life, body: { title: "One too many", body: "" } });
  assert.equal(limited.status, 429, "suggestion rate limit");
  sqlite.prepare("DELETE FROM rate_limits").run();
  const ok = await call("POST", "/api/suggestions", { cookie: life, body: { title: "Bidi‮test\u0007 ok", body: "" } });
  assert.equal(ok.status, 201);
  const t = (await call("GET", "/api/suggestions", { cookie: life })).data.items.find(i => i.title.startsWith("Bidi"));
  assert.equal(t.title, "Biditest ok");
});
await test("reviews: pending until approved, one per buyer, rating checked", async () => {
  assert.equal((await call("POST", "/api/reviews", { cookie: life, body: { rating: 6, text: "Great client overall", name: "Jamie" } })).status, 400);
  assert.equal((await call("POST", "/api/reviews", { cookie: life, body: { rating: 5, text: "Great client overall", name: "Jamie" } })).status, 201);
  let r = (await call("GET", "/api/reviews", { cookie: life })).data;
  assert.equal(r.items.length, 0); assert.equal(r.mine, true);
  sqlite.prepare("UPDATE reviews SET status = 'approved'").run();
  r = (await call("GET", "/api/reviews")).data;
  assert.equal(r.items.length, 1); assert.equal(r.items[0].name, "Jamie"); assert.equal(r.mine, false);
  await call("POST", "/api/reviews", { cookie: life, body: { rating: 4, text: "Edited: still great", name: "Jamie" } });
  assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM reviews").get().n, 1);
  assert.equal((await call("GET", "/api/reviews")).data.items.length, 0, "edited review goes back to pending");
});
await test("sign-in attempts are rate limited per IP", async () => {
  let last;
  for (let i = 0; i < 11; i++) last = await call("POST", "/api/session", { body: { key: "NOPE-NOPE-NOPE-NOPE-1" }, ip: "203.0.113.9" });
  assert.equal(last.status, 429);
});
await test("expired monthly licence is caught on the 6-hourly re-check", async () => {
  sqlite.prepare("UPDATE sessions SET checked_at = checked_at - 30000").run();
  LICENSES["MONTHLY-KEY-0000-00002"] = { valid: false, license_key: { status: "expired" }, meta: {} };
  const r = await call("GET", "/api/download", { cookie: month });
  assert.equal(r.status, 401); assert.match(r.data.error, /expired/);
  assert.equal((await call("GET", "/api/me", { cookie: month })).status, 401, "session removed");
  assert.equal((await call("GET", "/api/me", { cookie: life })).status, 200, "lifetime still fine");
});
await test("sign out removes the session and clears the cookie", async () => {
  const r = await call("DELETE", "/api/session", { cookie: life });
  assert.match(r.headers.get("Set-Cookie"), /Max-Age=0/);
  assert.equal((await call("GET", "/api/me", { cookie: life })).status, 401);
});
await test("responses carry security headers; unknown routes are 404", async () => {
  const r = await call("GET", "/api/nope");
  assert.equal(r.status, 404);
  assert.equal(r.headers.get("X-Content-Type-Options"), "nosniff");
  assert.equal(r.headers.get("Cache-Control"), "no-store");
  assert.match(r.headers.get("Content-Security-Policy"), /frame-ancestors 'none'/);
});
console.log(`\n${passed} tests passed`);
