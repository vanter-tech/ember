# Report 379 — Customer home redesign: banner + how-it-works + help

## 1. Identification
- **Report number:** 379
- **Current Task ID:** customer home is sparse and goes blank when the customer isn't
  bound to a restaurant
- **Predecessor Task:** report 378 (FloatingNav active highlight horizontal clip)

## 2. Objective
Replace the placeholder-avatar layout with a banner card (~40% of the viewport) holding
the join-table action, and fill the rest with content that is useful whether or not the
customer is at a restaurant.

## 3. Modified Files
- `frontend/src/pages/customer/Home.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
`Home.tsx` is rebuilt as one component (the old `showDashboard` fork is gone):
- **Banner card** — `min-h-[40vh]`, brand gradient (`#8c1717 → #3b0a0a`) with a subtle
  radial-dot pattern, the bundled `@/assets/ember.png` centred, a welcome line + the
  customer's name (`homeGuestName` fallback), and the white **"Entrar a una mesa"**
  button beneath the image. The gradient/pattern is where the later placeholder-image
  picker will plug in.
- **Remaining space** — a grid, 1 column on mobile / 2 on desktop:
  - **¿Cómo funciona?** (always, full width): three steps — scan/enter code, join with
    your group, order and pay your share.
  - **Tus visitas** (only when `hasTenant`, i.e. the visits query succeeds): the existing
    visit list (date / amount / points), restyled, with its empty state.
  - **¿Necesitas ayuda?** (always; spans both columns when there's no visits card): short
    text + an outline button that reopens the join modal.
- Removed: the loyalty points/tier card and its `loyaltyAccount` query, the external
  `i.pravatar.cc` avatars, and the now-unused `Avatar` / `Badge` / tier imports.

New `customer` i18n keys (es + en): `homeGuestName`, `homeHowItWorksTitle`,
`homeStep1..3Title/Body`, `homeHelpTitle/Body/Cta`. Existing `homeWelcomeBack`,
`homeJoinTableCtaShort`, `loyaltyVisitsTitle`, `loyaltyNoVisitsRegistered`,
`loyaltyVisitPoints` are reused.

## 5. Why It Changed?
The old view showed a fake person's avatar and only had real content (the loyalty bar)
once the customer was tied to a restaurant — otherwise it was near-empty. A gradient
banner needs no external asset, works in every state, and the how-it-works + help cards
give a first-time customer something actionable while the visits card still appears for
returning ones. The loyalty points/tier card was dropped per the agreed scope.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`); the `@/assets/ember.png` import
  type-checks via the `vite/client` types already in `tsconfig.app.json`.
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files).
- `pnpm run test:run` — 24 files, 78 tests pass (no test renders customer Home).

## 7. Follow-up (not in this task)
Placeholder-image picker modal for the banner: design-generated gradient/pattern options,
choice persisted on the account (new backend field + endpoint).
