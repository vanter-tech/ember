# Report 01 — task-1.1

**Task ID:** task-1.1
**Predecessor Task:** None (first task executed)

## Objective
Fix all `TS6133`/`TS6192` TypeScript compilation errors (`noUnusedLocals`/`noUnusedParameters`) blocking `pnpm run build`, so the frontend compiles cleanly again.

## Modified Files
- `frontend/src/pages/customer/ComandaView.tsx`
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/pages/customer/components/ItemsFloatingIsland.tsx`
- `frontend/src/pages/kitchen/components/FocusedCard.tsx`
- `frontend/src/pages/kitchen/OrdersDisplay.tsx`
- `frontend/src/pages/waiter/Tables.tsx`

## What Changed?
- Removed unused imports: `useState`, `ArrowRight`, `Car`, `User`, `api`, `useQuery` (`ComandaView.tsx`); `useUIStore`, `use`, `settingStore` (`Menu.tsx`); `settingStore` (`Tables.tsx`); `CardTitle`/`CardContent`/`CardFooter` (`FocusedCard.tsx`); `useMutation`/`useQueryClient` and the entire unused `Card*` import block (`OrdersDisplay.tsx`).
- Removed unused destructured locals: `settings` (`Menu.tsx`, `Tables.tsx`), `data` param in a mutation's `onSuccess` (`Menu.tsx`), `isLoading`/`isError` (`OrdersDisplay.tsx`).
- Removed unused `catch`/callback bindings: two `onError: (e) => {...}` handlers in `ComandaView.tsx` now take no parameter.
- Prefixed the unused `item` parameter with `_` in `ItemsFloatingIsland.tsx`'s `.map((_item, index) => ...)` (TS ignores underscore-prefixed unused parameters).
- In `OrdersDisplay.tsx`, used the previously-discarded `index` as `key={index}` on `<QueueCard>` instead of dropping it, since a `key` was genuinely missing on that list.

## Why It Changed?
`tsc -b` was failing outright under `noUnusedLocals`/`noUnusedParameters` (enabled in `tsconfig.app.json`), which meant `pnpm run build` (`tsc -b && vite build`) could not complete — blocking any deployable build. All changes are dead-code removal only; no runtime logic was altered. The `settingStore()` calls removed from `Menu.tsx`/`Tables.tsx` had their return values fully discarded (no consumer), so dropping them changes no visible behavior. Full loading/error UI for `OrdersDisplay.tsx` was intentionally left out of scope — it's covered by task-1.6.

## Verification
`cd frontend && pnpm run build` → succeeds (`✓ built in 2.45s`), zero TypeScript errors. Only pre-existing warning: main JS chunk >500kB (unrelated, not a compile error).
