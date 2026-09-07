# Report 393 — in-app camera QR scanner

## 1. Identification
- **Report:** 393
- **Task ID:** Q4 — no in-app QR scanner view
- **Predecessor:** report 392 — feat(customer): QR-join landing page (Q3, PR #92)

## 2. Objective
Make the "Escanear código QR" option in `JoinTableModal` actually open the camera and read a
table QR, instead of rendering nothing.

## 3. Modified Files
- `frontend/package.json`, `frontend/pnpm-lock.yaml` — add `jsqr` (1.4.0)
- `frontend/src/pages/customer/components/QrScanner.tsx` (new)
- `frontend/src/pages/customer/components/QrScanner.test.tsx` (new)
- `frontend/src/lib/qrToken.ts` — `tokenFromScannedValue`
- `frontend/src/lib/qrToken.test.ts` (new)
- `frontend/src/pages/customer/components/JoinTableModal.tsx` — wire the `'QR'` branch
- `frontend/src/locales/{es,en}/customer.ts` — `qrScanner*` strings

## 4. What Changed?
- **`QrScanner`** — requests `getUserMedia({ video: { facingMode: 'environment' } })`, draws each
  frame to an offscreen `<canvas>` and runs `jsQR` on the `ImageData` via `requestAnimationFrame`.
  On a hit it calls `tokenFromScannedValue(result.data)`; a valid Ember QR (`…/menu/join?token=<jwt>`
  or a bare JWT) stops the loop and fires `onDecoded(token)`, anything else is ignored and
  scanning continues. `cameraCapable()` (checked in render, not via `setState` in an effect)
  gates on `navigator.mediaDevices.getUserMedia` + a secure context; failure states render a
  "use the 5-digit code" fallback message. The effect cleans up: cancels the RAF and stops every
  track on unmount.
- **`JoinTableModal`** — the previously-empty `opciones === 'QR'` branch now renders `<QrScanner>`
  (lazy-loaded, so `jsqr` ~132 kB is its own chunk, not in the main bundle) plus a "Volver"
  button. `onDecoded` → `closeModal()` + `navigate('/menu/join?token=…')`, handing off to the
  Q3 landing page which does the actual join.
- **`tokenFromScannedValue`** — pulls the `token` query param out of a scanned URL, or accepts a
  bare three-part JWT whose `sub` decodes; returns `null` for any other QR content.
- Tests: `qrToken.test.ts` (URL / bare JWT / non-Ember URL / garbage) and `QrScanner.test.tsx`
  (unsupported message when `getUserMedia` is absent; camera view + hint when present). `pnpm run
  build` clean, `lint` 0 errors, `test:run` **102/102**.

## 5. Why It Changed?
The scan option set `opciones` to `'QR'` but `JoinTableModal` had no matching render branch, so
clicking it did nothing. A camera scanner needs a decoder; `jsqr` is the minimal choice (~132 kB,
zero deps, MIT) and we keep the UI ourselves. It is lazy-loaded because the scanner is a rare,
mobile-only action and the decoder should not weigh on every page.

The scanner deliberately does no joining of its own — it decodes and routes to `/menu/join`
(built in Q3), so there is one join implementation and one place that prompts for a display
name. The diner using this modal is already authenticated, so `MenuJoin` skips straight to the
name prompt and the `POST /sessions/{id}/join` call.

Out of scope: the pre-existing `ParticipantsQrModal` bug where the encoded URL uses
`window.location.origin` and omits the `/app/` basename on the Hub build (cloud is served at `/`,
unaffected).
