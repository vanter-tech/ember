# Report 665 — REMOVE-DRAG-AND-DROP-TABLE-LINK

## 1. Identification
- Report: 665
- Task ID: REMOVE-DRAG-AND-DROP-TABLE-LINK
- Predecessor: 664 (ROLLBACK-DRAG-OVERLAY-FULL-CARD)

## 2. Objective
`waiter/tables` kept feeling laggy when returning to it from another view even after the drag preview was rolled back. The user had said that if there was still a problem the drag-and-drop should go, leaving linking by the "Unir mesa" button and the merge animation. Remove the drag-and-drop.

## 3. Modified Files
- Deleted: `frontend/src/pages/waiter/lib/linkDrop.ts`, `frontend/src/pages/waiter/lib/linkDrop.test.ts`
- Modified: `frontend/package.json`, `frontend/pnpm-lock.yaml` (`@dnd-kit/core` removed), `frontend/src/pages/waiter/Tables.tsx`, `frontend/src/pages/waiter/components/TableCard.tsx`, `frontend/src/pages/waiter/Tables.test.tsx`, `frontend/src/locales/{es,en}/waiter.ts` (`linkDragHint` removed)
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- `Tables.tsx` no longer has `DndContext`, sensors, drag handlers, `DragOverlay`, the drag-only link mutation or the drag hint. `TableCard` no longer has `useDraggable`/`useDroppable`/`useDndContext`; it is a plain card again. `@dnd-kit/core` is uninstalled (`pnpm audit --prod` clean).
- Unchanged: linking and unlinking with "Unir mesa" / "Separar" (WAITER only), the one-wide-card grouping with its FLIP animation, kitchen/ticket/report labels, bulk delete.
- Tests: the drag tests were inverted first (a failing test "no table is draggable and there is no drag hint") and now pass; `linkDrop.test.ts` deleted with its module.

## 5. Why It Changed?
User decision, as the A/B step of a lag investigation that had not found a cause. The earlier measurements did not implicate dnd-kit (3 commits regardless of table count, no leaks, mount ~65 ms), so removing it is an experiment: if the lag persists the cause is elsewhere and this commit can be reverted (`git revert`) to bring the drag-and-drop back.

### Verification
- `pnpm exec vitest run src/pages/waiter` 67/67; full `pnpm run test:run` 311/312 (the known `MenuJoin` flake); `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings).

### NOT verified
- Whether the lag is gone: for the user to check in the browser. No cause of the lag has been confirmed.
