# Report 282 — HPD-10: landing real contact submit + production `site:`

## 1. Identification
- **Report number:** 282
- **Current Task ID:** HPD-10
- **Predecessor Task:** HPD-09 (report 281 — Cloudflare Pages `env-config.js` generator)
- **Branch:** `feat/hosted-production-deployment`

## 2. Objective
Turn the landing site's contact form into a real submission path and point the
site metadata at the production `.net` domain. The form previously faked success
with a 900 ms timeout; it now POSTs to a same-origin Cloudflare Pages Function
that validates and forwards the message to a webhook.

## 3. Modified Files
- `landing/src/components/ContactForm.tsx` (modified)
- `landing/functions/api/contact.ts` (new)
- `landing/astro.config.mjs` (modified)
- `reports/282-hpd-10-landing-contact.md` (new)
- `PROGRESS.md` (updated)

## 4. What Changed?
- **`ContactForm.tsx`** — in `handleSubmit`, replaced
  `await new Promise((resolve) => setTimeout(resolve, 900))` with a real
  `fetch('/api/contact', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, email, message }) })`
  (all three values `.trim()`-ed). A non-`ok` response throws, which the existing
  `catch` turns into the visible `errors.form` message; a success still redirects
  to `/thank-you`. Client-side `validate()` and all field markup are unchanged.
- **`landing/functions/api/contact.ts`** — new Cloudflare Pages Function.
  `onRequestPost` parses the JSON body (`400 bad json` on parse failure), trims
  `name`/`email`/`message`, validates non-empty name + message and `EMAIL_RE`
  on the email (`400 invalid`), and — only when `env.CONTACT_WEBHOOK_URL` is set
  — POSTs a plain-text `{ text: "Ember contacto\nNombre: …\nCorreo: …\n\n…" }`
  payload to that webhook. Returns `204` on success. `Env.CONTACT_WEBHOOK_URL` is
  optional so builds/previews without the var still return `204`.
- **`astro.config.mjs`** — `site: 'https://ember.vanter.com'` →
  `site: 'https://ember.vanter.net'` (drives canonical URLs + the sitemap).

## 5. Why It Changed?
The hosted-production SKU serves the marketing site from Cloudflare Pages at
`ember.vanter.net`, so the `site:` must match for correct canonical/sitemap URLs.
A static Pages upload has no backend, so the contact submission is handled by a
same-origin Pages Function (`/functions/api/*` → `/api/*`) rather than calling the
Ember API across origins — keeping the landing package fully standalone. The
webhook target is a Pages project env var (`CONTACT_WEBHOOK_URL`) set by the
Cloudflare-track colleague, not committed here.

## 6. Verification
- `cd landing && pnpm build` → success, 5 pages built, `dist/` + `sitemap-index.xml`
  produced. Pages Functions under `landing/functions/` are deployed by Cloudflare,
  not bundled by Astro — the build ignores them, as expected.
- Backend/frontend/printing-agent suites untouched by this task (landing is a
  standalone Astro package).
