# Report 308 — landing-real-brand-assets

## 1. Identification
- **Report number:** 308
- **Task ID:** landing-real-brand-assets (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 307 (landing-real-company-info)

## 2. Objective
Wire the real Ember brand art (maintainer-supplied) into the landing site:
favicon, Apple touch icon, Open Graph image, and the header/footer logo mark.

## 3. Modified Files
- `landing/src/assets/Ember_logo.svg` — **new** (source, maintainer-supplied: solid maroon flame)
- `landing/src/assets/ember-apple-touch-icon.png` — **new** (source, 180×180)
- `landing/src/assets/ember-og-image.png` — **new** (source, 1200×630)
- `landing/src/components/Flame.astro` — **new** (inline flame `<svg>`, `fill="currentColor"`)
- `landing/public/favicon.svg` — regenerated from `Ember_logo.svg`
- `landing/public/favicon.ico` — regenerated (16/32/48)
- `landing/public/apple-touch-icon.png` — real art
- `landing/public/og-image.png` — real art
- `landing/public/icon-192.png`, `landing/public/icon-512.png` — **new** (PWA)
- `landing/public/site.webmanifest` — icon list expanded
- `landing/src/components/Nav.astro` — logo mark → `<Flame />`
- `landing/src/components/Footer.astro` — added matching logo chip

## 4. What Changed?
The maintainer added three source files under `src/assets/`. `<head>` `<link>`s
and the `og:image` meta need the files at fixed `public/` paths, so a one-off
Node script (`sharp`, already a dep) produced the `public/` set:

- **`favicon.svg`** — the `Ember_logo.svg` path re-wrapped in a square viewBox
  (`-177 0 1181 1181`) so the portrait flame isn't letterboxed in the tab, with
  an inline `<style>`: `fill:#8c1717`, and `@media (prefers-color-scheme: dark)`
  → `#e8a9a9` so it stays legible on a dark browser chrome.
- **`favicon.ico`** — 16/32/48 PNG-encoded entries assembled from
  `ember-apple-touch-icon.png` (maroon square + white flame), so the non-SVG
  fallback matches the touch icon.
- **`apple-touch-icon.png`** / **`og-image.png`** — the real art, alpha channel
  stripped (`removeAlpha`; both were already fully opaque).
- **`icon-192.png`** / **`icon-512.png`** — downscaled from the touch icon for
  Android / PWA install; added to `site.webmanifest` alongside `favicon.ico`.

**Logo mark.** New `Flame.astro` renders the `Ember_logo.svg` path inline with
`fill="currentColor"`. `Nav.astro`'s brand chip swapped its old hand-drawn flame
`<path>` for `<Flame class="h-4 w-auto" />` (white flame on the existing
`bg-primary` rounded square — option A). `Footer.astro`'s text-only "Ember"
wordmark gained the same chip for consistency.

`Layout.astro` untouched — the `public/` filenames are unchanged, so the existing
`<link>`s already point at the new files.

## 5. Why It Changed?
Report 307 left the favicon / touch icon / OG image as placeholder art pending
real files. Maintainer supplied them and picked **option A** for the header/footer
logo (keep the maroon square chip, flame in white).

## Verification
- `cd landing && pnpm build` — green, 20 pages; `dist/` contains `favicon.svg`,
  `favicon.ico`, `apple-touch-icon.png`, `og-image.png`, `icon-192.png`,
  `icon-512.png`, `site.webmanifest`.
- `curl` on the preview: `/og-image.png` → `200 image/png`, `/favicon.ico` →
  `200 image/x-icon`.
- ICO structure probed: magic `0/1`, count 3, entries 16/32/48 with valid
  offsets.
- Browser: header + footer show the maroon chip with the new white flame mark;
  favicon renders as the maroon flame; rest of the page unchanged.
- No file outside `landing/` touched → backend/frontend suites not re-run.
