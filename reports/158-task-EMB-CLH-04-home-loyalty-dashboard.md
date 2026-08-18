# Report 158 — Task EMB-CLH-04

## Identification
- **Report number:** 158
- **Task ID:** EMB-CLH-04
- **Predecessor Task:** EMB-CLH-03 (report 157)

## Objective
Replace `/customer/home`'s avatar+join-table-only card with a loyalty dashboard (points, tier, visit history with amount paid) for any customer who has already joined a table somewhere, while leaving today's card unchanged for a customer who never has. Final task in the EMB-CLH backlog.

## Modified Files
- `frontend/src/pages/customer/Home.tsx`

## What Changed?
`Home.tsx` now runs two gated `useQuery` calls: `loyaltyAccountService.visits` fires unconditionally (its 404 vs. success is the tenant-detection signal), and `loyaltyAccountService.me` is `enabled` only once `visits` succeeds — so `/loyalty/accounts/me`'s strict tenant requirement is never hit from an unscoped context. While the visits query is loading or if it 404s, the component renders the exact original avatar + "Entrar a una mesa" card unchanged. On success it renders: a compact header (avatar, name, small "Entrar a una mesa" button — join-table access is preserved, not removed), a points/tier card (reusing `TIER_LABELS`/`TIER_BADGE_CLASSNAMES` and the same card styling already established in `Bill.tsx`) with a "Última visita" line, and a visit-history list (date, amount paid via the shared `formatCurrency` helper, points earned), with an empty-state message when the customer has joined but has no settled visits yet.

## Why It Changed?
Implements `docs/superpowers/specs/2026-08-17-customer-home-loyalty-dashboard-design.md` §4.3, the last piece of the EMB-CLH backlog: showing loyalty program info (points, last visit, prior visits, what was paid) on Home instead of just the avatar/join card, per the user's original request.

## Verification
- `cd frontend && pnpm run build` — `tsc -b` and `vite build` both passed.
- No `claude-in-chrome` browser tool was available this session (consistent with every other page in this branch's history — EMB-CR/EMB-STAFF/EMB-RV/EMB-CLP all note the same gap in `PROGRESS.md`). In its place: booted the Vite dev server and confirmed `index.html` serves (200) and `Home.tsx` transforms and serves cleanly through Vite's dev module graph (200, all imports resolving, no transform error) before stopping the dev server. A full authenticated click-through (both the tenant-less and dashboard states) is still owed, same as the other pages already flagged in `PROGRESS.md`.

## EMB-CLH backlog status
COMPLETE (EMB-CLH-01..04, reports 155–158).
