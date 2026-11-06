// Local preview of the website WITH the dashboard API (the real functions/api code), using SQLite for the
// database, a local folder for the download and MOCK licence keys instead of Lemon Squeezy.
// For testing only:  node --no-warnings website/backend/dev-server.mjs   then open http://localhost:8766/#dashboard
// Test keys:  LIFETIME-TEST-KEY-0001 (Lifetime)   MONTHLY-TEST-KEY-00002 (Monthly)
import { createServer } from "node:http";
import { DatabaseSync } from "node:sqlite";
import { readFileSync, writeFileSync, existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname, normalize, extname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const site = join(here, "..");
const tmp = mkdtempSync(join(tmpdir(), "stella-dev-"));
writeFileSync(join(tmp, "api.mjs"), readFileSync(join(site, "functions/api/[[route]].js")));
const { onRequest } = await import(pathToFileURL(join(tmp, "api.mjs")).href);

const sqlite = new DatabaseSync(":memory:");
sqlite.exec(readFileSync(join(here, "schema.sql"), "utf8"));
const DB = { prepare(sql) { let a = []; const st = sqlite.prepare(sql); return {
  bind(...x) { a = x.map(v => (v === undefined ? null : v)); return this; },
  async first() { return st.get(...a) ?? null; }, async all() { return { results: st.all(...a) }; },
  async run() { const r = st.run(...a); return { meta: { last_row_id: Number(r.lastInsertRowid), changes: r.changes } }; } }; } };
const env = { DB, RELEASES: { async get() { return { body: "This is a placeholder for the Stella jar (local preview).", size: 57 }; } },
  LS_STORE_ID: "1", LS_PRODUCT_ID: "2", LS_VARIANT_MONTHLY: "3", LS_VARIANT_LIFETIME: "4", RELEASE_KEY: "stella.jar",
  RELEASE_VERSION: "1.0.143", RELEASE_SHA256: "62f3077628ded5710f0e964b5428436c4bbe79605cff307df5a85d39c038443b",
  PORTAL_URL: "https://example.lemonsqueezy.com/billing", SESSION_KEY: Buffer.alloc(32, 3).toString("base64"), PEPPER: "dev" };
const MOCK = {
  "LIFETIME-TEST-KEY-0001": { valid: true, license_key: { status: "active" }, meta: { store_id: 1, product_id: 2, variant_id: 4, customer_email: "tester@example.com" } },
  "MONTHLY-TEST-KEY-00002": { valid: true, license_key: { status: "active", expires_at: "2026-12-06T10:00:00Z" }, meta: { store_id: 1, product_id: 2, variant_id: 3, customer_email: "monthly@example.com" } },
};
globalThis.fetch = async (_u, init) => new Response(JSON.stringify(MOCK[new URLSearchParams(init.body).get("license_key")] || { valid: false }));

const TYPES = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css", ".jpg": "image/jpeg", ".svg": "image/svg+xml", ".ttf": "font/ttf", ".txt": "text/plain" };
const port = Number(process.env.PORT || 8766);
createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  if (url.pathname.startsWith("/api/")) {
    const chunks = []; for await (const c of req) chunks.push(c);
    const headers = new Headers(); for (const [k, v] of Object.entries(req.headers)) if (typeof v === "string") headers.set(k, v);
    headers.set("CF-Connecting-IP", req.socket.remoteAddress || "127.0.0.1");
    const r = await onRequest({ request: new Request(url, { method: req.method, headers, body: chunks.length ? Buffer.concat(chunks) : undefined }), env });
    res.writeHead(r.status, Object.fromEntries(r.headers));
    res.end(Buffer.from(await r.arrayBuffer()));
    return;
  }
  const file = normalize(join(site, url.pathname === "/" ? "index.html" : decodeURIComponent(url.pathname)));
  if (!file.startsWith(site) || file.includes("/backend/") || file.includes("/functions/") || !existsSync(file)) { res.writeHead(404); res.end("Not found"); return; }
  res.writeHead(200, { "Content-Type": TYPES[extname(file)] || "application/octet-stream", "X-Content-Type-Options": "nosniff" });
  res.end(readFileSync(file));
}).listen(port, "127.0.0.1", () => console.log(`Stella preview: http://localhost:${port}/#dashboard  (test keys: LIFETIME-TEST-KEY-0001, MONTHLY-TEST-KEY-00002)`));
