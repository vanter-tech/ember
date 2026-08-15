# Report 89 — EMB-LP-06

## Identification
- **Report:** 89
- **Task ID:** EMB-LP-06
- **Predecessor Task:** EMB-LP-05 (report 88)

## Objective
Build `src/components/Hero.astro` — above-the-fold headline/subheadline, primary/secondary CTAs, and a product screenshot rendered through Astro's `<Image/>` pipeline.

## Modified Files
- `landing/src/components/Hero.astro` (new)
- `landing/src/assets/product-screenshot.png` (new)
- `landing/src/pages/index.astro`
- `landing/package.json`
- `landing/pnpm-lock.yaml`

## What Changed?
- `Hero.astro`: two-column brutalist section (`grid md:grid-cols-2`) — eyebrow label, oversized `text-5xl md:text-7xl` uppercase black headline with the accent-colored second line, subheadline, and a CTA row with primary ("Registrarme" → `${FRONTEND_URL}/register`, solid accent fill + `shadow-brutal`) and secondary ("Ver planes" → `#pricing`, outline style) buttons — both rendered inline, satisfying "CTA above the fold" without any scroll/JS gating. The screenshot column wraps an Astro `<Image/>` (`width={1200} height={800}`, descriptive Spanish `alt` text) in a `border-[4px] border-foreground shadow-brutal` frame for the brutalist border treatment.
- `src/assets/product-screenshot.png` (new): a placeholder 1200×800 PNG (off-white fill, near-black inset frame) generated with a throwaway Node/zlib script — no real product screenshot or image-editing tooling exists on this machine yet, same placeholder pattern EMB-LP-03 used for `og-image.png`/`apple-touch-icon.png`. Placed under `src/assets/` (not `public/`) specifically so it goes through Astro's `<Image/>` build pipeline rather than being served as-is.
- `landing/package.json`/`pnpm-lock.yaml`: added `sharp` as a devDependency — Astro's asset pipeline requires it to actually process `<Image/>` sources; `astro build` failed with `MissingSharp` before this was added, and Astro has no pure-JS fallback for local image transforms.
- `src/pages/index.astro`: imports and renders `<Hero />` directly below `<Nav />`, replacing the placeholder `<h1>Astro</h1>`.

## Why It Changed?
Checklist items #2 (CTA above the fold), #9 (alt text on every image), and #20 (compressed images via Astro's Image pipeline) require the hero to ship real primary/secondary CTAs visible without scrolling and a properly optimized, alt-labeled screenshot. `astro build` confirms the pipeline is wired correctly end-to-end: the placeholder PNG is picked up, resized/compressed, and emitted as a hashed `.webp` (`before: 6kB, after: 2kB`) rather than copied verbatim — the mechanism EMB-LP-07+ imagery should reuse. The screenshot itself remains a solid-fill placeholder (no real product screenshot exists yet); swap it before launch same as the other EMB-LP-03 placeholder assets. `astro build` PASSED, `dist/` removed post-verify. Frontend/backend untouched this task.
