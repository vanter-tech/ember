# Report 06

## 1. Identification
- **Report Number:** 06
- **Task ID:** task-1.6
- **Predecessor Task:** task-1.5

## 2. Objective
Add a global React error boundary to the app shell, and fix missing `key` props and missing loading/error states in `OrdersDisplay.tsx`.

## 3. Modified Files
- `frontend/src/components/ErrorBoundary.tsx` (new)
- `frontend/src/main.tsx`
- `frontend/src/pages/kitchen/OrdersDisplay.tsx`

## 4. What Changed?
- Added `ErrorBoundary`, a class component implementing `getDerivedStateFromError`/`componentDidCatch`, rendering a fallback screen styled consistently with `NotFound.tsx`.
- Wrapped `<App />` with `<ErrorBoundary>` inside `<StrictMode>` in `main.tsx`.
- `OrdersDisplay.tsx`: destructured `isLoading`/`isError` from the `useQuery` call and added early-return loading/error UI states.
- `OrdersDisplay.tsx`: replaced the array-`index` key on `QueueCard` with `order.id ?? index`, using the stable order identifier.

## 5. Why It Changed?
React requires a top-level error boundary to prevent an uncaught render error anywhere in the tree from blanking the entire app; none existed. `OrdersDisplay.tsx` rendered `useQuery` data with no loading/error feedback (silently showing an empty queue) and keyed list items by array index, which causes incorrect DOM reconciliation when the order list is resorted by `createdAt`.
