# Report 681 — SETTINGS-SKELETONS

## 1. Identification
- Report: 681
- Task ID: SETTINGS-SKELETONS
- Predecessor: 680 (ADMIN-CASH-REGISTER-SKELETONS)

## 2. Objective
Make every Settings tab that loads data draw its own form while loading (frame of the real card, rows of that tab), with no real text; and cover the tabs that had no loading state at all.

## 3. Modified Files
- New: `frontend/src/pages/admin/components/settings/{LoyaltyRewardsSkeleton,PlanSkeleton}.tsx`
- Modified: `frontend/src/components/skeletons/SettingsFormSkeleton.tsx`, `.../settings/{Billing,BusinessHours,Hardware,Loyalty,Menu,PaymentGateway,Space,Ticket,Branding,Plan,LoyaltyRewards}Settings.tsx`; tests `.../settings/SettingsTabs.loading.test.tsx`, `frontend/src/components/skeletons/skeletons.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- `SettingsFormSkeleton` now draws the real card frame (round icon + title + description blocks, divider, the tab's rows, the two footer buttons) and takes a `layout` of row kinds: field, pair, triple, toggle, chips, rule, day, preview (plus `twoColumns` for the toggle tabs). The old `fields` prop remains as the default.
- Each tab declares its own layout: Hardware/Menu (2 toggle rows, 2 columns), Space (1 field), Payment gateway (toggle + 3 fields), Loyalty (toggle, select, pair, 3 tier thresholds), Billing (pair, toggle, tip chips, 2 tax rules), Business hours (7 day rows), Ticket (3 fields, 4 toggles, preview buttons), Branding (legal name, tax id + phone, address, opening/closing times, wifi, colour).
- **Loyalty rewards:** the whole card is blocks (title, description and "new reward" button included) with a 4-column table.
- **Plan** rendered nothing while loading (blank pane): now a read-only card of blocks (plan name + status chip, period end, detail rows, feature list; no footer).
- **Branding**, the tab a new admin lands on, had no loading state (the form showed empty values and then filled): now a form skeleton.

## 5. Why It Changed?
Same standards: frame of the real page, no real text, no empty-looking state while data loads.

### Decisions
- The **Settings sidebar stays real**. It is navigation, not data: it needs no query, and turning it into blocks on every tab switch would remove the controls the user is clicking. Only the tab content loads.
- **Printing** was left out on purpose: it has three queries (agents, printers per agent, jobs) and shows "no printers" while they load, so it needs its own pass. Info and Export are static.

### Verification
- New tests failed first (each tab's structure, rewards card, Branding, Plan). `pnpm exec vitest run src/pages/admin src/components/skeletons src/pages/loadingStates.test.tsx` 101/101; `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings); full suite 374/375 (only the known `MenuJoin` failure).

### NOT verified
- Not measured in the running app. The row layouts mirror each tab's structure from its code, but sizes are estimates, and the real tabs do not all share the same spacing, so some will shift a little when data arrives.
