# Stella Client website

A static website (no server code, no build step): `index.html`, `assets/site.css`, `assets/site.js`
and `assets/modules.js`. Pages: home (live ClickGUI demo, features, gallery, pricing, FAQ),
`#updates` (update log), `#terms` (Terms and Conditions) and `#cookies` (Cookie Policy).

## Before going live

1. **Checkout links.** Open `assets/site.js` and fill in `CONFIG.checkout.monthly` (€8 / month)
   and `CONFIG.checkout.lifetime` (€15). Until then the buttons show "Checkout isn't connected yet".
   Only `https` links on the hosts in `allowedCheckoutHosts` are accepted.
   A *merchant of record* (Lemon Squeezy, Paddle or Tebex) is the easiest choice: it charges EU VAT,
   handles the withdrawal-right consent at checkout, invoices and refunds for you.
2. **Your details.** Fill in `owner`, `email` and `country` in the same `CONFIG`, and the contact
   line in `.well-known/security.txt`. They appear in the Terms and Cookie Policy.
3. **Legal review.** The Terms and Cookie Policy are a solid starting point written for this site,
   not legal advice. Have them checked for your country before you sell anything.
4. **Distribution.** Paying customers need somewhere to download Stella that non-buyers can't reach
   (for example the payment provider's file delivery, or a private release), plus a licence check in
   the client if you want to stop sharing. Right now the GitHub releases are public.
5. **Minecraft rules.** Read Minecraft's Usage Guidelines (minecraft.net/usage-guidelines) on charging
   for mods before you launch paid plans.

## Hosting (with the security headers)

Use **Cloudflare Pages** or **Netlify** (both free): create a project from this repository,
set the root / publish directory to `website`, no build command. Both read `_headers`, which sets
the Content Security Policy, HSTS, frame blocking and the other security headers.
GitHub Pages works too but can't send those headers (the CSP in `index.html` still applies).

## Security

- No third-party code: no CDNs, analytics, trackers or external fonts. Everything is served from the site.
- Strict Content Security Policy: scripts, styles, fonts and images only from the site itself,
  no inline scripts or styles, no plugins, no forms, no framing (`frame-ancestors 'none'` via `_headers`).
- Trusted Types enforced: the script never uses `innerHTML`, so script injection through the DOM is blocked.
- Checkout buttons only open `https` URLs on known payment hosts; the site never handles card data.
- No cookies or storage, so there's nothing to consent to and nothing to leak.

## Updating the ClickGUI demo

`assets/modules.js` is generated from the client's source code:

```
python3 website/tools/extract_modules.py src/main/java/dev/stella/client website/assets/modules.js
```

Then update the module / setting counts in `index.html` if they changed.

## Buyer dashboard (`#dashboard`)

Buyers sign in with the licence key from their purchase email and get: the download (with its SHA-256),
their plan and a link to manage it, install steps, what's new, feature suggestions with voting, and reviews.
Approved reviews also appear on the home page; until there are some, that section stays hidden.

It needs the small backend in `functions/api/[[route]].js` (Cloudflare Pages Functions) and **Lemon Squeezy**
for payments and licence keys. Without it the dashboard says it isn't connected and offers a labelled example.

### Set-up

1. **Lemon Squeezy:** create one product, "Stella Client", with two variants: *Monthly* (€8, subscription) and
   *Lifetime* (€15, one-time). Turn on **license keys** for both. Set the button after purchase to
   `https://your-site/#dashboard`. Note the store ID, product ID and both variant IDs. Put the two checkout links
   in `assets/site.js`.
2. **Cloudflare Pages** (project root: `website`):
   - D1 database `stella`: `npx wrangler d1 execute stella --remote --file=website/backend/schema.sql`,
     then bind it to the project as `DB`.
   - R2 bucket `stella-releases`: upload the jar as `stella.jar`, bind it as `RELEASES`.
   - Variables: `LS_STORE_ID`, `LS_PRODUCT_ID`, `LS_VARIANT_MONTHLY`, `LS_VARIANT_LIFETIME`, `RELEASE_KEY=stella.jar`,
     `RELEASE_VERSION`, `RELEASE_SHA256`, `PORTAL_URL` (your Lemon Squeezy customer portal link).
   - Secrets: `SESSION_KEY` (`openssl rand -base64 32`) and `PEPPER` (`openssl rand -hex 32`).
3. **Each release:** upload the new jar to R2 as `stella.jar` and update `RELEASE_VERSION` and `RELEASE_SHA256`.
4. **Reviews** wait for you to approve them (D1 console):
   `SELECT id, rating, name, text FROM reviews WHERE status = 'pending';` then
   `UPDATE reviews SET status = 'approved' WHERE id = 1;`
   Hide a suggestion with `UPDATE suggestions SET status = 'hidden' WHERE id = 1;` (or mark it `planned` / `done`).

### How it's protected

- Licence keys are checked with Lemon Squeezy at sign-in and again every 6 hours, so an ended Monthly plan loses access.
- Sessions: a random 256-bit token in a `__Host-` cookie (Secure, HttpOnly, SameSite=Strict, 7 days); only its hash
  is stored. The licence key is stored encrypted (AES-GCM).
- Every change must come from this site (Origin check plus a custom header), inputs are length-checked and
  cleaned, and sign-in, suggestions, votes and reviews are rate-limited.
- The jar is only served to a valid, signed-in licence.

### Test it locally

`node --no-warnings website/backend/test-api.mjs` runs the API tests (real SQLite, mocked Lemon Squeezy).
`node --no-warnings website/backend/dev-server.mjs` serves the site with the API at
http://localhost:8766/#dashboard using the test keys `LIFETIME-TEST-KEY-0001` and `MONTHLY-TEST-KEY-00002`.
