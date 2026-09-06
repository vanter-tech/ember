# Report 369 — Admin section sidebars: mobile popover menu (replaces the pill strip)

## 1. Identification
- **Report number:** 369
- **Current Task ID:** replace the mobile horizontal pill strip (report 368) for admin
  section sidebars — 11 Settings sections scrolled "endlessly"
- **Predecessor Task:** report 368 (admin sidebar mobile pill strip)

## 2. Objective
The `<md` pill strip from report 368 put every section in one horizontally-scrolling
row; with 11 Settings sections it felt endless. Replace it with a single button that
opens the section tree in a popover and closes on selection.

## 3. Modified Files
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/inventoryHub/components/InventoryHubBar.tsx`
- `frontend/src/pages/admin/cashRegister/components/CashRegisterBar.tsx`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## 4. What Changed?
Each `*Bar` now renders, below `md`, a full-width `variant="outline"` button showing the
active section's icon + label and a `ChevronsUpDown` affordance. It opens a shadcn
`Popover` (controlled `open` state) whose content is `w-[var(--radix-popover-trigger-width)]`
(matches the trigger) and scrolls internally (`max-h-[70vh]` for Settings). Picking a
section calls the existing handler and closes the popover. The `md+` vertical sidebar
(`hidden md:flex`), its groups and the collapse control are unchanged.

**`SettingsBar.tsx`** was refactored so the section tree has one source of truth:
- `LEAF` — `{ labelKey, Icon }` per `SettingsType`.
- `SETTINGS_NAV` — ordered `leaf` / `group` nodes (BILLING, HARDWARE, FIDELIZACION
  groups with their members), replacing the report-368 flat `SETTINGS_SECTIONS` array.
- `renderNavList(onNavigate?)` renders the full (non-collapsed) tree with the existing
  `expandedGroup` accordion behaviour; it is used by both the desktop sidebar's
  expanded state and the mobile popover (which passes `() => setMenuOpen(false)`).
- The desktop collapsed (icon-only) state now maps `SETTINGS_NAV` instead of repeating
  each button; `handleGroupClick` / `handleFlatClick` semantics are untouched.

**`InventoryHubBar.tsx`** / **`CashRegisterBar.tsx`** — small local `SECTIONS` arrays
drive the popover list (3 and 2 items); desktop nav bodies unchanged.

**i18n** — added `sectionsMenuLabel` (`Secciones` / `Sections`), used only as the
trigger label when no section is active (Inventory before a nested route resolves).

## 5. Why It Changed?
A horizontal scroll strip hides most of its items off-screen and gives no sense of how
many there are — bad for an 11-item list. A trigger + popover keeps the mobile footprint
to one line, shows the current section at rest, and presents the whole grouped tree
(desktop parity) on demand, closing itself once a choice is made. Consolidating the
Settings tree into `SETTINGS_NAV` means the mobile panel and the desktop sidebar can't
drift apart.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files).
- `pnpm run test:run` — 24 files, 78 tests pass (unchanged; no test renders these views).
