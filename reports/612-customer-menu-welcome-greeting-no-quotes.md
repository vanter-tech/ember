# Report 612 — CUSTOMER-MENU-WELCOME-GREETING-NO-QUOTES

## 1. Identification
- Report: 612
- Task ID: CUSTOMER-MENU-WELCOME-GREETING-NO-QUOTES
- Predecessor: CUSTOMER-MENU-WELCOME-CATEGORIES-COPY (report 611)

## 2. Objective
Drop the quotation marks around the restaurant name in the customer-menu welcome greeting added in report 611.

## 3. Modified Files
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
`welcomeGreeting`: `Bienvenido a "{{name}}"` → `Bienvenido a {{name}}` (ES), `Welcome to "{{name}}"` → `Welcome to {{name}}` (EN).

## 5. Why It Changed?
Direct copy feedback.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- Not visually verified on a device/browser.
