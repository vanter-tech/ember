# Report 152

**Task ID:** EMB-CLP-07 — Admin reward-catalog frontend UI (create/edit/toggle-active)
**Predecessor Task:** EMB-CLP-06 — Admin reward-catalog endpoints (report 151)

## Objective
Give admins a UI to manage the loyalty reward catalog (`POST/GET/PATCH /loyalty/rewards`, shipped in EMB-CLP-06) — create rewards, edit their fields, and toggle `active` — inside the existing "Fidelización" Settings tab.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/uiStore.ts`
- `frontend/src/pages/admin/components/settings/LoyaltySettings.tsx`

## New Files
- `frontend/src/pages/admin/components/settings/loyalty/types.ts`
- `frontend/src/pages/admin/components/settings/loyalty/CreateRewardModal.tsx`
- `frontend/src/pages/admin/components/settings/loyalty/EditRewardModal.tsx`

## What Changed?
- `api.ts`: added hand-typed `LoyaltyTier`/`LoyaltyRewardResponse`/`CreateLoyaltyRewardRequest`/`UpdateLoyaltyRewardRequest` plus `loyaltyRewardService` (`list`/`create`/`update` against `/loyalty/rewards`), flagged as interim until `pnpm run openapi` is re-run — same convention as the existing `LoyaltySettings` type block.
- `uiStore.ts`: extended `ModalType` with `'CREATE_REWARD' | 'EDIT_REWARD'`.
- `loyalty/types.ts`: `TIER_LABELS`/`TIER_BADGE_CLASSNAMES` Spanish labels + badge colors for the 4 `LoyaltyTier` values.
- `loyalty/CreateRewardModal.tsx` / `EditRewardModal.tsx`: react-hook-form + zod dialogs mirroring `CreateStaffModal`/`EditStaffModal`. Create takes name/description/requiredTier; Edit adds an inline `active` `Switch` field (toggle-active happens through the edit form, same pattern as staff deactivation).
- `LoyaltySettings.tsx`: wrapped the existing config `Card` in a flex column and added a second `Card` listing all rewards in a `Table` (name/description, tier badge, active/inactive badge, per-row edit button) with a "Nueva recompensa" trigger; renders `<CreateRewardModal />`/`<EditRewardModal />`; new `['loyaltyRewards']` query, invalidated by both mutations.

## Why It Changed?
EMB-CLP-06 shipped the backend with no consumer; EMB-CLP-07 closes that gap using the smallest structural change (append to the already-existing Fidelización tab rather than a new nav route) and reuses this codebase's established CRUD-modal pattern (global `useUIStore` modal slot + react-hook-form/zod dialogs) instead of inventing a new one, keeping the reward catalog consistent with how Staff and other admin entities are managed.

## Verification
`cd frontend && pnpm run build` (`tsc -b && vite build`) — passed, no TypeScript errors.
