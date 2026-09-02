# Report 329 — LSEO-05: swap the 105 KB .ico logos for 3 KB PNGs

## 1. Identification
- **Report number:** 329
- **Task ID:** LSEO-05 (follow-up — the "shrink the ~105 KB `ember-logo-l/d.ico`"
  item left to the user in report 328)
- **Predecessor task:** report 328 (LSEO-05 footer-logo-update)

## 2. Objective
The nav and footer rendered `ember-logo-l.ico` / `ember-logo-d.ico` as `<img>`
elements — 105 902 bytes **each**, an oversized legacy format that competed for
bandwidth during mobile first paint (PSI mobile: FCP 2.0 s / LCP 2.7 s, both
marginal amber). The user produced lightweight PNG replacements. Wire them in and
drop the dead `.ico` assets.

## 3. Modified Files
- `landing/src/components/Nav.astro`
- `landing/src/components/Footer.astro`
- `landing/src/layouts/Layout.astro`
- `landing/public/ember-logo-l.png` (new, 3 359 B — added by the user)
- `landing/public/ember-logo-d.png` (new, 2 638 B — added by the user)
- `landing/public/ember-logo-l.ico` (deleted, 105 902 B)
- `landing/public/ember-logo-d.ico` (deleted, 105 902 B)

## 4. What Changed?
- **`Nav.astro`** / **`Footer.astro`** — the two logo `<img src>` values changed
  from `/ember-logo-{l,d}.ico` to `/ember-logo-{l,d}.png`. Nothing else touched:
  the new PNGs are **142 × 180**, identical to the existing `width`/`height`
  attributes, so the reserved aspect-ratio box (and CLS) is unchanged; the
  `.site-logo--light|--dark` theme swap and all classes stay as-is.
- **`Layout.astro`** — the favicon `<link rel="icon" href="/ember-logo-l.ico"
  sizes="any">` (the same 105 KB file) replaced with the standard pair already
  present in `public/`: `<link rel="icon" href="/favicon.ico" sizes="32x32">` +
  `<link rel="icon" href="/favicon.svg" type="image/svg+xml">` (3 601 B). No
  change needed to `site.webmanifest` — it already points at `favicon.svg` /
  `favicon.ico` / `apple-touch-icon.png` / `icon-192` / `icon-512`.
- Deleted both `ember-logo-*.ico` files: after the edits nothing references them
  (`grep` across `src/` clean), and leaving them in `public/` would still ship
  them into `dist/`.

## 5. Why It Changed?
~212 KB of icon payload (2 × 105 KB) on every page load, for a mark that displays
at 32 px, is pure waste and the most likely contributor to the amber mobile
FCP/LCP. PNG at the real source resolution is ~3 KB each — a ~97 % cut — with no
visual or layout change. `.ico` only ever made sense as a favicon container, not
as a rendered page image.

## 6. Verification
- `cd landing && pnpm run build` — 20 pages, sitemap emitted, no errors.
- `dist/index.html`: 2 × `ember-logo-l.png` + 2 × `ember-logo-d.png` (nav +
  footer), 1 × `favicon.ico` + 1 × `favicon.svg`; `grep -rn "ember-logo.*\.ico"
  dist/` → no matches.
- `dist/` contains `ember-logo-l.png`, `ember-logo-d.png`, `favicon.ico`,
  `favicon.svg`; the `.ico` logos are gone from the build.
- Landing-only change; backend/frontend suites unaffected.
- **User to re-run PSI** on `https://ember.vanter.net/` to confirm FCP/LCP move
  into the green now that the icon payload is gone.
