# Internationalization & Language Switcher — Design Spec

**Date:** 2026-08-18
**Backlog prefix:** `EMB-i18N`
**Status:** Approved, pending implementation plan

## 1. Purpose

Every UI string in the frontend is a hardcoded Spanish literal — there is no i18n library installed (`frontend/package.json` has none) and no mechanism to change language. This spec adds a language switcher (dropdown, shown next to the "Ember" wordmark) that lets any user flip the whole app between Spanish and English, and the translation layer needed to back it.

Today the "Ember" wordmark itself is duplicated in five places with no shared header: `components/TopNav.tsx:69` (`AdminLayout`/`WaiterLayout`), `pages/customer/Menu.tsx:112`, `pages/kitchen/OrdersDisplay.tsx:57`, `pages/auth/Login.tsx:92`, and `layouts/PlatformLayout.tsx:11` (the separate `/console` app). This spec touches the first four; `/console` is explicitly out of scope (see §2).

## 2. Scope decisions (confirmed with user)

1. **Tenant frontend app only.** Customer, Waiter, Kitchen, Admin, and the shared Login/Register screens. The `/console` Platform Operator portal (EMB-PC) is a fully separate app (own auth store, own layout) and is explicitly excluded from this backlog.
2. **Two languages: Spanish (default) + English.** No other locales in v1.
3. **Client-side preference only, no backend involvement.** The chosen language is stored in `localStorage` via a Zustand `persist` store and applies instantly. It is NOT persisted to `User` or any tenant setting — every browser/session picks its own language independently. This keeps the backend, `identity` module, and `RestaurantSettings` completely untouched.
4. **No language auto-detection.** Default is always `es` on first visit (matching current behavior) until the user explicitly picks something else via the switcher. `navigator.language` is not consulted.
5. **Translation mechanism: a custom Zustand store + typed dictionaries, not react-i18next.** The project already uses Zustand + `persist` for equivalent state (`authStore`, `sessionStore`, `platformAuthStore`); with only two languages and no plural/ICU requirements, a ~40-line custom hook matches the existing codebase convention better than adding two new dependencies for features (namespaced lazy-loading, ICU pluralization) this project doesn't need.
6. **Form-validation messages (zod/react-hook-form) and toast copy (`react-hot-toast`) are in scope but sequenced last** (EMB-i18N-08, see §5) rather than mixed into each page's migration, since those strings are scattered across schemas/mutation callbacks rather than JSX and are more error-prone to touch alongside markup changes.

## 3. Architecture

### 3.1 Dictionaries

New `frontend/src/locales/` directory, one file per role-based namespace, mirroring the existing `pages/` split:

```
locales/
  es/common.ts     en/common.ts     (nav labels, buttons, generic actions: "Guardar", "Cancelar", "Buscar...")
  es/auth.ts        en/auth.ts       (Login/Register)
  es/customer.ts    en/customer.ts
  es/waiter.ts      en/waiter.ts
  es/kitchen.ts     en/kitchen.ts
  es/admin.ts       en/admin.ts
  index.ts
```

Each `es/*.ts` file exports a flat `const customer = { menuTitle: 'Ember', loadingCategories: 'Cargando categorías de Ember...', ... }` object — the Spanish literal IS the source of truth, so migrating a page is purely mechanical extraction, no translation work for ES. Each matching `en/*.ts` file is declared `satisfies typeof esCustomer` (importing the ES object's type) — `tsc -b` fails the build if an English namespace is missing a key or has an extra one. This is the only "test" needed for translation completeness, and it plugs directly into the repo's existing zero-tolerance TS-error policy.

`index.ts` assembles `{ es: { common, auth, customer, waiter, kitchen, admin }, en: { ... } }`.

### 3.2 Locale store

`store/localeStore.ts`, same shape as `authStore.ts`:

```ts
type LocaleState = {
  locale: 'es' | 'en'
  setLocale: (locale: 'es' | 'en') => void
}
```

Wrapped in Zustand's `persist` middleware, own `localStorage` key (e.g. `ember-locale`), default `'es'`. If the persisted value is missing or not a recognized locale, it falls back to `'es'` rather than throwing.

### 3.3 Translation hook

`lib/i18n.ts` exports `useTranslation(namespace: keyof typeof es)`:

```ts
const { t, locale, setLocale } = useTranslation('customer')
t('menuTitle')                        // -> 'Ember' / 'Ember' (brand name unchanged across locales)
t('itemsAdded', { count })            // -> '{{count}} platillos agregados', interpolated
```

`t()` looks up `dictionaries[locale][namespace][key]`. Interpolation is a simple `{{var}}` string-replace, sufficient for the handful of dynamic strings (counts, names) that exist today — no ICU/pluralization engine. If a key is somehow absent at runtime (shouldn't happen given §3.1's compile-time check), `t()` logs a `console.warn` in dev and returns the Spanish value as a safe fallback — it never throws or renders a raw key to the user.

### 3.4 Migration mechanics

Each page/component swaps its hardcoded literal for `t('key')`, and the corresponding key+value gets added to that namespace's `es/*.ts` (copy of the existing literal) and `en/*.ts` (English translation). No component restructuring beyond this substitution.

## 4. Language switcher component

`components/LanguageSwitcher.tsx`, built on the existing `components/ui/select.tsx` (Radix `Select`, already a dependency) rather than a new dropdown implementation:

- Trigger shows the active locale's abbreviation (`ES` / `EN`).
- Menu lists `Español` / `English`.
- Selecting a value calls `localeStore.setLocale(...)`; because every string is sourced reactively from the store via `useTranslation`, the whole app re-renders in the new language immediately, no page reload.

Inserted directly next to the existing wordmark in the four in-scope locations, without merging those headers into one shared component (avoids an unrelated layout refactor):

- `components/TopNav.tsx` (next to the `<h1>` at line 69 — covers Admin + Waiter)
- `pages/customer/Menu.tsx` (next to the `<h1>` at line 112)
- `pages/kitchen/OrdersDisplay.tsx` (next to the `<h1>` at line 57)
- `pages/auth/Login.tsx` (next to the `CardTitle` at line 92)

## 5. Phased rollout (implementation-plan granularity)

Given ~90 `.tsx` files, this ships as sequential sub-tasks, each its own PLAN→APPROVE→EXECUTE→REPORT→COMMIT cycle and squashed commit:

1. **EMB-i18N-01 — Infrastructure:** `locales/common.ts` (es+en), `localeStore.ts`, `lib/i18n.ts`, `LanguageSwitcher.tsx`, inserted in all 4 wordmark locations; smoke test (see §6). No full-page migrations yet — proves the mechanism end-to-end.
2. **EMB-i18N-02 — Auth + Nav:** `Login.tsx`, `Register.tsx`, `TopNav.tsx`'s remaining strings, `FloatingNav.tsx`'s `title` attributes.
3. **EMB-i18N-03 — Customer:** everything under `pages/customer/**` (Home, Menu, Bill, ComandaView, floating islands, modals).
4. **EMB-i18N-04 — Waiter:** `pages/waiter/**` (Tables, TableInformation, waiter CashRegister, charge/refund/void modals).
5. **EMB-i18N-05 — Kitchen:** `pages/kitchen/**` (OrdersDisplay, QueueCard, FocusedCard).
6. **EMB-i18N-06 — Admin core:** `pages/admin/**` excluding analytics/settings (Category, ListMenuItem, Staff, admin CashRegister).
7. **EMB-i18N-07 — Admin analytics & settings:** `pages/admin/analytics/components/**`, `pages/admin/components/settings/**` (isolated for size and because several format numbers/dates alongside labels).
8. **EMB-i18N-08 — Validation & toast copy:** zod schema messages and `react-hot-toast` strings across all roles, once the JSX layer is fully migrated.

Each task updates `PROGRESS.md`'s task queue and gets its own numbered report, per the existing convention used by EMB-CR/EMB-CLP/EMB-RV.

## 6. Testing strategy

- **Compile-time key-parity check** (§3.1's `satisfies`) is the primary safety net for translation completeness — no separate script needed.
- **New smoke test** in EMB-i18N-01, following the existing `test/Button.smoke.test.tsx` pattern (vitest + testing-library): renders `LanguageSwitcher`, selects English, asserts `localeStore.getState().locale === 'en'` and that a sample translated string updates.
- **Per-task verification:** `cd frontend && pnpm run build` (mandatory) plus a manual pass toggling the switcher on whatever pages that task migrated, confirming instant re-render and no missing-key warnings in the console.
- No backend tests are affected — `./mvnw test` is untouched by this backlog.

## 7. Explicit non-goals

- No i18n for the `/console` Platform Operator portal.
- No backend/database changes of any kind — no new `User` column, no tenant-level default language setting.
- No browser-language auto-detection.
- No third-party i18n library (react-i18next/FormatJS) — a custom store + typed dictionaries covers the two-locale, no-pluralization requirement.
- No merging of the five duplicated wordmark headers into one shared component — only the switcher itself is shared.
- No translation of validation/toast copy until EMB-i18N-08 (explicitly sequenced last, not skipped).
