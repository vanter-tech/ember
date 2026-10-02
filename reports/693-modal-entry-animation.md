# Report 693 — MODAL-ENTRY-ANIMATION

## 1. Identification
- **Report number:** 693
- **Task ID:** MODAL-ENTRY-ANIMATION
- **Predecessor task:** report 692 — OPS-UNREAD-FIELDS (merged locally into `main`; this is branch `fix/modal-entry-animation`)

## 2. Objective
Every modal opened abruptly, with no enter animation like the routed views have.

## 3. Modified Files
- `frontend/src/index.css`
- `frontend/src/components/ui/overlayAnimation.test.tsx` (new)

## 4. What Changed?
Two `@custom-variant` lines: `data-open` → `&[data-state="open"]` and `data-closed` → `&[data-state="closed"]`.

## 5. Why It Changed?
`ui/dialog`, `ui/alert-dialog`, `ui/popover` and `ui/select` were generated for base-ui and animate through `data-open:animate-in / fade-in-0 / zoom-in-95` and `data-closed:animate-out / fade-out-0 / zoom-out-95`. The project uses Radix, which reports its state as `data-state="open|closed"`, so `[data-open]` never matched and the animation classes already in the components never applied. Mapping the variants fixes all four components (every modal, alert, popover and select) at once, without touching them.

## Verification
- Built stylesheet now contains `.data-open\:animate-in[data-state=open]` (+ fade/zoom) and the `data-closed` counterparts.
- Real browser (dev server, `/login` language popover, the same Radix mechanism): after opening, the content has `animation-name: enter` and opacity 0 / scale .95 at the start, 0.80 / .99 half-way (100 ms duration), 1 / 1 at the end; on Escape it gets `data-state=closed` with an `exit` animation and unmounts on `animationend`. The tab was in the background, so the browser paused the animation clock; the progress was driven by hand — I did not watch it play in a visible window.
- vitest: an open dialog reports `data-state="open"` and carries the animation classes (the CSS itself cannot be evaluated in jsdom). Frontend build exit 0, lint 0 errors, vitest 416/417 (known `MenuJoin` failure).
- Note: the animation is the stock 100 ms fade + zoom from 95%. If a longer or different one is wanted (e.g. slide-up on mobile), it is a change to the `duration-*` / classes in those four components.
