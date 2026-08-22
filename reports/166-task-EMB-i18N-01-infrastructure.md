# Report 166 — Task EMB-i18N-01

## 1. Identification
- **Report:** 166
- **Task ID:** EMB-i18N-01
- **Predecessor Task:** bugfix-customer-floating-islands-mobile-overlap (report 165)

## 2. Objective
Stand up the i18n infrastructure (locale store, typed dictionaries, translation hook) and insert a `LanguageSwitcher` next to the "Ember" wordmark in the 4 places it appears in the tenant app, per `docs/superpowers/plans/2026-08-18-emb-i18n.md` Task 1.

## 3. Modified Files
- Created: `frontend/src/locales/types.ts`
- Created: `frontend/src/locales/es/common.ts`
- Created: `frontend/src/locales/en/common.ts`
- Created: `frontend/src/locales/index.ts`
- Created: `frontend/src/store/localeStore.ts`
- Created: `frontend/src/lib/i18n.ts`
- Created: `frontend/src/components/LanguageSwitcher.tsx`
- Created: `frontend/src/test/LanguageSwitcher.smoke.test.tsx`
- Modified: `frontend/src/components/TopNav.tsx`
- Modified: `frontend/src/pages/customer/Menu.tsx`
- Modified: `frontend/src/pages/kitchen/OrdersDisplay.tsx`
- Modified: `frontend/src/pages/auth/Login.tsx`

## 4. What Changed?
- `locales/types.ts`: `Locale = 'es' | 'en'` and `TranslationVars` types.
- `locales/{es,en}/common.ts`: first dictionary namespace (`languageSwitcherLabel`, `languageSpanish`, `languageEnglish`); `en/common.ts` uses `satisfies typeof esCommon` (no `as const` on either side) so `tsc -b` enforces key parity.
- `locales/index.ts`: assembles `dictionaries.{es,en}.common` and exports `Namespace = keyof typeof dictionaries.es`.
- `store/localeStore.ts`: Zustand `persist` store (`ember-locale-storage` key), default `'es'`, mirrors `authStore.ts`'s shape.
- `lib/i18n.ts`: `useTranslation(namespace)` hook — reads/writes `localeStore`, guards against a corrupted localStorage value (anything not exactly `'en'` collapses to `'es'`), supports `{{var}}` interpolation, falls back to the ES string (dev-console-warns) on a missing key.
- `components/LanguageSwitcher.tsx`: `Select` (existing `ui/select.tsx`) bound to the store; trigger shows `ES`/`EN`.
- `test/LanguageSwitcher.smoke.test.tsx`: 2 tests (render + store-update), matching `Button.smoke.test.tsx`'s tier of coverage.
- Inserted `<LanguageSwitcher />` beside the wordmark in `TopNav.tsx` (admin/waiter chrome), `Menu.tsx` (customer), `OrdersDisplay.tsx` (kitchen), and top-right of the card in `Login.tsx`.
- Incidental: removed a pre-existing stray trailing-whitespace line in `OrdersDisplay.tsx` directly adjacent to the edited block (line was blank-with-whitespace, not part of any prior task).

## 5. Why It Changed?
First task of the EMB-i18N backlog (spec `docs/superpowers/specs/2026-08-18-emb-i18n-design.md`, plan `docs/superpowers/plans/2026-08-18-emb-i18n.md`): ships the reusable store/hook/dictionary/component scaffolding every later per-role task (EMB-i18N-02..08) will extend, plus proves the switcher renders at all 4 duplicated wordmark spots before any page content is migrated. No page copy is translated yet — that starts at EMB-i18N-02.

## Verification
- `pnpm test:run src/test/LanguageSwitcher.smoke.test.tsx` — 2 passed.
- `pnpm run build` (`tsc -b && vite build`) — green, no errors.
