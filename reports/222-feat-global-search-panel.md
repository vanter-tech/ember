# Report 222 — feat-global-search-panel

## 1. Identification
- **Report Number:** 222
- **Task ID:** feat-global-search-panel
- **Predecessor Task:** report 221 (bugfix-topnav-create-button-settings)

## 2. Objective
Make TopNav's search input a real global search: typing (admin only) surfaces matching categories, modifier groups, inventory items, staff, and direct shortcuts to Analíticas/Caja/Configuración tabs, in a dropdown panel that opens on click — regardless of which page the admin is currently on.

## 3. Modified Files
- `frontend/src/components/ui/popover.tsx` (new)
- `frontend/src/components/GlobalSearchResults.tsx` (new)
- `frontend/src/components/GlobalSearchResults.test.tsx` (new)
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/TopNav.tsx`
- `frontend/src/locales/es/common.ts`
- `frontend/src/locales/en/common.ts`

## 4. What Changed?
Added `components/ui/popover.tsx`, a shadcn-style wrapper over `radix-ui`'s `Popover` (same `data-slot`/animate-in-out convention as `dialog.tsx`, since no Popover primitive existed in this codebase yet). `uiStore.ts` gained `isGlobalSearchOpen`/`setGlobalSearchOpen`; `searchTerm`/`setSearchTerm` were left untouched so `Staff.tsx`'s existing live filter keeps working unmodified. `TopNav.tsx`'s search bar (admin role only) is now wrapped in `Popover`/`PopoverAnchor` — the input itself is unchanged (still bound directly to `searchTerm`, still reacts live as typed) and additionally opens the panel on click via `onClick`; `PopoverAnchor` (not `PopoverTrigger`) is used deliberately so clicking inside the already-open input to reposition the cursor doesn't toggle it closed — only an outside click, Escape, or selecting a result closes it (Radix's built-in dismiss behavior). New `GlobalSearchResults.tsx` renders inside `PopoverContent`: with an empty query it shows a one-line hint; otherwise it fires `useQuery`s (`enabled` only while the panel is open and the query is non-empty) reusing the exact query keys/service calls already used by `Category.tsx`/`ModifierGroups.tsx`/`Inventory.tsx`/`Staff.tsx` (categories fetched with `size=1000` instead of the paginated default of 6, since the panel needs the full set to search over), filters client-side by name, and groups results under Categorías/Modificadores/Inventario/Personal headers plus a Secciones group covering Analíticas, Caja, and all 11 Configuración tabs (reusing `SettingsBar.tsx`'s own label keys so wording never drifts). Clicking a result navigates to the matching page (settings entries also call `openSettings(tab)`) and closes the panel.

## 5. Why It Changed?
User-requested feature, refined over three rounds of discussion: first scoped to global search across Inventario (with its 3 tabs)/Analíticas/Personal/Caja/Configuración, then a full-screen modal was rejected in favor of a dropdown panel anchored under the existing input, with the input's live-typing behavior left exactly as it already worked. Slash-command syntax (`/...`) was explicitly deferred to a future task. `pnpm run build` PASS, `pnpm run test:run` 36/36 PASS (5 new tests) after the change.
