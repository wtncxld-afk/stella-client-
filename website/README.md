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
