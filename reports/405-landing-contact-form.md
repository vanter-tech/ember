# Report 405 — Landing: real contact form on `/contacto`

## 1. Identification
- **Report number:** 405
- **Current Task:** Landing nice-to-have #3 — working contact form (Pages
  Function + Resend + Turnstile + `/gracias`)
- **Predecessor Task:** Report 404 — Landing: security page `/info/seguridad`

## 2. Objective
`/contacto` only offered `mailto:` links. Add a real form that submits to a
Cloudflare Pages Function, is protected by Turnstile, delivers the message
by email through Resend, and confirms on a dedicated `/gracias` page.

## 3. Modified Files
- `landing/functions/api/contact.ts` — new (Pages Function)
- `landing/src/components/ContactForm.tsx` — new (React island)
- `landing/src/pages/gracias.astro` — new
- `landing/src/pages/en/gracias.astro` — new (thin re-export)
- `landing/src/pages/contacto.astro`
- `landing/src/i18n/ui.ts`
- `PROGRESS.md`
- `reports/405-landing-contact-form.md` — new

## 4. What Changed?
- **`functions/api/contact.ts`** — `onRequestPost` handler at `/api/contact`:
  1. Parse JSON `{name,email,message,company,turnstileToken}`.
  2. Honeypot: if `company` is non-empty, return `204` without sending.
  3. Validate: name (1–120), email (regex, ≤200), message (5–4000).
  4. If `TURNSTILE_SECRET_KEY` / `RESEND_API_KEY` / `CONTACT_TO` are unset →
     `500 not_configured` (fails loudly; never silently drops a message).
  5. Verify the Turnstile token against `siteverify` (with `CF-Connecting-IP`).
  6. Send via Resend API — `from: Ember <onboarding@resend.dev>`,
     `to: CONTACT_TO`, `reply_to:` the visitor's address.
  7. `204` on success; `4xx/5xx` with an `{error}` code otherwise.
- **`ContactForm.tsx`** — React island in the current soft design (no leftover
  brutalist styling from the abandoned `feat/hosted-production-deployment`
  branch). Fields: nombre, correo, mensaje, a visually-hidden honeypot, and a
  Turnstile widget rendered explicitly after injecting
  `challenges.cloudflare.com/turnstile/v0/api.js` once. Client-side
  validation mirrors the server; on success `window.location` goes to the
  localized `/gracias`; on failure the Turnstile widget is reset and an
  inline error shown. Site key from `PUBLIC_TURNSTILE_SITE_KEY`, falling back
  to Cloudflare's always-passing test key `1x00000000000000000000AA` when
  unset (local dev / unconfigured preview).
- **`contacto.astro`** — form card inserted right after the lede as the
  primary action (`<ContactForm client:visible lang={lang} />`); the existing
  mail/office/hours cards stay below under a new "Otras formas de contacto"
  heading. The 20-minute-demo box is unchanged.
- **`gracias.astro` (+ `/en/gracias`)** — thank-you page in `Layout`, matching
  the site style.
- **`i18n/ui.ts`** — +20 keys per locale (`cform.*`, `gracias.*`,
  `cpage.form.*`, `cpage.channels.title`). ES/EN parity 445/445.

## 5. Why It Changed?
- **Resend over a chat webhook or a hosted form service:** it lands in a
  normal inbox (what a business wants), is free to 100/day, and needs no
  domain/DNS setup — `onboarding@resend.dev` is an allowed sender out of the
  box. A Slack/Discord webhook would force the user to own that workspace; a
  hosted form service (Formspree etc.) would send visitor PII to a third
  party and complicate Turnstile.
- **Turnstile over a plain honeypot:** a public contact form is a spam
  magnet; Turnstile is free, privacy-friendly, and invisible in most cases.
  The honeypot is kept as a cheap extra layer.
- **Fail loudly when unconfigured:** the abandoned prior function returned
  `204` even with no destination, silently dropping messages. This one
  returns `500` until the env vars exist, so a misconfigured deploy is
  obvious.
- **Reused the prior function/form *logic*, not its styling:** the
  `feat/hosted-production-deployment` components were written against an old
  brutalist design system that no longer exists on the site.

## One-time configuration (Cloudflare Pages → Settings → Environment variables)
The form returns an error until these are set on the **Production** (and
**Preview**, if desired) environment:

| Variable | Value | Notes |
|---|---|---|
| `RESEND_API_KEY` | API key from resend.com (free signup → API Keys) | secret; server-only |
| `TURNSTILE_SECRET_KEY` | secret key of the Turnstile widget (CF dash → Turnstile) | secret; server-only |
| `PUBLIC_TURNSTILE_SITE_KEY` | site key of the **same** Turnstile widget | plain; inlined into the client bundle at build — a redeploy is needed after setting it |
| `CONTACT_TO` | inbox that should receive the messages | plain |

Until `PUBLIC_TURNSTILE_SITE_KEY` is set, the widget uses Cloudflare's test
key and always passes; the server still rejects the submission because
`TURNSTILE_SECRET_KEY`/`RESEND_API_KEY`/`CONTACT_TO` are missing (`500`).

## Verification
- `cd landing && pnpm run build` — clean, **26 pages** (was 24); `/gracias`
  and `/en/gracias` emitted; the `ContactForm` island bundles and the
  `/contacto` HTML contains the server-rendered form.
- ES/EN i18n key parity: 445 / 445, no duplicates.
- Rendered spot-check: ES `/contacto` shows "Escribinos" + fields + the
  Turnstile mount; EN shows "Write to us"; `/gracias` renders in both
  locales.
- Not exercised in this task: a live end-to-end submission (needs the four
  env vars on a real Cloudflare Pages deploy).
