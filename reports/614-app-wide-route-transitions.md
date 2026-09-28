# Report 614 — APP-WIDE-ROUTE-TRANSITIONS

## 1. Identification
- Report: 614
- Task ID: APP-WIDE-ROUTE-TRANSITIONS
- Predecessor: CUSTOMER-MENU-STEP-TRANSITIONS (report 613)

## 2. Objective
Extend report 613's fade/slide-in entrance (customer menu steps) to every routed page in the app, so navigating between views feels consistently fluid, not just inside `/customer/menu`.

## 3. Modified Files
- `frontend/src/components/AnimatedOutlet.tsx` (new)
- `frontend/src/layouts/AccountantLayout.tsx`
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/layouts/CustomerLayout.tsx`
- `frontend/src/layouts/KitchenLayout.tsx`
- `frontend/src/layouts/PlatformLayout.tsx`
- `frontend/src/layouts/WaiterLayout.tsx`

## 4. What Changed?
- New shared `AnimatedOutlet` (wraps React Router's `Outlet` in a `div` keyed by `location.pathname`, classes `animate-in fade-in slide-in-from-bottom-2 duration-300` — same `tw-animate-css` utilities from report 613, still no new dependency). Keying by pathname forces the wrapper to remount and replay the animation on every navigation, while the **layout itself stays mounted** (nav bars, WebSocket `connect()`/`disconnect()` effects in `WaiterLayout`/`KitchenLayout`/`CustomerLayout` are untouched — only the routed page content re-enters).
- Every layout's `<Outlet />` replaced with `<AnimatedOutlet />`. `KitchenLayout`'s `<main>` is a stretching flex column (`flex flex-1 flex-col min-h-0`, needed so `OrdersDisplay`'s `h-full`/`flex-1` sizing works) — there `AnimatedOutlet` takes an explicit `className="flex flex-1 flex-col min-h-0"` to keep the page a direct flex child with the same stretch behavior; the other five layouts' `<main>` isn't a flex-stretch container, so the plain wrapper changes nothing about height resolution.
- Not touched (no shared layout/outlet to hook into): standalone pages like `Login`/`Register`, `TenantLanding`, and the customer join flow (`JoinByCode`/`MenuJoin`) — happy to add per-page entrance animations there too if wanted, but that's a different, page-by-page mechanism rather than this one shared wrapper.

## 5. Why It Changed?
Direct follow-up: "apliquemos estas animaciones en todas las vistas... para hacer más fluida la navegación." A single shared `AnimatedOutlet` centralizes the behavior instead of hand-editing every page component, and keying at the Outlet level (rather than the whole app root) avoids remounting layouts on every navigation, which would have caused needless WebSocket reconnect churn in Waiter/Kitchen/Customer.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- `cd frontend && pnpm run test:run` — 245/246 (67/68 files); the 1 failure is the pre-existing `MenuJoin.test.tsx` case, unrelated.
- Not visually verified on a device/browser.
