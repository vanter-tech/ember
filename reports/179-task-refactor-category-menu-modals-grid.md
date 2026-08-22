# Report 179 — refactor-category-menu-modals-grid

## Identification
- **Report number:** 179
- **Task ID:** refactor-category-menu-modals-grid
- **Predecessor Task:** merge-main-into-feature-kitchen-view (report 178)

## Objective
Restyle the admin Categories and Menu Items (platillos) modals — currently a plain vertical field cascade — into the same grid layout pattern already used for the staff and ticket-settings modals, and fix the "Editar categoría" button, which called `openModal('EDIT_CATEGORY', ...)` with no modal listening for that type.

## Modified Files
- `frontend/src/pages/admin/components/NewCategoryModal.tsx`
- `frontend/src/pages/admin/components/EditCategoryModal.tsx` (new)
- `frontend/src/pages/admin/Category.tsx`
- `frontend/src/pages/admin/components/NewMenuModal.tsx`
- `frontend/src/pages/admin/components/EditMenuModal.tsx`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## What Changed?
- `NewCategoryModal.tsx`: dialog widened `sm:max-w-md` → `sm:max-w-xl`, kept single-column (`space-y-5`) by explicit decision — `Category` only has name/description/image, and description (textarea) + image (file input) both need full width regardless, so a 2-column grid wouldn't change anything visually there.
- New `EditCategoryModal.tsx`: mirrors `NewCategoryModal` but for updates — listens on `activeModal === 'EDIT_CATEGORY'`, prefills from `modalPayload` (the clicked `CategoryResponse`), image optional on edit (same pattern as `EditMenuModal`'s existing image field), calls `categoryService.update` (already existed in `api.ts`, wired to `PUT /catalog/categories/{id}`, but was never connected to any modal). Mounted in `Category.tsx` next to `<NewCategoryModal />`. This is what actually fixes the dead "Editar categoría" button — the backend endpoint and frontend service call already existed, only the UI was missing.
- `NewMenuModal.tsx` / `EditMenuModal.tsx`: dialog widened to `sm:max-w-xl`, form wrapper switched from `space-y-5` to `grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-5`. Name + Price now sit side by side in row 1 (both short inputs); Description (textarea), the "Disponible"/`available` switch, Image (file input), and the `DialogFooter` all get `sm:col-span-2` to span the full width, since none of them work well split across two narrow columns.
- Incidental fix while touching that exact block: `EditMenuModal.tsx`'s `available` switch `FormField` had its `FormLabel` wired to `t('priceLabel')` (copy-paste leftover from the field above it) instead of `t('activateLabel')` — the switch was rendering "Precio" as its label instead of "Activar". Corrected to `activateLabel`.
- New i18n key `editCategoryDialogTitle` added to both `locales/es/admin.ts` and `locales/en/admin.ts`; every other label on the new modal reuses existing keys (`nameLabel`, `descriptionLabel`, `coverImageLabel`, `categoryNamePlaceholder`, `newCategoryDescriptionPlaceholder`, `cancelButton`, `savingButton`, `saveButton`).

## Why It Changed?
User request, made right after merging PR #45: "hay que hacer una refactorización de todos los modal que hay en admin/categories, y las de los platillos para hacer los modal grid" — the same cascade-to-grid pattern already applied to the staff (report 177) and ticket-settings (report 176) modals this session, extended to Categories and Menu Items. Scoped via brainstorming: user confirmed (a) worth fixing the dead edit-category button as part of this pass rather than leaving it broken, and (b) not to force a fake 2-column grid onto the category modal where there's nothing meaningful to pair with Name.

## Verification
- `cd frontend && pnpm run build` → `tsc -b && vite build` clean, no TS errors. No backend files touched this task, so `./mvnw test` not re-run.
- No browser click-through this session (no `claude-in-chrome` tool available) — disclosed gap, consistent with prior sessions.

## Branch note
This task started a new branch, `refactor/admin-modals-grid`, off the just-updated `main` (post PR #45 merge) — `feature/kitchen-view` is now fully merged and retired, kept around locally but no longer the active branch.
