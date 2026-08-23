# EMB-MOD — Modificadores de producto — Design Spec

**Date:** 2026-08-22
**Backlog prefix:** `EMB-MOD`
**Status:** Approved, pending implementation plan
**Related:** SaaS feature-gap initiative (`PROGRESS.md`), sibling of EMB-GATEWAY/EMB-INV in the same backlog wave

## 1. Purpose

Today a `MenuItem` has a single fixed price and no way to capture per-order
customization (size, doneness, extras, exclusions). `AddItemRequest` only
carries `menuItemId`; `OrderItem`/`KitchenItem` (both Mongo, embedded in
`Session`/`KitchenOrder`) carry no modifier data. This spec adds reusable
modifier groups that admins attach to menu items, a selection UI for
customers, price adjustment at add-time, and propagation of the chosen
modifiers through to the Kitchen Display System and the printed kitchen
ticket.

This is gap `EMB-MOD` from the SaaS feature-gap audit (2026-08-22).

## 2. Scope decisions (confirmed with user)

1. **Three selection types per group**: `SINGLE_REQUIRED` (radio, exactly
   one), `MULTI_OPTIONAL` (checkbox, 0..∞), `MULTI_LIMITED` (checkbox, admin
   sets min/max). `minSelections`/`maxSelections` are stored as explicit
   columns on the group regardless of type, so cart validation is a single
   numeric range check with no type-specific branching.
2. **Groups are reusable across menu items** (many-to-many), not redefined
   per item — e.g. one "Término de cocción" group is created once and
   attached to every applicable dish.
3. **Options only add to price, never subtract or replace it.**
   `priceDelta >= 0`, always summed on top of `MenuItem.price`. No negative
   deltas, no absolute price override.
4. **min/max/required is fixed on the group**, not configurable per
   assignment. If a dish needs different rules for the "same" concept, it
   gets a separate group — keeps the join table free of duplicated
   business-rule columns.
5. **Group ↔ item relationship is an explicit join entity**
   (`MenuItemModifierGroup`, carrying `displayOrder`), not a bare
   `@ManyToMany` table — matches the project's existing convention for
   relations that need their own data (`BillSplit`, `CashMovement`), and
   leaves room to grow without a new migration.
6. **No in-cart editing of modifiers.** Changing a selection means deleting
   the `OrderItem` and re-adding it — matches the current cart API shape
   (add/delete only, no update endpoint) and keeps MOD-03/MOD-05 scope
   tight.
7. **Soft-deactivate only, never hard-delete** groups or options
   (`active` flag, same pattern as `MenuItem.available`). Deactivated
   options disappear from admin/customer selection but never retroactively
   change `OrderItem`s already placed, because those carry a price/name
   **snapshot**, not a live reference.

## 3. Backend data model

### 3.1 New entities (`catalog` module, Postgres/JPA, `V13__modifiers.sql`)

**`modifier_groups`**

| column | type | notes |
|---|---|---|
| `id` | `bigint PK identity` | |
| `tenant_id` | `uuid` | `@TenantId`, auto-filtered like `Category`/`MenuItem` |
| `name` | `varchar(100)` | e.g. "Término de cocción" |
| `selection_type` | `varchar(20)` | `SINGLE_REQUIRED`, `MULTI_OPTIONAL`, `MULTI_LIMITED` |
| `min_selections` | `int` | derived at creation from `selection_type`, stored explicitly |
| `max_selections` | `int`, nullable | `null` = unbounded (only valid for `MULTI_OPTIONAL`) |
| `active` | `boolean` | soft-deactivate, default `true` |

**`modifier_options`**

| column | type | notes |
|---|---|---|
| `id` | `bigint PK identity` | |
| `group_id` | FK → `modifier_groups(id)` | real JPA `@ManyToOne` |
| `name` | `varchar(100)` | e.g. "Término medio", "Extra queso" |
| `price_delta` | `numeric(10,2)` | `>= 0`, default `0` |
| `active` | `boolean` | default `true` |
| `display_order` | `int` | |

**`menu_item_modifier_groups`** (explicit join entity, per §2.5)

| column | type | notes |
|---|---|---|
| `id` | `bigint PK identity` | |
| `menu_item_id` | FK → `menu_items(id)` | |
| `group_id` | FK → `modifier_groups(id)` | |
| `display_order` | `int` | order groups appear when adding an item |

Unique constraint on `(menu_item_id, group_id)` — a group can't be attached
twice to the same dish.

`SINGLE_REQUIRED` forces `min_selections = max_selections = 1` at creation
time; the service rejects any other combination for that type. `active=false`
on a group or option removes it from admin/customer views but existing
`MenuItemModifierGroup` rows and `OrderItem` snapshots are untouched.

## 4. Backend CRUD admin (MOD-02)

New `catalog.controller.ModifierGroupController`, class-level
`@PreAuthorize("hasRole('ADMIN')")` (mirrors `CategoryController`):

| Method & path | Purpose |
|---|---|
| `GET /admin/modifier-groups` | list all groups (incl. inactive) with nested options, tenant-scoped |
| `POST /admin/modifier-groups` | create group + its options in one request |
| `PATCH /admin/modifier-groups/{id}` | edit name/type/min-max/`active` |
| `POST /admin/modifier-groups/{id}/options` | add an option |
| `PATCH /admin/modifier-groups/{id}/options/{optionId}` | edit an option |
| `DELETE /admin/modifier-groups/{id}/options/{optionId}` | sets `active=false`, never deletes the row |
| `PATCH /admin/catalog/menu-items/{id}/modifier-groups` | replace the assignment set (`[{groupId, displayOrder}]`) for a `MenuItem` |

`ModifierGroupService` validation: `minSelections <= maxSelections`;
`SINGLE_REQUIRED` rejects any payload that doesn't resolve to `min=max=1`;
`priceDelta < 0` rejected at the DTO layer (`@DecimalMin("0")`). The
menu-item assignment endpoint validates every `groupId` belongs to the
caller's tenant and is `active`.

Routes added to `SecurityAuditTest`'s 401 matrix, per existing convention.

## 5. Cart capture & pricing (MOD-03)

**`OrderItem`** (Mongo, embedded in `Session`) gains:

```java
private List<SelectedModifier> modifiers; // default empty list
```

**`SelectedModifier`** (new, `session.model`, embedded, not a separate
collection): `groupName`, `optionName`, `priceDelta` — a **snapshot** taken
at add-time, same principle already applied to `OrderItem.name`/`price`.
Later edits to the modifier catalog never retroactively change orders
already placed.

**`AddItemRequest`** gains `selectedOptionIds: List<Long>` (optional,
defaults to empty) — flat list of `ModifierOption` ids; the backend
regroups them by `groupId` for validation.

**`SessionService.addItem` validation**, before building the `OrderItem`:

1. Load the `MenuItem` and its active `MenuItemModifierGroup` assignments.
2. Group `selectedOptionIds` by `groupId`. For every assigned group, check
   `minSelections <= count <= maxSelections`.
3. Any `optionId` not belonging to a group assigned to this `MenuItem` (or
   belonging to another tenant) is rejected — same treatment as an invalid
   `menuItemId` today.
4. `OrderItem.price = MenuItem.price + Σ priceDelta` of the selected
   options.

Invalid selections throw a new `InvalidModifierSelectionException`, mapped
to `400` via the existing `GlobalExceptionHandler` (RFC 7807), consistent
with every other domain validation.

**`OrderItemDto`** (the REST-facing shape waiter/customer read) gains the
same `modifiers` field. The `ItemAdded` event already serializes the full
`List<OrderItem>`, so it needs no shape change — it inherits the new field
automatically.

## 6. Propagation to KDS and printed ticket (MOD-06)

Verified against current code: `KitchenService` builds each `KitchenItem` by
copying fields off `OrderItem` (name, participant, status — no price, by
design); `PrintingEventListener.renderKitchenPayload` prints `"- " +
item.getName()` per line directly from the `KitchenItemsConfirmed` event's
`OrderItem` list. The customer receipt (`renderReceiptPayload`) does **not**
itemize today (header/bill#/footer only) — that's a pre-existing gap, out of
scope here, not something this spec introduces.

Changes:

- **`KitchenItem`** gains `modifiers: List<String>` — flat display names
  (e.g. `"Término medio"`, `"Extra queso"`), no price, matching `KitchenItem`'s
  existing price-free shape.
- **`KitchenService`**, where `KitchenItem` is built from `OrderItem`: maps
  `item.getModifiers()` (`List<SelectedModifier>`) to that list of display
  strings.
- **`PrintingEventListener.renderKitchenPayload`**: after each item's name
  line, appends one indented line per modifier, reading `item.getModifiers()`
  directly off the `OrderItem` the event already carries.
- **Frontend KDS** (`KitchenLayout`/item card): renders `modifiers` as a
  sub-list under the dish name — purely visual, no WebSocket/store change.

## 7. Admin UI (MOD-04)

- New **"Modificadores"** tab in the admin catalog page (same level as
  Categories/Menu Items — this is catalog data, not tenant settings).
- `NewModifierGroupModal`/`EditModifierGroupModal` (mirrors
  `NewCategoryModal`/`EditCategoryModal`: `useUIStore` open/close,
  `react-hook-form` + `zod`, new `modifierGroupService.create/update` in
  `api.ts`). Form: name, selection type (drives min/max: locked for
  `SINGLE_REQUIRED`/`MULTI_OPTIONAL`, editable for `MULTI_LIMITED`),
  dynamic option list (name + `priceDelta`, "+ opción" button).
- `NewMenuModal`/`EditMenuModal` (MenuItem create/edit) gain a section to
  assign active `ModifierGroup`s to the dish, with simple up/down controls
  for `displayOrder` (no drag-and-drop library).
- `MenuItemResponse`/`MenuDTO` (backend) and their mirror in
  `backend-types.ts` gain:
  `modifierGroups: [{id, name, selectionType, minSelections, maxSelections, options:[{id, name, priceDelta}]}]`.

## 8. Customer UI (MOD-05)

Today `Menu.tsx`'s "+" button calls the add-item mutation directly with no
intermediate step. That stays unchanged for any dish with
`modifierGroups: []` — the majority of the menu keeps zero added friction.

For a dish with `modifierGroups.length > 0`, "+" opens a new
`SelectModifiersModal` (same shadcn/ui `Dialog` pattern as the rest of the
app) instead of calling the mutation directly:

- Renders each assigned group in `displayOrder`: `SINGLE_REQUIRED` →
  `RadioGroup`; `MULTI_OPTIONAL`/`MULTI_LIMITED` → checkboxes, disabling
  further checks once `maxSelections` is reached.
- Live total price (`item.price + Σ priceDelta` of the current selection)
  shown on the "Agregar ($X.XX)" button.
- "Agregar" stays disabled while any group with `minSelections > 0` hasn't
  met it.
- On confirm, calls `SessionTableService.addItem(sessionId, itemId,
  selectedOptionIds)` — the existing mutation gains the new parameter, same
  `onSuccess`/`onError` toast handling already in place.

## 9. Testing

- `ModifierGroupServiceTest` (backend): min/max validation, `SINGLE_REQUIRED`
  type coercion, `priceDelta` rejection.
- `SessionServiceTest`: cart-side validation matrix (missing required
  selection, exceeding max, option from an unassigned/foreign-tenant group),
  correct price computation.
- Errors surfaced through the existing `GlobalExceptionHandler` RFC 7807
  path — no new response shape.
- No new frontend component tests — consistent with the rest of the catalog
  UI, which has none today.
- New routes added to `SecurityAuditTest`.

## 10. Deferred (explicitly out of scope for v1)

- In-cart editing of already-added modifiers (delete + re-add only).
- Per-assignment override of a group's min/max/required.
- Negative or absolute-override price deltas.
- Modifier groups scoped to a `Category` rather than assigned per-`MenuItem`.
- Drag-and-drop reordering in the admin UI (simple up/down controls only).

## 11. Task breakdown (reference — full task list lives in `PROGRESS.md`)

MOD-01 model (`ModifierGroup`/`ModifierOption`/`MenuItemModifierGroup`) ·
MOD-02 backend CRUD admin · MOD-03 cart capture + price computation ·
MOD-04 admin UI · MOD-05 customer selector UI · MOD-06 propagate to KDS
and printed ticket.
