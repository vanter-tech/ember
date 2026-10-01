# Report 664 — ROLLBACK-DRAG-OVERLAY-FULL-CARD

## 1. Identification
- Report: 664
- Task ID: ROLLBACK-DRAG-OVERLAY-FULL-CARD
- Predecessor: 663 (WAITER-BULK-DELETE-ORDER-ITEMS)

## 2. Objective
The user reported that `waiter/tables` felt laggy when returning to it from another view (worse LCP than the other views, no fade-in) and that it started with the change that made the drag preview show the whole card. Roll the floor back to the state right before that change, keeping the merge animation and the bulk delete.

## 3. Modified Files
- Deleted: `frontend/src/pages/waiter/components/TableCardView.tsx`, `frontend/src/pages/waiter/components/TableCardView.test.tsx`
- Modified: `frontend/src/pages/waiter/components/TableCard.tsx` (single component again, label computed inline), `frontend/src/pages/waiter/Tables.tsx` (original red chip in the `DragOverlay`, `TableCardView` import removed), `frontend/src/pages/waiter/lib/groupTables.ts` (`tableCardLabel` helper removed)
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- The drag preview is the original small red chip again (`M{n}`), so the "red bar" look comes back: dnd-kit gives the overlay wrapper the dragged card's box and the chip is only one line tall.
- `TableCard` is the single component with the dnd-kit hooks and the card markup (as before the `TableCardView` split). Behaviour of linking, the one-wide-card grouping, the FLIP animation, long-press activation and the bulk delete is unchanged.
- An uncommitted attempt was discarded at the user's request: replacing the bare "Cargando…" text of `Tables` with the page layout plus 6 placeholder cards. It did not fix what the user saw. The diff was kept only in the session scratchpad.

## 5. Why It Changed?
The user asked to return to the state just before the overlay change to see whether it caused the lag. This is a rollback made as a new commit (no history rewritten); `0ec65bb6`, `f76fa26e` and `10cdd582` stay in history.

### What the investigation found (not a confirmed root cause)
- No timer or request leak across 5 round trips; the same 4 requests each time, server ~10 ms.
- React mounts the 12 tables in ~65 ms; commits stay at 3 whatever the number of tables (96 tested), cost linear; no dnd-kit fan-out.
- `waiter/tables` is lighter than the admin analytics page (292 nodes, 0 running animations).
- The bare "Cargando datos del panel…" early return in `Tables.tsx` dates from 2026-08-18 (`4dd6a91f`), before dnd-kit (2026-10-01). With an empty cache it paints at ~35 ms and the real content at ~90–105 ms, which fits "no fade, LCP higher" on a page reload; the placeholder-layout fix removed that gap in measurement but the user reported it did not fix the problem.
- The browser used for the measurements was hidden, so frames, paint and the real LCP could not be measured by me.

### Verification
- `pnpm exec vitest run src/pages/waiter` 72/72; full `pnpm run test:run` 316/317 (the known `MenuJoin` flake); `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings).

### NOT verified
- Whether the lag/LCP gap disappears after this rollback: that is for the user to confirm in the browser. If it persists, the overlay was not the cause.
