-- Stella dashboard database (Cloudflare D1 / SQLite).
-- Apply once:  npx wrangler d1 execute stella --remote --file=website/backend/schema.sql

-- Signed-in dashboard sessions. Only a hash of the session token is stored, and the licence key
-- is kept encrypted (AES-GCM, key in the SESSION_KEY secret) so it can be re-checked with the store.
CREATE TABLE IF NOT EXISTS sessions (
  id_hash        TEXT PRIMARY KEY,
  license_hash   TEXT NOT NULL,
  license_enc    TEXT NOT NULL,
  plan           TEXT NOT NULL,          -- 'monthly' | 'lifetime'
  email_masked   TEXT NOT NULL,
  renews_at      TEXT,                   -- ISO date for monthly plans, NULL for lifetime
  expires_at     INTEGER NOT NULL,       -- unix seconds
  checked_at     INTEGER NOT NULL        -- last time the licence was re-validated
);
CREATE INDEX IF NOT EXISTS sessions_expiry ON sessions (expires_at);

-- Feature suggestions from buyers. status: 'open' | 'planned' | 'done' | 'hidden'
CREATE TABLE IF NOT EXISTS suggestions (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  license_hash  TEXT NOT NULL,
  title         TEXT NOT NULL,
  body          TEXT NOT NULL,
  status        TEXT NOT NULL DEFAULT 'open',
  created_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS votes (
  suggestion_id INTEGER NOT NULL,
  license_hash  TEXT NOT NULL,
  PRIMARY KEY (suggestion_id, license_hash)
);

-- Reviews: one per licence, shown only after you approve them.
-- Approve: UPDATE reviews SET status = 'approved' WHERE id = 1;
CREATE TABLE IF NOT EXISTS reviews (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  license_hash  TEXT NOT NULL UNIQUE,
  rating        INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
  text          TEXT NOT NULL,
  name          TEXT NOT NULL,
  status        TEXT NOT NULL DEFAULT 'pending',   -- 'pending' | 'approved' | 'hidden'
  created_at    INTEGER NOT NULL
);

-- Fixed-window rate limits per action and client.
CREATE TABLE IF NOT EXISTS rate_limits (
  bucket        TEXT PRIMARY KEY,
  window_start  INTEGER NOT NULL,
  count         INTEGER NOT NULL
);
