# Report 328 — LSEO-05: update the outdated footer logo

## 1. Identification
- **Report number:** 328
- **Task ID:** LSEO-05 (Landing — SEO & customer acquisition backlog; branding-coherence
  fix surfaced while running the LSEO-05 technical checks)
- **Predecessor task:** report 327 (LSEO-03, landing-cta-localhost-fix)

## 2. Objective
While auditing the deployed landing for LSEO-05 (PageSpeed / Rich Results / 404
status), the footer was found to still render the old generic `<Flame>` mark
inside a `bg-primary` square + a plain `Ember` wordmark, whereas `Nav.astro`
already switched to the real brand logo (`ember-logo-l.ico` / `ember-logo-d.ico`)
with a theme-aware light/dark swap. Bring the footer lockup in line with the nav.

## 3. Modified Files
- `landing/src/components/Footer.astro`

## 4. What Changed?
- Removed `import Flame from './Flame.astro'` (footer was its only consumer; the
  `Flame.astro` file is left in place for possible future use).
- Replaced the `<span class="… bg-primary …"><Flame/></span>` + plain
  `<p>Ember</p>` block with the exact lockup used by `Nav.astro`: two `<img>`
  tags (`/ember-logo-l.ico` and `/ember-logo-d.ico`, `alt=""`, `width="142"
  height="180"`, classes `site-logo site-logo--light|--dark h-8 w-auto`) followed
  by `<p class="site-wordmark …">Ember</p>`.
- Added a component-scoped `<style>` block to `Footer.astro` mirroring the nav's
  rules: `.site-logo--dark { display:none }` by default, flipped under
  `:root[data-theme='dark']`; `.site-wordmark` brand-red (`#8c1717`) in light,
  `var(--color-foreground)` in dark. Astro `<style>` is component-scoped, so the
  nav's identical rules do not reach the footer — they had to be repeated here.
- No structural/link change: the footer logo remains non-linked (nav wraps its
  lockup in `<a href="/">`, footer does not — left as-is, out of scope).

## 5. Why It Changed?
The nav and footer are the two places the brand mark appears on every landing
page. After the nav moved to the real logo asset, the footer kept the placeholder
`Flame` treatment, so the same page showed two different "Ember" logos — a
visible inconsistency on a site whose whole recent arc (report 287) was making
`ember.vanter.net` and `app.ember.vanter.net` look like one product. Reusing the
nav's asset + classes keeps a single source of truth for the mark.

## 6. Verification
- `cd landing && pnpm run build` — 20 pages built, sitemap emitted, no errors.
- `dist/index.html` footer now contains `ember-logo-l.ico`, `ember-logo-d.ico`,
  `site-wordmark`; no `Flame` markup remains.
- `dist/en/index.html` contains 2 `ember-logo-d.ico` references (nav + footer),
  confirming the dark-variant `<img>` ships on every page.
- Landing-only change; backend/frontend suites not affected.

## 7. LSEO-05 status (still open, needs the user in a browser)
- ✅ 404 returns HTTP 404 on Cloudflare (verified live for `/` and `/en/` bogus paths).
- ✅ Hero LCP already optimized (`webp`, `loading="eager"`, `fetchpriority="high"`,
  explicit `width`/`height`, `decoding="async"`).
- ⬜ PageSpeed Insights run on `https://ember.vanter.net/` + `/planes/` — pending.
- ⬜ Rich Results Test on `https://ember.vanter.net/` — pending.
- Note: `ember-logo-l/d.ico` are ~105 KB each; the user is separately shrinking
  those source files.
