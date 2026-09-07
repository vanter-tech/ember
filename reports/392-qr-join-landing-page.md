# Report 392 — QR-join landing page (`/menu/join`)

## 1. Identification
- **Report:** 392
- **Task ID:** Q3 — the QR from "asignar mesa" doesn't work
- **Predecessor:** report 391 — fix(customer): show the tax breakdown on the bill screen (Q2, PR #91)

## 2. Objective
Make the table QR actually join a diner to the session. The waiter's `ParticipantsQrModal`
encodes `${origin}/menu/join?token=<jwt>`, but no `/menu/join` route existed, so every scan
landed on `NotFound`.

## 3. Modified Files
- `frontend/src/lib/qrToken.ts` (new) — `sessionIdFromQrToken` + `PENDING_QR_TOKEN_KEY`
- `frontend/src/pages/customer/MenuJoin.tsx` (new)
- `frontend/src/pages/customer/MenuJoin.test.tsx` (new)
- `frontend/src/lib/api.ts` — `SessionTableService.joinSessionViaQr`
- `frontend/src/App.tsx` — public `/menu/join` route
- `frontend/src/pages/auth/navigateForRole.ts` — QR-token check ahead of the resume path
- `frontend/src/locales/{es,en}/customer.ts` — `qrJoin*` strings

## 4. What Changed?
- **`MenuJoin`** (public route, sibling of `/login`, outside `ProtectedRoute`):
  1. Reads `?token`, stores it in `sessionStorage` under `PENDING_QR_TOKEN_KEY`.
  2. No/garbled token → an "invalid or expired link" card.
  3. Not authenticated as `CUSTOMER` → `<Navigate to="/login">` (token already parked).
  4. Authenticated → a name field → `POST /sessions/{sessionId}/join` with `{ qrToken, userName }`
     (`sessionId` decoded from the token's `sub`), then `setAuth({ token })` +
     `setSession(session)` + clear the parked token + go to `/customer/menu`. 404 → "QR expired",
     409 → "already at another table", else generic — each bounces back to `/customer`.
- **`navigateForRole`** — the `CUSTOMER` branch now checks `PENDING_QR_TOKEN_KEY` first and, if
  set, returns the diner to `/menu/join` to finish, ahead of the existing resume-open-session
  logic.
- **`sessionIdFromQrToken`** — base64url-decodes the JWT payload and returns a non-empty string
  `sub` (the session id), else `null`.
- Tests: no token → invalid card; unauthenticated → parks token + redirects to `/login`;
  authenticated → name submit calls `joinSessionViaQr(sessionId, token, name)`. `pnpm run build`
  clean, `lint` 0 errors, `test:run` **94/94**.

## 5. Why It Changed?
The backend QR-join path was already complete: `POST /sessions/{id}/join`
(`JoinSessionRequest{qrToken, userName}` → `SessionService.joinSession` → `validateQrToken` +
`bindResolvedTenant` + `withRescopedToken`). The token's `sub` claim *is* the session id, so the
path `{id}` is only cosmetic. What was missing was purely the customer-side landing page and the
route for it.

The `/sessions/{id}/join` endpoint is `CUSTOMER`-only and there is no guest/anonymous auth
(`/auth` has only register/login/pin), so an unauthenticated scanner must log in first. Rather
than add anonymous accounts (a product + security decision, and the 5-digit code flow already
requires a real login), `MenuJoin` parks the token and routes through `/login`, and
`navigateForRole` completes the round trip. `/menu/join` has to be a public route: hanging it
under `/customer/*` would let `ProtectedRoute` redirect the scanner to `/login` *and drop the
`?token`* before the page could save it.

Out of scope: **Q4** (in-app camera scanner — the `'QR'` branch in `JoinTableModal` renders
nothing) and the pre-existing `ParticipantsQrModal` bug where the QR URL is built from
`window.location.origin`, which omits the `/app/` basename on the Hub build (cloud is served at
`/`, so it is unaffected).
