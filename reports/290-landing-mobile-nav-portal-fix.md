# Report 290 — landing-mobile-nav-portal-fix

## 1. Identification
- **Report number:** 290
- **Task ID:** landing-mobile-nav-portal-fix (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 289 (landing-plans-page-comparison)

## 2. Objective
Fix the mobile burger menu: tapping it did nothing usable.

## 3. Modified Files
- `landing/src/components/MobileNavDrawer.tsx`

## 4. What Changed?
The drawer's overlay/panel (`position: fixed; inset: 0`) rendered inside
`<header>`, and the header carries `bg-background/80 backdrop-blur`. A
`backdrop-filter` establishes a **containing block for `position: fixed`
descendants**, so `inset-0` resolved against the ~69 px-tall header box instead of
the viewport — the overlay and the slide-in panel collapsed to a strip behind the
header and were effectively invisible. The React island itself hydrated fine and
the `open` state toggled; the bug was pure CSS containing-block.

Fix: the overlay is now rendered through `createPortal(overlay, document.body)`, so
it escapes the header's containing block and `fixed inset-0` resolves against the
viewport again. A `mounted` flag (set in a `useEffect`) gates the portal so it
never runs during SSR. Bumped the overlay `z-index` from `z-50` to `z-[60]` (it now
sits above the whole document, not just the header). The header keeps its blur.

No markup, styling, links, or the body-scroll-lock behaviour changed otherwise.

## 5. Why It Changed?
`backdrop-blur` was added to the header in the brutalist→SaaS restyle (report 287);
the drawer predates it and had always assumed `fixed` meant "relative to the
viewport". Portalling to `body` is the standard fix and keeps the header's blur.

## Verification
- `cd landing && pnpm build` — green, 6 pages.
- Dev server in Chrome: with the `md:hidden` wrapper forced visible, clicking the
  burger now renders the drawer as a direct child of `<body>`; measured overlay
  `1912×918` (full viewport), panel pinned to the right edge, full height. Links,
  the backdrop click-to-close, the × button and Esc all dismiss it.
- No file outside `landing/` touched.
