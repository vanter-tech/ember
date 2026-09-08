# Report 408 — Landing: keep `CONTACT_TO` in wrangler.jsonc; name missing vars

## 1. Identification
- **Report number:** 408
- **Current Task:** Contact form returns 500 `not_configured` after deploy
- **Predecessor Task:** Report 407 — Landing: contact endpoint moved to a Worker

## 2. Objective
Live testing of `/api/contact` on `ember.vanter.net` returned
`{"error":"not_configured"}` (500) even after the owner added
`TURNSTILE_SECRET_KEY`, `RESEND_API_KEY` and `CONTACT_TO` under the Worker's
runtime variables. Cause: `CONTACT_TO` was added as a **plaintext variable**
in the dashboard, and every Workers Builds deploy runs `wrangler deploy`,
which resets plaintext `vars` to exactly what `wrangler.jsonc` declares —
wiping `CONTACT_TO` on each deploy (the two Secrets survive). Put
`CONTACT_TO` in `wrangler.jsonc` so it is deploy-stable, and make the
`not_configured` response name which variables are actually missing.

## 3. Modified Files
- `landing/wrangler.jsonc`
- `landing/worker/index.ts`
- `PROGRESS.md`
- `reports/408-landing-contact-env-fix.md` — new

## 4. What Changed?
- **`wrangler.jsonc`** — added `"vars": { "CONTACT_TO":
  "tofernandoband01@outlook.com" }`. It is an email address, not a secret,
  and already appears in the repo (the `mailto:` links), so committing it
  adds no exposure. `wrangler deploy --dry-run` now shows
  `env.CONTACT_TO ("tofernandoband01@outlook.com")` as a bound variable.
- **`worker/index.ts`** — the config check now returns
  `{"error":"not_configured","missing":["…"]}` listing the absent
  variables, instead of a bare `not_configured`. The two secrets
  (`TURNSTILE_SECRET_KEY`, `RESEND_API_KEY`) are still set out of band and
  survive deploys; only they need dashboard/CLI configuration now.

## 5. Why It Changed?
- Plaintext `vars` set only in the dashboard are not durable for a
  Git-connected Worker — `wrangler deploy` is the source of truth for them.
  Secrets are the exception (they persist). Moving `CONTACT_TO` into
  `wrangler.jsonc` matches how the value should be managed.
- The opaque `not_configured` cost a long debugging loop; naming the missing
  keys makes the next misconfiguration a one-look fix.

## Owner action — now only two Secrets on the `ember` Worker
`ember` → Settings → **Variables and Secrets** (runtime), type **Secret**:

| Name | Value |
|---|---|
| `TURNSTILE_SECRET_KEY` | secret key of the Turnstile widget (site key `0x4AAAAAAE…`) |
| `RESEND_API_KEY` | `re_…` from resend.com |

Verify with `npx wrangler secret list` from `landing/` — both must be
listed. `CONTACT_TO` no longer needs a dashboard entry (delete the wiped
one if it lingers).

## Known separate issue — Turnstile error `600010`
On the owner's Edge/Opera the widget sometimes fails to execute the
challenge (`[Cloudflare Turnstile] Error: 600010`, alongside "No available
adapters" / WebGL warnings) — a degraded browser environment (hardware
acceleration off / VM / hardened privacy), not a site or config problem.
Where the widget does issue a token (it did in one Opera run) the form
proceeds. If it blocks the owner on their machine: enable hardware
acceleration or test elsewhere. Dropping Turnstile for the honeypot alone
is a possible product decision, tracked separately.

## Verification
- `cd landing && pnpm run build` — clean, 26 pages.
- `npx wrangler deploy --dry-run` — `env.CONTACT_TO` bound with the address;
  `env.ASSETS` present; worker bundles.
- Not exercised: a live 2xx submission (still needs the two Secrets on the
  deployed Worker and a browser where Turnstile issues a token).
