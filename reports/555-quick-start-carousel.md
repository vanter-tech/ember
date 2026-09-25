# Report 555 — QUICK-START-CAROUSEL

## 1. Identification
- **Report number:** 555
- **Task ID:** QUICK-START-CAROUSEL
- **Predecessor:** report 554 (QUICK-START-STANDALONE-ROW)

## 2. Objective
Remove the hard cut of the last visible tile and the horizontal scrollbar from the quick-start row.

## 3. Modified Files
- `frontend/src/pages/auth/QuickStartCarousel.tsx` (new)
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/locales/{es,en}/auth.ts`

## 4. What Changed?
- New `QuickStartCarousel`: snap-scrolling row with the scrollbar hidden, a `linear-gradient` mask that fades only the edge that still has content, and prev/next arrow buttons (`scrollBy` one tile) that render only when the row overflows. When everything fits, first/last `margin: auto` keeps it centered while staying fully scrollable when it overflows.
- Overflow/scroll state is tracked via `ResizeObserver` (scroller + tiles, re-armed on profile count change) and a scroll listener.
- `Login.tsx` wraps the tiles in it (`snap-center` per tile); new i18n keys `quickStartPrevAria`/`quickStartNextAria`.

## 5. Why It Changed?
The abrupt clipped tile and native scrollbar looked unfinished; fading edges + arrows give a carousel feel without extra dependencies.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` 16/16. Not checked visually in a browser; no dedicated test for the overflow behavior (jsdom has no layout).
