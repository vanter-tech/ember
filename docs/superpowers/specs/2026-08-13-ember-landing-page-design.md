# Ember Landing Page — Design Spec

**Date:** 2026-08-13
**Status:** Approved design, task breakdown deferred (see Next Steps)

## Overview

A public marketing site for Ember — Hero, Features, Pricing, CTA, Nav, Footer — built as a new
standalone package, `ember/landing/`, sibling to `backend/` and `frontend/`. Its only job is
converting cold traffic (Google, ads, word of mouth) into sign-ups; it links out to the existing
`frontend` app for anything requiring auth or backend data.

## Scope

**In scope:** Nav, Hero, Features, Pricing (display only), CTA band, Footer — a single-page
marketing site.

**Out of scope (deliberately, not oversight):**
- Tenant onboarding (`/register`, `/login`) — already built in `frontend/` (task-4.3), stays
  there. It's tightly coupled to auth/session/API logic that has no reason to move.
- Per-tenant landing (`/t/:slug`) — already built in `frontend/` (task-4.3, `TenantLanding.tsx`),
  stays there.
- Testimonials, FAQ, blog — no customers to quote yet; easy to add later without restructuring.
- Any backend integration — `landing/` never calls the Ember API. Zero coupling by design.

## Why a separate package (not inside `frontend/`)

`frontend/` is a Vite CSR SPA — right for an authenticated app, wrong for a page whose entire job
is SEO and fast first paint for cold traffic. `frontend/`'s build already warned about bundle size
(677 kB) before this existed; stacking marketing content into the same bundle would make that
worse and couple marketing-copy edits to the app's full build/test/deploy cycle for no reason.

A fully separate git repo was considered and rejected for now: the real need is *technical*
separation (own build tool, own bundle, own deploy target), which a sibling package inside this
monorepo already provides, without paying for a second `CLAUDE.md`/`PROGRESS.md`/reports workflow.
Cheap to extract into its own repo later (`git subtree`/`git filter-repo`) if ever needed (e.g.
handing marketing-only access to a contractor).

## Package & Tooling

- New `ember/landing/` — standalone Astro project, own `package.json`/lockfile, **not** part of
  any shared workspace with `frontend/`. Scaffolded via `pnpm create astro@latest`.
- **Tailwind CSS 4** via Astro's Vite integration — same major version as `frontend/` for a
  familiar authoring model, but its own separate theme/config, not shared code (these are
  independently deployed, and brutalism is a deliberate visual break from the app's shadcn look).
- **`@astrojs/react`** integration, used sparingly — only for genuinely interactive bits (mobile
  nav toggle, possibly a pricing-period switch). No `shadcn/ui`/Radix — that dependency tree exists
  to support soft, rounded, animated components, the opposite of this page's style. Landing gets
  its own small set of plain `.astro` components.
- **Dev server on port 5174** (`frontend/`'s dev server already owns 5173 — set explicitly in
  `astro.config.mjs`'s `server.port` so both can run side by side during development).
- **Deployment**: static host (Vercel/Netlify/Cloudflare Pages — provider choice deferred, doesn't
  affect the design). No Dockerfile, no `docker-compose.yml` entry — a static `astro build` output
  has no business running in a container next to the database.

## Visual Design System (Brutalist)

- **Palette**: stark black (`#0a0a0a`) and off-white (`#f5f5f0`) as the base — no grays, no
  gradients. Ember's existing brand red (`#920703`, already the accent throughout `frontend/`) as
  the one loud accent color, used as solid fills, never tints. Keeps brand continuity despite the
  stylistic break from the app's soft look.
- **Borders & shadows**: thick solid black borders (3–4px) on cards, buttons, section dividers.
  Hard offset shadows, no blur (`6px 6px 0 #000`) — the "cutout/sticker" look. Zero border-radius
  anywhere — deliberate contrast to the app's `rounded-3xl`.
- **Typography**: reuse the `Inter`/`Geist` `@fontsource` packages already in `frontend/` (no new
  font dependency), pushed hard — huge, black-weight (900) headlines, tight line-height. A
  monospace system-font stack for small meta text (nav items, eyebrow labels, pricing fine print).
- **Layout**: asymmetric, blocky sections with exposed thick horizontal rules between them instead
  of soft transitions/gradients. Generous whitespace inside blocks, abrupt boundaries between them.
- **Buttons/CTAs**: solid red or black fill, thick contrasting border, hard offset shadow,
  uppercase bold label, no rounding.
- Single fixed theme — no dark-mode toggle for a marketing page.

## Page Structure

1. **Nav** — bold "EMBER" wordmark, in-page links to Features/Pricing, "Iniciar sesión" /
   "Registrarme" buttons (copy matches `TenantLanding.tsx`'s existing Spanish) linking out to
   `frontend`. Mobile hamburger toggle (the one React island).
2. **Hero** — oversized headline + subheadline, one primary CTA ("Registrarme" →
   `frontend`'s `/register`), optionally a bold-bordered product screenshot as a visual anchor.
   Copy is placeholder pending real marketing content.
3. **Features** — blocky grid (3–6 cards) covering the product's actual pillars per `CLAUDE.md`'s
   vision: real-time collaborative cart, KDS, waiter/floor management, admin analytics. Icon +
   bold short headline + 1–2 line description each.
4. **Pricing** — four cards matching `RestaurantPlan` exactly (`FREE`/`STARTER`/`PRO`/
   `ENTERPRISE`) for naming consistency, though this is static marketing content with no backend
   wiring. Name, price (placeholder), feature bullets, CTA. `ENTERPRISE`'s CTA is "Contáctanos"
   (`mailto:`), not self-serve register.
5. **CTA band** — one final full-width, high-contrast section before the footer.
6. **Footer** — wordmark, copyright, placeholder Privacy/Terms links, contact email.

## Integration & Error Handling

- **Zero backend coupling** — no fetch to the Ember API anywhere in this package.
- Cross-linking via `PUBLIC_APP_URL` (Astro's client-exposed env var convention) —
  `http://localhost:5173` in local dev, the real app domain in production. A small `src/config.ts`
  exports it plus helpers (`registerUrl()`, `loginUrl()`) so link construction lives in one place.
- **Error handling**: a `src/pages/404.astro` for unmatched routes. No forms, no API calls — no
  other failure mode exists. `ENTERPRISE`'s "Contáctanos" is a plain `mailto:` link, not a form.

## Testing & Verification

- `astro build` is the primary gate — fails on broken imports/type errors, the same role `tsc -b`
  plays for `frontend/`.
- No unit-test framework for v1 — no business logic exists here worth testing (no state, no
  calculations, just markup/content).
- Manual verification: `astro dev` (port 5174) locally, click through Nav/CTA links to confirm
  they resolve against the running `frontend` dev server (port 5173).

## Open / Deferred Decisions

- Real marketing copy (Hero headline/subheadline, feature descriptions, pricing numbers) — the
  user's to write; implementation will use placeholder text.
- Static-host provider choice (Vercel vs. Netlify vs. Cloudflare Pages) — doesn't affect the
  design, decide at deploy time.
- Contact email address for `ENTERPRISE`'s CTA and footer.

## Next Steps

Task breakdown into `PROGRESS.md` (nomenclature `EMB-LP-XX`, matching the `EMB-PC-XX` precedent
set for the Platform Console) is **intentionally deferred** — the user wants to revise this spec
first. Do not append `EMB-LP` tasks to `PROGRESS.md` until they confirm the spec is final.
