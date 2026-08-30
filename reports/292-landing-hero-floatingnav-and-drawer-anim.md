# Report 292 — landing-hero-floatingnav-and-drawer-anim

## 1. Identification
- **Report number:** 292
- **Task ID:** landing-hero-floatingnav-and-drawer-anim (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 291 (landing-hero-editorial-serif)

## 2. Objective
Polish round: animate the (now working) mobile nav drawer, widen the gap between
the hero text and the dashboard image, and add a second floating overlay — a
"Floating nav" pill — at the top of the dashboard to mirror the status card at the
bottom.

## 3. Modified Files
- `landing/src/components/MobileNavDrawer.tsx`
- `landing/src/components/Hero.astro`

## 4. What Changed?
### Mobile nav drawer animations
The overlay is now **always mounted** (portaled to `body`) rather than
conditionally rendered, so both enter and exit transitions run:
- Overlay: `transition-opacity duration-300`; `opacity-100` when open,
  `pointer-events-none opacity-0` when closed.
- Panel: `transition-transform duration-300 ease-out`; `translate-x-0` ↔
  `translate-x-full` (slides in/out from the right).
- Burger icon: the three bars now `transition-all duration-300` and morph to an ✕
  when open (`translate-y-2 rotate-45` / `opacity-0` / `-translate-y-2 -rotate-45`).
  The button toggles (`setOpen(v => !v)`) and its `aria-label` swaps
  Abrir/Cerrar menú.
- `inert={!open}` on the overlay so the off-screen panel is fully non-interactive
  and out of the tab order when closed (replaces manual `tabIndex` juggling).
- `motion-reduce:transition-none` on all three, plus nav links got hover states and
  padded hit areas.

### Hero — text/image separation
Grid gap widened: `gap-10` → `gap-12 md:gap-16 lg:gap-20`, so "sincronizado" clears
the tilted dashboard.

### Hero — Floating nav overlay
A new `.hero-nav` pill absolutely positioned over the **top-left** of the dashboard
(`-top-4 left-4` → `md:-top-6 md:left-2`), mirroring the bottom `.hero-card`. It is
a `rounded-full border bg-card shadow-lg` strip of four `size-8` icon buttons —
grid (active, `bg-primary`), list, user, log-out — echoing Ember's real in-app
FloatingNav. `hidden sm:flex`, `aria-hidden`, `rotate(3deg)` at rest, straightens
(`translateY(-4px) rotate(2deg)`) on `.hero-media:hover` alongside the panel and the
status card; `prefers-reduced-motion` drops its transition.

## 5. Why It Changed?
The drawer worked (report 290) but snapped open/closed with no motion; the hero
image sat close to the italic headline; and the maintainer wanted the dashboard
framed by two floating chips (status card + nav) instead of one. Keeping the drawer
mounted and animating with CSS classes is simpler than exit-animation bookkeeping
and gives `inert` a clean home.

## Verification
- `cd landing && pnpm build` — green, 6 pages.
- Dev server in Chrome:
  - Drawer: clicking the burger slides the panel in from the right while the
    backdrop fades and the icon rotates to ✕ (`getComputedStyle` on the bars →
    `45deg / none / -45deg`); closed state measured `opacity 0`,
    `pointer-events: none`, `translate: <panel width>`, `inert` present.
  - Hero: the floating-nav pill renders over the dashboard's top-left with the four
    icons; on `.hero-media:hover` both it and the status card straighten with the
    panel. No horizontal page overflow.
- No file outside `landing/` touched.
