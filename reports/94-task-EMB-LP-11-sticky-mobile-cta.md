# Report 94 — EMB-LP-11: StickyMobileCTA island

**Predecessor Task:** EMB-LP-10 (report 93)

## Objective
Add a persistent mobile-only bottom CTA bar (Register/Iniciar Sesión) that appears once the visitor scrolls past the hero.

## Modified Files
- `landing/src/components/StickyMobileCTA.tsx` (new)

## What Changed?
Added `StickyMobileCTA.tsx`, a React island following `MobileNavDrawer.tsx`'s exact hooks pattern: a `scroll` listener (passive, cleaned up on unmount) toggles `visible` once `window.scrollY` passes 90% of `window.innerHeight`, avoiding any dependency on a hero element `id` since the component isn't wired into a page yet. When visible, it renders a `md:hidden` `fixed inset-x-0 bottom-0` bar with `border-t-[3px] border-foreground` and `shadow-brutal`, containing two flex-equal CTAs styled identically to `Nav.astro`/`MobileNavDrawer.tsx`'s existing login/register buttons (`border-[3px] border-foreground` outline for login, `bg-accent text-background shadow-brutal-sm` for register), both linking to `${FRONTEND_URL}/login` and `${FRONTEND_URL}/register`.

## Why It Changed?
Implements EMB-LP-11 from the landing-page spec (checklist item #11): a mobile conversion affordance that stays out of the way above the fold but keeps CTAs reachable during scroll on small screens, without duplicating the desktop nav's always-visible buttons.

## System Health
`landing`'s `astro build` PASSING (`dist/` removed post-verify). Component is standalone, NOT yet wired into `index.astro` (EMB-LP-13's job, same as Footer). `frontend`/`backend` untouched this task.
