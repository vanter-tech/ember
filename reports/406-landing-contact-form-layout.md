# Report 406 — Landing: full-width contact form + Turnstile test-widget fix

## 1. Identification
- **Report number:** 406
- **Current Task:** Contact-form review feedback (follow-up to report 405)
- **Predecessor Task:** Report 405 — Landing: real contact form on `/contacto`

## 2. Objective
After seeing the deployed form: it was capped at `max-w-xl`, leaving a large
empty column to its right, and it showed Cloudflare's visible "testing only"
Turnstile box because no real site key is configured yet. Make the form
section span the full page width (keeping the mail / office / hours cards
below as they were) and stop rendering the test widget.

## 3. Modified Files
- `landing/src/pages/contacto.astro`
- `landing/src/components/ContactForm.tsx`
- `PROGRESS.md`
- `reports/406-landing-contact-form-layout.md` — new

## 4. What Changed?
- **`contacto.astro`** — removed the `max-w-xl` cap on the form `<section>`
  so it spans the page container (`max-w-6xl`), the same width as everything
  else on the page. The "Otras formas de contacto" heading and the
  three-card mail / office / hours grid below it are unchanged, and so is
  the 20-minute-demo box.
- **`ContactForm.tsx`**:
  - Name and email now sit in a `sm:grid-cols-2` row (they no longer look
    stranded on a full-width card); message spans full width below.
  - `SITE_KEY` reads `PUBLIC_TURNSTILE_SITE_KEY` with **no test-key
    fallback**. `HAS_CAPTCHA = Boolean(SITE_KEY)` gates the script load, the
    widget element and the client-side token check. With the var unset the
    whole Turnstile branch is dead-code-eliminated at build — no widget, no
    "testing only" box.
  - When a real key is present the widget renders with
    `appearance: 'interaction-only'`, so a challenge box only appears if
    Cloudflare actually needs one.

## 5. Why It Changed?
- The `max-w-xl` cap was the cause of the empty right-hand space; removing
  it lets the form use the page width. The contact-info cards stay where
  they were.
- The visible box was Cloudflare's **test site key**
  (`1x00000000000000000000AA`), which always renders a "testing only"
  widget. Since the server already returns `500` until its secret is
  configured, a fake widget in the meantime only looked broken — better to
  show nothing until the real key exists.

## Owner action (unchanged from report 405)
The form still needs these on Cloudflare Pages → Settings → Environment
variables before it can deliver, and the Turnstile widget only appears once
the first two are set and the site is redeployed:

| Variable | Value |
|---|---|
| `PUBLIC_TURNSTILE_SITE_KEY` | site key of a Turnstile widget (CF dash → Turnstile → add widget, free) |
| `TURNSTILE_SECRET_KEY` | secret key of that same widget |
| `RESEND_API_KEY` | API key from resend.com (free signup) |
| `CONTACT_TO` | inbox that receives the messages |

Recommended Turnstile widget mode: **Managed**. With
`appearance: interaction-only` most visitors will see nothing; only
suspicious sessions get a visible check.

## Verification
- `cd landing && pnpm run build` — clean, 26 pages (unchanged).
- Built `/contacto` HTML: form `<section>` no longer `max-w-xl`; the three
  info cards and "Otras formas de contacto" heading still present;
  `sm:grid-cols-2` name/email row present; no Turnstile script or widget
  element emitted (site key unset). ES and EN both render.
- ES/EN i18n key parity: 445 / 445.
