# Report 167 — EMB-i18N-02: Auth + Nav i18n

## 1. Identification
- **Report #:** 167
- **Task ID:** EMB-i18N-02
- **Predecessor Task:** EMB-i18N-01 (report 166)

## 2. Objective
Extract the remaining hardcoded strings on the auth screens (`Login.tsx`, `Register.tsx`) and the shared tenant-app chrome (`TopNav.tsx`, `FloatingNav.tsx`, `NotFound.tsx`, `ErrorBoundary.tsx`, `TenantSuspendedModal.tsx`) into the i18n dictionary system built in EMB-i18N-01, per Task 2 of `docs/superpowers/plans/2026-08-18-emb-i18n.md`.

## 3. Modified Files
- `frontend/src/locales/en/auth.ts` (new)
- `frontend/src/locales/es/auth.ts` (new)
- `frontend/src/locales/es/common.ts`
- `frontend/src/locales/en/common.ts`
- `frontend/src/locales/index.ts`
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/Register.tsx`
- `frontend/src/components/TopNav.tsx`
- `frontend/src/components/FloatingNav.tsx`
- `frontend/src/components/NotFound.tsx`
- `frontend/src/components/ErrorBoundary.tsx`
- `frontend/src/components/TenantSuspendedModal.tsx`

## 4. What Changed?
- New `auth` namespace registered in `locales/index.ts`. Because `Login.tsx`/`Register.tsx` are already English-only (unlike every other screen), `en/auth.ts` is the literal-extraction source and `es/auth.ts` is a newly authored Spanish translation `satisfies typeof enAuth` — the mirror image of every other namespace in this plan.
- `common` (es+en) extended with: `brandFallback`; TopNav's 5 button/search-placeholder keys; FloatingNav's 9 `navTables`/`navKitchen`/`navCash`/`navCategories`/`navAnalytics`/`navStaff`/`navSettings`/`navHome`/`navLogout` keys; `NotFound`'s 3 keys; `ErrorBoundary`'s 3 keys; `TenantSuspendedModal`'s 2 keys (its logout button reuses the existing `navLogout` key instead of a duplicate, since the text is identical).
- `Login.tsx`/`Register.tsx`: all JSX-rendered copy (titles, descriptions, placeholders, button/link text) now reads from `useTranslation('auth')` (+`'common'` for the shared "Ember" fallback). `loginSchema`/`registerSchema` and `toast.*` calls left untouched (deferred to EMB-i18N-08).
- `TopNav.tsx`: `buttonText`/`searchPlaceholder` branching now reads from `t(...)`; the clock's hardcoded `toLocaleTimeString('es', ...)` now switches to `'en-US'`/`'es-MX'` based on the active locale.
- `FloatingNav.tsx`: all 9 `title` attributes now read from `t(...)` (two separate `title="Home"` occurrences — customer menu link and customer home link — both updated).
- `NotFound.tsx`: heading/message/back-link now translated (the bare `404` digit itself was left as-is, no locale variance).
- `ErrorBoundary.tsx`: since it's a class component, hooks can't run directly in `render()`. Added a small functional `ErrorFallback` component that calls `useTranslation('common')` and renders the translated copy; the class's `render()` now returns `<ErrorFallback />` in the error state instead of inline JSX.
- `TenantSuspendedModal.tsx`: title, default fallback message, and logout button text now translated.

## 5. Why It Changed?
Continues the EMB-i18N backlog (Task 2 of the plan) — after EMB-i18N-01 shipped the infra and switcher with no page copy translated, this task migrates the auth + shared-nav surfaces so the language switcher actually changes visible text there. Zod/toast copy is deliberately deferred to EMB-i18N-08 per the plan's global constraints, to avoid mixing validation-message i18n into a plain string-extraction pass.

## 6. Verification
- `cd frontend && pnpm run build` → `tsc -b && vite build` succeeded, no errors (this also validates the `satisfies typeof` key-parity check across `auth`/`common`).
- `cd frontend && pnpm test:run` → 3 test files, 7 tests, all passed.
- **Gap (disclosed, matches prior-session convention):** no `claude-in-chrome` browser tool was available this session (confirmed via `ToolSearch`), and the backgrounded `pnpm run dev` process was reaped by the sandbox immediately after startup — no interactive click-through/switcher-toggle verification was performed. Verification for this task relied on the build's `satisfies` type-check (catches missing/extra dictionary keys) plus a `grep` pass confirming no targeted hardcoded literal remained in the 7 touched files. A real manual pass is still owed, consistent with the same gap noted in EMB-RV/EMB-PC sessions.
