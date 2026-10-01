# Report 667 — LINK-PANEL-ORDER-AND-UNLINK-SIZE

## 1. Identification
- Report: 667
- Task ID: LINK-PANEL-ORDER-AND-UNLINK-SIZE
- Predecessor: 666 (RESTORE-DRAG-AND-DROP-TABLE-LINK)

## 2. Objective
In the waiter's table detail panel, list the linked tables above the "Unir mesa" button and make the "Separar" button larger.

## 3. Modified Files
- `frontend/src/pages/waiter/Tables.tsx`, `frontend/src/pages/waiter/Tables.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
The "Mesas unidas" list (one row per linked table) now renders before the "Unir mesa" button instead of after it. Each row shows the table number larger (`text-lg`) and a full-size "Separar" button (`h-10`, `px-5`) instead of the small one (`h-7`). Behaviour is unchanged.

## 5. Why It Changed?
User request: the list of what is already linked should come first and the unlink action should be easier to hit.

### Verification
- A new test (order in the DOM and button size) failed first. `pnpm exec vitest run src/pages/waiter` 76/76; `pnpm run build` exit 0; `pnpm run lint` 0 errors.

### NOT verified
- How it looks on screen (not inspected).
