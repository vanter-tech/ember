# Report 302 — landing-dark-mode

## 1. Identification
- **Report number:** 302
- **Task ID:** landing-dark-mode (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 301 (landing-hero-centered)

## 2. Objective
Add a dark theme to the landing with a header toggle, using the palette proposed
in the previous answer (keeps the ember red as `--primary`, unlike the SaaS which
drops it in dark).

## 3. Modified Files
- `landing/src/styles/global.css` — dark token block on `:root[data-theme="dark"]`
- `landing/src/layouts/Layout.astro` — FOUC-prevention inline script in `<head>`
- `landing/src/components/Nav.astro` — theme-toggle button + icon CSS + toggle script
- `landing/src/components/TiltedShot.astro` — `ring-black/5` → `ring-border`;
  `brightness(0.9)` on the image in dark
- `landing/src/components/MobileNavDrawer.tsx` — backdrop `bg-foreground/50` →
  `bg-black/50` (foreground inverts in dark)

## 4. What Changed?
### Tokens (`global.css`)
The light `@theme` stays as the base. A new `:root[data-theme="dark"]` rule
redefines every `--color-*` var:

| token | dark |
|---|---|
| background | `oklch(0.16 0.008 40)` (warm near-black) |
| foreground | `oklch(0.96 0.004 60)` |
| card | `oklch(0.205 0.008 40)` |
| primary | `oklch(0.62 0.2 27)` (ember red, brightened) |
| primary-foreground | `oklch(0.99 0 0)` |
| secondary / accent | `oklch(0.27 0.006 40)` |
| muted | `oklch(0.23 0.006 40)` |
| muted-foreground | `oklch(0.71 0 0)` |
| destructive | `oklch(0.7 0.19 22)` |
| border | `oklch(1 0 0 / 0.10)` |
| input | `oklch(1 0 0 / 0.14)` |
| ring | `oklch(0.62 0.2 27 / 0.55)` |

`color-scheme` is set per theme. Everything else on the site is already
token-based, so no per-component dark overrides beyond the two noted below.

### Theme resolution + toggle
- **`Layout.astro`** runs a blocking `is:inline` script in `<head>` before paint:
  `document.documentElement.dataset.theme = localStorage['ember-theme'] ??
  (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')`. No FOUC.
- **`Nav.astro`** has a `#theme-toggle` button (a `size-9` icon button in the right
  group, visible at every breakpoint incl. mobile). It shows a moon in light and a
  sun in dark via scoped CSS keyed on `:root[data-theme="dark"]` — so the correct
  icon is right from first paint. Its click handler flips
  `document.documentElement.dataset.theme` and writes `localStorage['ember-theme']`.
- Because the head script always resolves to an explicit `data-theme`, the CSS
  needs only the single `[data-theme="dark"]` rule (no `@media` duplication). OS
  changes while the page is open are not live-followed — acceptable.

### Component touch-ups
- `TiltedShot` ring is now `ring-border` (token → subtle white in dark); the light
  app screenshot gets `filter: brightness(0.9) contrast(1.02)` under
  `:root[data-theme="dark"]` to tame the glare.
- **Floating chips keep the light palette.** They sit on top of a *light* app
  screenshot, so in dark mode a token-driven chip looked like a dark card on a
  light UI. `TiltedShot`'s `:global([data-float])` rule now re-declares the light
  `--color-card` / `--color-card-foreground` / `--color-primary` / `-foreground` /
  `--color-muted` / `--color-muted-foreground` / `--color-border` locally, so every
  `data-float` chip (the hero's nav pill + "Mesa 8 · lista" card, and any future
  ones) renders light regardless of the site theme.
- The mobile-drawer scrim is a fixed `bg-black/50` (was `bg-foreground/50`, which
  becomes a white veil once `foreground` inverts).

## 5. Why It Changed?
"Implementá el dark mode con toggle usando esa paleta." Built as a token-swap
theme with a header toggle + OS-aware default; the brand ember red is kept (just
brightened) so the landing still reads as Ember in dark.

## Verification
- `cd landing && pnpm build` — green, 10 pages.
- Dev server in Chrome: with OS = dark the home loads dark on first paint
  (`data-theme="dark"`, body bg `oklch(0.16 0.008 40)`, primary
  `oklch(0.62 0.2 27)`). Clicking the toggle flips dark↔light each time and writes
  `localStorage['ember-theme']`; the sun/moon icon follows. `/planes` (comparison
  table), the CTA band, footer and FAQ red bars all render correctly in dark; no
  horizontal page overflow in either theme.
- No file outside `landing/` touched.
