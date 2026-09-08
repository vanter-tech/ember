# Report 407 — Landing: move the contact endpoint to a Worker; wire the Turnstile site key

## 1. Identification
- **Report number:** 407
- **Current Task:** Make `/api/contact` run on the actual deploy target and
  bake in the real Turnstile site key
- **Predecessor Task:** Report 406 — Landing: full-width contact form + Turnstile fix

## 2. Objective
`ember.vanter.net` is a **Cloudflare Worker with static assets** (`landing/
wrangler.jsonc`: `name: "ember"`, `assets.directory: "./dist"`, no `main`),
deployed by Workers Builds from GitHub `/landing` — **not** Cloudflare
Pages. The `functions/api/contact.ts` from report 405 is a Pages Functions
convention and would never run on this target; `/api/contact` would 404.
Replace it with a real Worker entry, and set the Turnstile site key now that
the widget exists.

## 3. Modified Files
- `landing/worker/index.ts` — new (Worker entry)
- `landing/wrangler.jsonc`
- `landing/functions/api/contact.ts` — deleted (dir removed)
- `landing/src/components/ContactForm.tsx`
- `PROGRESS.md`
- `reports/407-landing-contact-worker.md` — new

## 4. What Changed?
- **`worker/index.ts`** — `export default { fetch(request, env) }`:
  - `POST /api/contact` → the same handler as before (honeypot → field
    validation → Turnstile `siteverify` → Resend send; `500` when
    `TURNSTILE_SECRET_KEY` / `RESEND_API_KEY` / `CONTACT_TO` are unset).
  - `GET/other /api/contact` → `405`.
  - everything else → `env.ASSETS.fetch(request)` (the Astro `dist/` build;
    `not_found_handling: "404-page"` covers unknown paths).
  - Self-typed, no `@cloudflare/workers-types` dependency.
- **`wrangler.jsonc`** — added `"main": "./worker/index.ts"` and
  `"assets".binding: "ASSETS"` so the Worker can serve the static build.
  Verified with `npx wrangler deploy --dry-run` (bundles, sees `env.ASSETS`,
  reads 89 asset files).
- **`functions/`** — deleted; it was dead on this deploy target.
- **`ContactForm.tsx`** — `SITE_KEY` now defaults to the real Ember Turnstile
  site key `0x4AAAAAAEsaAHQ6XDMni_IM` (public — it ships in the HTML anyway),
  still overridable via `PUBLIC_TURNSTILE_SITE_KEY`. The widget therefore
  always renders, with `appearance: 'interaction-only'` so most visitors see
  nothing.

## 5. Why It Changed?
- Workers Static Assets does not auto-route a `functions/` directory — that
  is a Pages-only feature. A single Worker script that special-cases the one
  dynamic route and delegates the rest to `ASSETS` is the minimal correct
  shape for this deploy.
- The site key is not a secret; hard-coding it (with an env override) means
  no build-time variable to manage in Workers Builds. Only the two secrets
  and `CONTACT_TO` need dashboard configuration.

## Owner action — Cloudflare dashboard → Workers & Pages → `ember` → Settings → Variables and Secrets
| Name | Type | Value |
|---|---|---|
| `TURNSTILE_SECRET_KEY` | Secret | secret key of the Turnstile widget |
| `RESEND_API_KEY` | Secret | API key from resend.com (Sending access) |
| `CONTACT_TO` | Text | inbox that should receive the messages |

(Or `npx wrangler secret put <NAME>` from `landing/` for the two secrets.)
These persist across Workers Builds deploys. No build variable is needed.
Until all three exist, `POST /api/contact` returns `500` and the form shows
its generic error.

## Verification
- `cd landing && pnpm run build` — clean, 26 pages.
- `npx wrangler deploy --dry-run` — Worker bundles; `env.ASSETS` binding
  present; 89 files read from `dist/`.
- `ContactForm` bundle now contains the site key + `interaction-only` + the
  Turnstile script URL.
- Not exercised: a live submission (needs the three runtime vars on the
  deployed Worker).
