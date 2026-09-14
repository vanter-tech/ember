# Report 469

## 1. Identification
- **Report Number:** 469
- **Task ID:** KDS focused-ticket: move "Seleccionar todo" next to "Imprimir" (ad-hoc, layout only)
- **Predecessor Task:** report 468 (Cliente/Anular cleanup + reprint)

## 2. Objective
User asked to move the "Seleccionar todo"/"Deseleccionar todo" button up into the same row as "Imprimir", instead of its own row below. Follow-up correction: the status `Select` dropdown must also join that same row, positioned to the left of "Seleccionar todo" — confirmed via `AskUserQuestion` (final order: `[Dropdown] [Seleccionar todo] [Imprimir]`, dropdown still only rendered while `selectedIds.size > 0`).

## 3. Modified Files
- Modify: `frontend/src/pages/kitchen/components/FocusedCard.tsx`

## 4. What Changed?
Collapsed the two-row layout into one: the top button row (`<div className="flex flex-row items-center gap-3">`) now holds, in order, the conditional status `Select` (only when `selectedIds.size > 0`), the select-all `Button`, then "Imprimir". The previously separate row for the dropdown was removed. No logic changes — pure layout.

## 5. Why It Changed?
Direct user request, clarified mid-task on exact dropdown placement via a quick disambiguation question (two plausible readings of "a la izquierda del boton").

## 6. Verification
- `pnpm exec vitest run src/pages/kitchen/components/FocusedCard.test.tsx` — **7/7**.
- `pnpm run test:run` (full suite) — **135/135**, unchanged.
- `pnpm run build` / `pnpm run build:hub` — both clean.
