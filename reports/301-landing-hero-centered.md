# Report 301 — landing-hero-centered

## 1. Identification
- **Report number:** 301
- **Task ID:** landing-hero-centered (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 300 (landing-funcionalidades-tiltedshots)

## 2. Objective
"El hero, lo necesito más grande y centrado." Convert the split (text-left /
image-right) hero into a centered stacked hero with a larger headline and a large
centered product shot.

## 3. Modified Files
- `landing/src/components/Hero.astro`

## 4. What Changed?
- **Layout:** the `md:grid-cols-2` split is gone. The content wrapper is now
  `mx-auto flex max-w-6xl flex-col items-center px-6 py-16 text-center md:py-24` —
  badge, headline, paragraph and CTAs are all centred (`sm:justify-center` on the
  button row).
- **Bigger:** headline `text-5xl sm:text-6xl md:text-7xl lg:text-[5.5rem]
  xl:text-[6.25rem]` (100 px at `xl`, was ~84), `max-w-4xl text-balance`.
  Paragraph `max-w-2xl text-balance md:text-xl`.
- **Centered product shot:** `<TiltedShot>` moved below the CTAs with
  `frameClass="mt-14 w-full max-w-5xl md:mt-16"` (1024 px centred). The tilt / ring
  / layered shadow / hover from `TiltedShot` are unchanged; the responsive width
  overrides (`md:w-[132%] …`) and the `-mr` bleed are dropped since it is centred
  now. The status-card chip moved from bottom-left to **bottom-right**
  (`-bottom-4 -right-4`) so the two floating chips sit on a diagonal.
- **No forced viewport height:** `min-h-[calc(100svh-4rem)]` + `flex-1` are
  removed. A centred hero with a 1024 px image is taller than one screen by design;
  the section is now content-sized (`flex flex-col`) so nothing clips the headline
  and the image simply extends below the fold. The feature bar still follows.
- The dot-grid mask re-centred: `radial-gradient(… at 72% 34%) → (… at 50% 28%)`.

## 5. Why It Changed?
The maintainer asked for a bigger, centred hero. The split layout put the headline
against the left edge and capped its size; a centred stack lets the headline run to
100 px and the dashboard sit large and central, which is the look they were after.

## Verification
- `cd landing && pnpm build` — green, 10 pages.
- Dev server in Chrome: the hero renders centred — `getComputedStyle(h1).textAlign
  === 'center'`, `fontSize 100px`, `h1.getBoundingClientRect().top ≈ 215`
  (not clipped); the `<TiltedShot>` is 1024 px wide and centred
  (`|innerWidth − right − left| ≈ 15`). No horizontal page overflow.
- No file outside `landing/` touched.
