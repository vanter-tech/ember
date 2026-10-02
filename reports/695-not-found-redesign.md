# Report 695 — NOT-FOUND-REDESIGN

## 1. Identification
- **Report number:** 695
- **Task ID:** NOT-FOUND-REDESIGN
- **Predecessor task:** report 694 — RELEASE-V0.3.8

## 2. Objective
The 404 was loose text on a white page (red heading, blue link). Give it the identity of the login/register/join screens.

## 3. Modified Files
- `frontend/src/components/NotFound.tsx`
- `frontend/src/components/NotFound.test.tsx` (new)
- `frontend/src/locales/es/common.ts`, `frontend/src/locales/en/common.ts`

## 4. What Changed?
- `NotFound` now renders inside `JoinShell` (landing dot-grid and red glows backdrop, "Ember · Desarrollado por Vanter" footer, circular language picker), the same shell the customer join screens use.
- Login-style card (`max-w-md`, `py-10`, shadow, same fade/slide-in as routed views): large `#920703` "404", an `h1` heading, the message, the address that failed (`useLocation().pathname`, truncated) and a full-width primary button to `/`, which already redirects each role to its home or to `/login`.
- Copy: "Página no encontrada" / "La dirección que buscas no existe o fue movida." / "Ir al inicio" (and the English equivalents).

## 5. Why It Changed?
A wrong link is often a first impression (customer QR, shared URL); the page now looks like part of Ember and shows what address was requested, which also helps support.

## Verification
Frontend build exit 0, lint 0 errors, vitest 420/421 (known `MenuJoin` failure); 4 new tests. Checked in a browser at `/ruta/que-no-existe` (screenshot: card, brand footer, language picker). The tab was in the background, so the entry animation was finished by hand before the screenshot. Not checked on a phone viewport. The `JoinShell` stays in `pages/customer/components`; moving it to a shared place was left out to keep the change small.
