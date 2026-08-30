# Report 306 — landing-client-router

## 1. Identification
- **Report number:** 306
- **Task ID:** landing-client-router (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 305 (landing-i18n)

## 2. Objective
Add Astro's `<ClientRouter />` (the dependency-free client-side router, ex
`<ViewTransitions />`, renamed in Astro 5) to the landing site for smooth
SPA-style navigation and prefetching — with the adjustments its swap model
requires so the theme and the header scripts keep working across navigations.

## 3. Modified Files
- `landing/src/layouts/Layout.astro`
- `landing/src/components/Nav.astro`

## 4. What Changed?
**`Layout.astro`** — `import { ClientRouter } from 'astro:transitions'` and
`<ClientRouter />` in `<head>` (after `<meta generator>`). Navigations now go
through the History API with a default cross-fade instead of a full document
load, and in-viewport links are prefetched.

The anti-FOUC theme script was reworked: its body is now a named `applyTheme()`
that is (a) called once at first paint as before, and (b) registered as an
`astro:after-swap` listener on `document`. `document` survives view transitions,
so the one listener re-applies `data-theme` after every client-side navigation —
`astro:after-swap` runs after the new DOM is in place but **before it is
painted**, and the swapped-in page ships without `data-theme` (it is runtime
state), so this prevents a theme flash. No `data-astro-rerun` — that would
re-run the whole script on each swap and re-add a duplicate listener every time.

**`Nav.astro`** — the trailing `<script is:inline>` (scroll → `data-scrolled` on
`#site-header`, click → theme toggle) was wrapped in a `setup()` registered on
`document`'s `astro:page-load` event (which fires on the initial full load **and**
after every client-side navigation). `#site-header` / `#theme-toggle` are fresh
nodes after each swap, so the handlers must re-bind; the persisted `window`
`scroll` listener is removed before re-adding to avoid a leak per navigation.

## 5. Why It Changed?
Maintainer asked for the Astro router that smooths transitions / load times
("le cambiaron el nombre" — yes: `ViewTransitions` → `ClientRouter` in Astro 5;
this repo is on Astro 7.2.2). `<ClientRouter />` swaps `<head>`/`<body>` in place
instead of reloading, so inline scripts do **not** re-execute on navigation —
without the two adjustments the dark-mode class would drop on the first
client-side nav and the sticky-header scroll state would stop updating.

## Verification
- `cd landing && pnpm build` — green, **20 pages**; built HTML links the
  `ClientRouter.astro_..._script...js` chunk on every page.
- Preview + browser:
  - `window` global set before a nav survives it → real client-side navigation,
    no full document reload; `astro:after-swap` / `astro:page-load` both fire.
  - Toggle to dark on `/planes`, then nav `/planes → /funcionalidades` → target
    page renders dark, `<html data-theme>` intact, no flash.
  - Active nav pill updates per route after a client-side nav
    (`aria-current="page"` moves).
  - `computer.scroll` (trusted events) on a freshly hard-loaded `/planes` after a
    nav → `#site-header` gains its scrolled background/border → scroll handler
    re-bound. (Programmatic `window.scrollTo` from the automation context does not
    emit a page-visible `scroll` event — a known tooling quirk, not a site bug;
    manual `dispatchEvent('scroll')` and real wheel scroll both work.)
- No file outside `landing/` touched → backend/frontend suites not re-run.
