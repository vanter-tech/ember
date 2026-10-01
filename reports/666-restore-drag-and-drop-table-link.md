# Report 666 — RESTORE-DRAG-AND-DROP-TABLE-LINK

## 1. Identification
- Report: 666
- Task ID: RESTORE-DRAG-AND-DROP-TABLE-LINK
- Predecessor: 665 (REMOVE-DRAG-AND-DROP-TABLE-LINK)

## 2. Objective
Bring back the drag-and-drop table linking and the full-card drag preview. Both had been rolled back (reports 664 and 665) while investigating a lag in `waiter/tables`; the user then found that the lag only happened inside the DevTools responsive (device-emulation) view and the page is fine in the normal browser view, and asked for the merge-by-drag back.

## 3. Modified Files
Restored to their state before reports 664/665 (`git revert --no-commit` of `008233c5` and `f8d9e9db`; the two reports and `PROGRESS.md` were deliberately kept):
- `frontend/package.json`, `frontend/pnpm-lock.yaml` (`@dnd-kit/core` 6.3.1 back)
- `frontend/src/pages/waiter/Tables.tsx`, `frontend/src/pages/waiter/components/TableCard.tsx`, `frontend/src/pages/waiter/Tables.test.tsx`, `frontend/src/pages/waiter/lib/groupTables.ts`, `frontend/src/locales/{es,en}/waiter.ts` (drag hint)
- Re-added: `frontend/src/pages/waiter/components/TableCardView.tsx` (+ test), `frontend/src/pages/waiter/lib/linkDrop.ts` (+ test)
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- Long-press drag-and-drop linking is back: a free table is dragged onto an occupied one (mouse activates after 8 px, touch after a 350 ms hold), occupied targets pulse only while dragging and only under `motion-safe`, WAITER only.
- The drag preview is the whole card again (`TableCardView`, same box as the dragged card) instead of the red chip.
- The linking by button, the one-wide-card grouping with its FLIP animation, and the bulk delete are untouched.
- Reports 664 and 665 stay as the history of what was tried.

## 5. Why It Changed?
The lag that motivated the rollback was not in the application: it only appeared in the DevTools responsive-mode view (device emulation normally throttles the CPU and the Performance recording adds load). The earlier measurements had also not implicated dnd-kit (3 commits regardless of the number of tables, no timer or request leaks, mount ~65 ms). Removing the drag-and-drop was therefore unnecessary.

One real, minor finding from the investigation, not acted on: entering `waiter/tables` from another layout (Kitchen, Admin) causes about 8 store updates while the WebSocket connects, and `WaiterLayout`/`Tables` subscribe to the whole store. A test with the real store measured only 2 extra commits (~1 ms), so it is not worth changing now.

### Verification
- `pnpm exec vitest run src/pages/waiter` 75/75 (as before the experiment); full `pnpm run test:run` 319/320 (the known `MenuJoin` flake); `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings); `pnpm audit --prod` clean.

### NOT verified
- The drag itself on the user's device after this restore (it worked there before the experiment).
