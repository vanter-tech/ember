# Report 304 — landing-feature-images

## 1. Identification
- **Report number:** 304
- **Task ID:** landing-feature-images (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 303 (landing-cta-redesign)

## 2. Objective
Swap the `Hero.png` placeholder in the `/funcionalidades` detail rows for the real
per-feature admin screenshots the maintainer added.

## 3. Modified Files
- `landing/src/lib/features.ts` — each `Feature` gains an `image: ImageMetadata`
- `landing/src/pages/funcionalidades.astro` — `<TiltedShot src>` uses `feature.image`;
  dropped the `placeholderShot` import
- **New assets** (added by the maintainer): `landing/src/assets/Cart.png`,
  `Kitchen.png`, `Table_view.png`, `Analytics.png`

## 4. What Changed?
`features.ts` now imports the four screenshots and maps one to each feature:

| # | feature | image |
|---|---|---|
| 01 | Carrito colaborativo | `Cart.png` |
| 02 | Comandas en cocina (KDS) | `Kitchen.png` |
| 03 | Gestión de piso y meseros | `Table_view.png` |
| 04 | Analítica para administradores | `Analytics.png` |

`funcionalidades.astro` passes `feature.image` to each `<TiltedShot>` (the
alternating layout, `flip`, `-ml`/`-mr` bleed, and the `overflow-hidden` section
are unchanged). `features.ts` is also consumed by `FeatureCards` /
`FeaturesTeaser` — the new `image` field is unused there, no change.

## 5. Why It Changed?
"Ya agregué las imágenes con nombres para ser identificados, y toca cambiar los
placeholder de la vista de Funcionalidades."

## Verification
- `cd landing && pnpm build` — green, 10 pages; the four PNGs optimise to WebP
  (`Cart` 80→31 kB, `Kitchen` 90→32 kB, `Table_view` 147→42 kB, `Analytics`
  143→42 kB).
- Dev server in Chrome: each `/funcionalidades` detail row shows its own screenshot
  in the tilted frame (cart / KDS / table detail / analytics). No horizontal page
  overflow; unchanged in light and dark (the shots are light UIs, so they take the
  same `brightness(0.9)` dim in dark as the hero).
- No file outside `landing/` touched.
