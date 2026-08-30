# Report 291 — landing-hero-editorial-serif

## 1. Identification
- **Report number:** 291
- **Task ID:** landing-hero-editorial-serif (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 290 (landing-mobile-nav-portal-fix)

## 2. Objective
The maintainer found the hero "muy simple" and disliked the Anton headline. Swap the
display face to a high-contrast editorial serif and add depth: a background glow, a
faint dot grid, a floating status card over the dashboard, and a real-feature bar
pinned to the bottom of the hero, with the rest of the content pushed toward the top.

## 3. Modified Files
- `landing/src/components/Hero.astro`
- `landing/src/styles/global.css` — `@fontsource/anton` → `@fontsource-variable/fraunces`, `--font-display` retargeted
- `landing/package.json` / `landing/pnpm-lock.yaml` — drop `@fontsource/anton`, add `@fontsource-variable/fraunces`

## 4. What Changed?
### Headline font
`--font-display` is now `'Fraunces Variable', Georgia, serif` (was Anton). The `h1`
drops `uppercase`, uses `font-medium leading-[1.05] tracking-tight`
(`text-5xl → sm:6xl → lg:7xl`), and `sincronizado` is `italic text-primary` —
Fraunces' display italic gives the editorial "boutique brand" read the maintainer
picked over the condensed-athletic and the plain-Inter options.

### Hero enrichment (all four requested)
- **Background glow** — two `var(--color-primary)` `blur(90px)` circles:
  `.hero-glow--right` (`46rem`, `top:8%; right:-6%`, `opacity:.12`) behind the
  dashboard and `.hero-glow--left` (`40rem`, `bottom:-8%; left:-12%`, `opacity:.09`)
  behind the headline, for a diagonal balance.
- **Dot grid** — `.hero-grid` `radial-gradient` dots at `26px`, faded with a
  `radial-gradient` `mask-image` centred at `72% 34%` so it only reads around the
  image, `opacity:.55`.
- **Floating card** — an absolutely-positioned `rounded-lg border bg-card shadow-lg`
  chip over the dashboard's bottom-left: a check glyph + "Mesa 8 · lista" / "Cocina
  la marcó hace 2 s", `rotate(-3deg)`. `hidden sm:flex` (off on the smallest
  screens). On `.hero-media:hover` it straightens with the panel.
- **Feature bar** — the section is `flex min-h-[calc(100svh-4.25rem)] flex-col
  justify-center` (the `4.25rem` accounts for the sticky nav; `justify-center`
  centres the content+bar block as a unit). The content grid is natural-height
  `items-center py-10`, and a full-width `border-t border-border bg-muted/40` strip
  follows it with only `mt-4 md:mt-8` of separation — a `<ul>` of **real SaaS
  features**: Carrito colaborativo por QR · Comandas de cocina (KDS) · División de
  cuentas y caja · Analítica por período · Multi-restaurante, each with a primary
  check. `flex-wrap` on mobile, `md:flex-nowrap md:justify-between` (one
  evenly-spread row) on desktop; items `whitespace-nowrap`. (A first pass pinned the
  bar to the very bottom with a `flex-1` spacer; the maintainer wanted it tight to
  the hero content, hence the `justify-center` + small `mt`.)

Both decorative layers are `aria-hidden pointer-events-none absolute` inside the
now-`relative` `.hero` section; the content grid gets `relative` so it stacks above
them. The hover rule moved from `.hero-media__img:hover` to
`.hero-media:hover .hero-media__img` so hovering the card region also triggers it.
The tilt / image-bleed / `prefers-reduced-motion` behaviour from report 288 is
unchanged; the hero height went from `min-h-dvh` to `calc(100svh-4.25rem)` so the
new bottom bar is fully in view.

## 5. Why It Changed?
Anton (report 288) was the maintainer's own CBUM reference but didn't land once
built; they chose the editorial-serif direction and asked for the hero to carry
more visually. Fraunces is a variable high-contrast display serif (free, offline
via `@fontsource-variable`) that pairs with the Inter body without a second sans.
The glow / grid / floating card were three of the enrichment options the maintainer
selected; the fourth (a highlights row) was then moved into a bottom feature bar
and rewritten with real product features, and the rest of the hero content pulled
to the top, per a follow-up.

## Verification
- `cd landing && pnpm install && pnpm build` — green, 6 pages.
  `@fontsource-variable/fraunces` in, `@fontsource/anton` out.
- Dev server in Chrome: `getComputedStyle(h1).fontFamily` starts `Fraunces Variable`;
  both glows (`.hero-glow--right` / `.hero-glow--left`), the masked dot grid, and the
  floating card ("Mesa 8 · lista") render. The feature bar sits ~72 px below the
  CTAs (not pinned to the fold) and is fully in view, one evenly-spread row on
  desktop with all five real features. No horizontal page overflow. Hover straightens
  both the panel and the card.
- No file outside `landing/` touched.
