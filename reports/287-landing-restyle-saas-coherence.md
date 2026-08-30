# Report 287 — landing-restyle-saas-coherence

## 1. Identification
- **Report number:** 287
- **Task ID:** landing-restyle-saas-coherence (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 286 (HPD-14 — Ops Agent + Prometheus scrape + alert policies)

## 2. Objective
Replace the landing site's brutalist look (zero-radius, hard 6px black box-shadows,
3–4px black borders, all-caps `font-black` headings, `font-mono` eyebrow labels) with
the SaaS frontend's design system so `ember.vanter.net` and `app.ember.vanter.net`
share one visual language. Pure visual change — no behaviour, routing, SEO, contact
flow, or build-pipeline changes. Legal pages (`/terms`, `/privacy`) restyled only;
their content is unchanged.

## 3. Modified Files
- `landing/package.json` — add `@fontsource-variable/inter` dependency
- `landing/pnpm-lock.yaml` — lockfile update for the above
- `landing/src/styles/global.css` — token layer rewrite
- `landing/src/components/Nav.astro`
- `landing/src/components/Hero.astro`
- `landing/src/components/Features.astro`
- `landing/src/components/Pricing.astro`
- `landing/src/components/CTASection.astro`
- `landing/src/components/ContactSection.astro`
- `landing/src/components/Footer.astro`
- `landing/src/components/ContactForm.tsx`
- `landing/src/components/CookieBanner.tsx`
- `landing/src/components/MobileNavDrawer.tsx`
- `landing/src/components/StickyMobileCTA.tsx`
- `landing/src/pages/404.astro`
- `landing/src/pages/thank-you.astro`
- `landing/src/pages/terms.astro`
- `landing/src/pages/privacy.astro`

## 4. What Changed?
### Token layer (`global.css`)
- Removed the brutalist `@theme` block: `--radius-*: 0px`, `--shadow-brutal`,
  `--shadow-brutal-sm`, `--border-width-thick/heavy`, the `#f5f5f0` cream background,
  the `#8c1717` `--color-accent`, and the `--font-mono` label stack.
- Added the SaaS light palette, values copied verbatim from
  `frontend/src/index.css` `:root`: `background/foreground/card/primary/
  primary-foreground/secondary/secondary-foreground/muted/muted-foreground/
  accent/accent-foreground/destructive/border/input/ring` as `oklch(...)`. The
  landing's old red accent maps onto `--color-primary` (`oklch(0.395 0.175 28.5)`),
  which is the same brand maroon (`#8c1717`) the SaaS already uses.
- Radius scale mirrors the SaaS (`--radius: 0.625rem` + `sm/md/lg/xl/2xl`
  multipliers).
- `--font-sans` now `'Inter Variable', 'Inter', ui-sans-serif, system-ui`; the real
  webfont is pulled in via `@import "@fontsource-variable/inter"` (offline-friendly on
  Cloudflare Pages, mirrors how the SaaS bundles Geist via `@fontsource-variable`).
- `@layer base { body { ... } }` sets background/foreground/font/antialiasing so a
  bare `<body>` renders correctly (previously every section carried `bg-background`).
- `:focus-visible` outline moved from the 3px red ring to a 2px `--color-ring` neutral.

### Components / pages
Consistent, mechanical class swap applied everywhere:

| Before | After |
|---|---|
| `border-[3px]/[4px] border-foreground`, section `border-b-[3px]` | `border border-border`, `border-t border-border` |
| `shadow-brutal` / `shadow-brutal-sm` | `shadow-sm` (cards), `shadow-md` (hero image, banners), `shadow-lg` (mobile drawer/sticky bar) |
| square corners | `rounded-lg` on cards / buttons / inputs, `rounded-xl` on the hero screenshot frame |
| `bg-accent text-background` buttons | `bg-primary text-primary-foreground` with `hover:bg-primary/90` |
| `font-black uppercase tracking-tight` headings | `font-bold` / `font-semibold`, sentence case, `tracking-tight` |
| `font-mono ... uppercase tracking-widest text-accent` eyebrows | `text-xs font-semibold uppercase tracking-wider text-primary` |
| `text-foreground/80` body copy | `text-muted-foreground` |
| Pricing highlighted plan = solid black card | white card with `border-primary ring-1 ring-primary` |
| Pricing `font-mono "— feature"` list | flex row + inline check `<svg>` in `text-primary` (no icon dependency) |
| CTA section `bg-foreground` | `bg-primary text-primary-foreground`, inverted button on `bg-background` |
| Nav (opaque, hard border) | `bg-background/80 backdrop-blur`, `border-b border-border` |
| ContactForm error styling on `accent` | shadcn `destructive` token; inputs use `border-input` + `focus-visible:ring-ring` |

One real layout bug fixed while reviewing the running dev server: the hero `<h1>`
rendered `Tu restaurante,sincronizado` with **no space** — Astro collapsed the
newline+indent between the text node and `<span class="text-primary">`, and at
`md:text-6xl` (60px) the unwrapped string was 703px wide in a 528px column, so the
hero image visually clipped it. Fixed by putting text + span on one line with an
explicit space and dropping the hero to `md:text-5xl` (48px, closer to the SaaS
heading scale); "sincronizado" now wraps to its own line as the coloured emphasis.

No other JSX structure, props, state, `client:*` directives, ARIA attributes, or copy
strings (beyond dropping forced uppercase) were changed. `astro.config.mjs`,
`functions/api/contact.ts`, `lib/constants.ts`, `SEO.astro`, `Analytics.astro`,
`Layout.astro`, and all build scripts are untouched.

## 5. Why It Changed?
The landing was the only surface still on the throwaway brutalist theme; every other
Ember UI (admin, waiter, KDS, customer cart, Ember Hub `/app`) is on the shadcn
"radix-nova" neutral+maroon system. A prospect moving from `ember.vanter.net` to the
product signup saw two unrelated-looking products. Sharing the exact token values
(not just "similar" ones) means future palette changes to `frontend/src/index.css`
can be copied across in one edit, and the brand maroon is now one concept
(`--color-primary`) instead of two (`--color-accent` here, `--primary` there).
Legal-page content was explicitly left as-is (already carries real Vanter S.A. /
Ecuador jurisdiction text); only the brutalist card chrome around it changed.

## Verification
- `cd landing && pnpm install && pnpm build` — green. 5 pages built
  (`index`, `404`, `privacy`, `terms`, `thank-you`), sitemap emitted, image
  optimization OK.
- Built CSS (`dist/_astro/*.css`) inspected: `--color-primary:oklch(39.5% .175 28.5)`,
  `--color-destructive`, the `body` base rule, and one `@font-face` for Inter Variable
  all present.
- `grep` across `landing/src` for `shadow-brutal|border-foreground|border-[3px]|
  font-mono|font-black|bg-accent|text-accent` — zero matches.
- Backend / frontend test suites not run: no file outside `landing/` was touched.
