# EMB-FEAT-HUB — Ember Hub: waiter-managed seats (design)

**Date:** 2026-09-07
**Status:** approved, pre-plan
**Acronym:** `EMB-FEAT-HUB`
**Depends on:** PR #97 (guest table-join) merged — `LoyaltyAccrualListener` already
skips a participant whose `userId` is `null` after guest-join task T3. This spec
adds the matching guard to `LoyaltyAccountJoinListener`.

---

## 1. Context & problem

The Ember Hub is the on-prem build: the Spring Boot server runs on a machine inside
the restaurant's LAN (`SPRING_PROFILES_ACTIVE=hub`, portable Postgres/MinIO,
jpackage `.exe`), and the compiled SPA is served from the same origin at `/app/`.

The customer-facing flow — scan the table QR / type a 5-digit code, join a
collaborative real-time cart from your own phone — assumes the diner's phone can
reach the server. On the Hub it cannot: phones are on cellular or a guest Wi-Fi
that does not route to the LAN host. So the whole "customer joins the table"
subsystem is dead weight in the Hub build, and the QR / join-code the waiter
generates when assigning a table does nothing.

What the restaurant actually needs on the Hub is a **fully waiter-driven** table:
the waiter assigns the table, sets how many people are seated, gives each seat a
quick name so orders can be told apart, adds items from the existing per-table
"add item" screen and attributes each to a seat, then charges the table with the
split methods that already exist ("pago por consumo" / "pagos iguales").

## 2. Goals

- The Hub build has **no customer flow**: no customer login, no customer menu, no
  `/menu/join`, no QR / join-code UI. This is a build-time fork, not a runtime
  toggle.
- A waiter can open a table with N **name-only seats** — a `Participant` with
  `userId = null` and a name. Unnamed seats get `"Asiento 1"`, `"Asiento 2"`, …
- A waiter can **rename, add, and remove** seats while the table is open.
- Everything downstream — `waiter-items` attribution, `BY_CONSUMPTION` /
  `EQUAL_PARTS` split, per-split payment, leave-table redistribution, settle &
  close — works unchanged against name-only seats.
- Loyalty never engages for a name-only seat (no account, no points, no visits),
  and the loyalty admin UI is hidden in the Hub build.
- The Cloud build is behaviourally unchanged.

## 3. Non-goals

- No new Spring profile branching in the new endpoints. They are
  `hasRole('WAITER')` and harmless on Cloud; only the Hub frontend calls them.
- No DB migration. `Participant` lives in a JSON column and `userId` is already
  nullable.
- No "customer upgrades a seat to a real account", no guest→seat bridge, no
  per-seat device pairing. Out of scope.
- No full byte-level code-split of the customer bundle out of the Hub build —
  customer pages become lazy chunks that are never fetched on the Hub; removing
  the chunks entirely is a follow-up.
- Kitchen (KDS) and admin analytics are untouched.

## 4. Approach

Two independently testable halves, one plan:

**A. Backend — name-only seats.** Seed seats at session creation; three
WAITER-only endpoints to add / rename / remove a seat; a null-safety sweep of the
participant-matching code; the loyalty join guard; one new WS event.

**B. Frontend — Hub build fork.** Gate the customer subsystem out of the router
with `isHubBuild`; branch the "assign table" modal so the Hub variant collects
seat names instead of rendering a QR; add seat-management controls to the waiter
table-detail view; hide the loyalty admin section in the Hub build; new
`waiter`-namespace i18n strings.

The only Hub-vs-Cloud signal on the frontend is the one already in use:

```ts
const isHubBuild = import.meta.env.BASE_URL !== '/'
```

set by `vite build --base=/app/` (`pnpm run build:hub`, driven by
`ember-hub/build-frontend.ps1`). `App.tsx` already derives `routerBasename` from
it; `Login.tsx` already hides the self-registration link with it.

---

## 5. Backend design

### 5.1 Data model — unchanged

`com.vanter.ember.session.model.Participant` is a POJO in `Session.participants`
(a `@JdbcTypeCode(SqlTypes.JSON)` column):

```java
Participant { String userId; String name; }
```

A name-only seat is `Participant.builder().userId(null).name("Asiento 1").build()`.
No migration. `OrderItem.participantName`, `BillSplit.participantName`,
`Payment.participantName` are all already plain strings keyed by name — the split
math (`BillingService.splitByConsumption` groups by `OrderItem.participantName`;
`splitEqually` uses participant count) needs no change.

### 5.2 Seed seats at session creation

**`CreateSessionRequest`** gains an optional field:

```java
public record CreateSessionRequest(
        @NotNull UUID tableId,
        @Min(1) int maxParticipants,
        List<@Size(max = 50) String> seatNames   // optional; Hub sends it, Cloud omits it
) {}
```

**`SessionService.createSession`** signature becomes:

```java
public Session createSession(UUID tableId, String waiterId, int maxParticipants,
                             List<String> seatNames)
```

Behaviour:

- `seatNames` null / empty → **no participants seeded** (Cloud path, exactly as
  today — customers add themselves on join).
- `seatNames` non-empty:
  - `seatNames.size()` must be `<= maxParticipants` → else
    `IllegalArgumentException` (→ 409 via `GlobalExceptionHandler`).
  - Trimmed non-blank entries are used as-is; blank / missing slots up to
    `maxParticipants` are filled `"Asiento " + (i + 1)`.
  - Resulting names must be **unique** (case-sensitive, post-trim). A collision
    among *provided* names → `IllegalArgumentException` ("Nombres de asiento
    duplicados"). Auto-filled names never collide with each other; if a provided
    name equals an auto-name slot that would be generated, the provided name wins
    for its slot and the auto-slot generator skips already-taken names.
  - Seed one `Participant{userId:null, name}` per slot.
- **No per-seat `ParticipantJoined` on creation.** `SessionOpened` already fires
  and the waiter floor board (`Tables.tsx`) refetches on it; there is no customer
  session topic to feed. Seats appear when the waiter opens the table detail
  (fresh `GET /sessions/{id}`).

Existing call site `SessionController.createSession` passes
`request.seatNames()`. `SessionCreatedResponse(id, joinCode)` is unchanged — the
Hub frontend ignores `joinCode`.

### 5.3 Seat-management endpoints

All on `SessionController`, all `@PreAuthorize("hasRole('WAITER')")`, all require
the caller to be the session's assigned waiter (same check as
`addItemAsWaiter` / `getQrToken`) and the session to be `OPEN`.

#### `POST /sessions/{id}/participants`

Add one name-only seat.

```java
public record AddSeatRequest(@Size(max = 50) String name) {}   // name optional
```

- Blank / null `name` → `"Asiento " + (nextFreeIndex)` where `nextFreeIndex` is
  the lowest `n >= 1` such that `"Asiento n"` is not already a participant name.
- Non-blank `name` must be unique among current participant names → else
  `IllegalArgumentException`.
- Append the `Participant`; if `participants.size() > maxParticipants`, bump
  `maxParticipants` to match.
- Publish `ParticipantJoined(tenantId, id, null, name, false)` (5-arg form; the
  `guest` flag is `false`, `userId` is `null`).
- Return `SessionDetailResponseDto` (`sessionService.getSessionDetails(id)`).

#### `PATCH /sessions/{id}/participants`

Rename a seat.

```java
public record RenameSeatRequest(
        @NotBlank String from,
        @NotBlank @Size(max = 50) String to) {}
```

- Seat `from` must exist; `to` must be non-blank, `<= 50`, and unique among the
  other participant names → else `IllegalArgumentException`.
- **Blocked once a bill exists:** if
  `billRepository.findBySessionIdAndStatusNot(id, BillStatus.VOIDED).isPresent()`
  → `IllegalStateException` ("Recalculá la cuenta para renombrar asientos"). The
  waiter voids or settles first. This keeps the cascade small; the typo-fix use
  case is always pre-billing.
- Cascade (pre-bill, so only draft/kitchen items exist): rewrite every
  `OrderItem` in the session whose `participantName.equals(from)` to `to`, and
  every `SessionActivity` entry likewise. `OrderItem.participantId` is already
  `null` for a name-only seat — nothing to touch there.
- Persist, then publish a new event
  `ParticipantRenamed(tenantId, id, from, to)`.
- Return `SessionDetailResponseDto`.

#### `DELETE /sessions/{id}/participants/{name}`

Remove a name-only seat. `{name}` is path-encoded.

- The named participant must exist **and** have `userId == null` — a seat backed
  by a real account (should never happen on the Hub, but the endpoint is shared)
  is refused with `AccessDeniedException`.
- Reuse leave semantics (mirrors `SessionService.leaveSession`, keyed by name
  instead of user):
  - Remove the seat's `DRAFT` `OrderItem`s and publish a `DeleteItem` per
    discarded draft.
  - Remove the `Participant`.
  - `hasBillableItems` = any remaining item not `DRAFT`. If
    `participants.isEmpty() && !hasBillableItems` → close the session
    (`SessionClosed`). Otherwise persist and publish
    `ParticipantLeft(tenantId, id, null, name)`.
- The existing `ParticipantLeftListener` already reacts to `ParticipantLeft`:
  when a non-voided bill exists and that name has an `UNPAID` `BillSplit`, it
  calls `paymentService.redistributeSplit(billId, name)` — so removing a seat
  after the bill is calculated redistributes its share, unchanged.
- `maxParticipants` is left as-is (capacity ≥ seat count is fine).
- Return `SessionDetailResponseDto` (or `204` if the session was closed — return
  the DTO with `isOccupied=false` for a uniform client; the Hub UI navigates
  back to the table list on `CLOSED`, same as today).

### 5.4 New event: `ParticipantRenamed`

```java
package com.vanter.ember.session.event;

import java.util.UUID;

public record ParticipantRenamed(
        String type, UUID tenantId, String sessionId, String oldName, String newName) {
    public ParticipantRenamed(UUID tenantId, String sessionId, String oldName, String newName) {
        this("PARTICIPANT_RENAMED", tenantId, sessionId, oldName, newName);
    }
}
```

Wired into both WS listeners (same one-liner pattern as the other events):

- `SessionWebSocketListener` → `/topic/session/{sessionId}` (harmless on Cloud;
  used by the Hub waiter table-detail subscription).
- `WaiterWebSocketListener` → `/topic/waiter/{tenantId}` (floor board).

The Hub table-detail view (`TableInformation.tsx`) already subscribes to the
waiter session topic via `useWebsocketStore().subscribeToWaiterSession(id)`; on
`PARTICIPANT_RENAMED` it invalidates `['sessionDetails', id]` and `['bill', id]`.

### 5.5 Null-safety sweep

Name-only seats put `Participant` rows with `userId == null` into
`Session.participants`. Several participant-matching sites call
`p.getUserId().equals(x)` and would NPE. These are all customer-driven paths
(dead in the Hub build) but the endpoints are shared with Cloud, so harden them —
`java.util.Objects.equals` or flip to `x.equals(p.getUserId())` where `x` is
known non-null:

| File | Method | Line (approx) |
|---|---|---|
| `session/service/SessionService.java` | `joinSession` | 173 |
| `session/service/SessionService.java` | `joinSessionCode` | 211 |
| `session/service/SessionService.java` | `addItem` | 285 |
| `session/service/SessionService.java` | `isParticipant` | 611 |
| `session/service/SessionService.java` | `leaveSession` | 645 |
| `session/service/SessionService.java` | `resumeSession` | 692 |
| `session/controller/SessionController.java` | `getSession` (customer participant check) | 69 |

`rejectIfSeatedElsewhere` uses the derived query
`findByTenantIdAndParticipants_UserId(tenantId, userId)` — a JSON-column derived
query with a non-null `userId` argument; it will simply not match null-userId
rows, which is correct. No change.

### 5.6 Loyalty guard

`loyalty/listener/LoyaltyAccountJoinListener.handleParticipantJoined`:

```java
@EventListener
public void handleParticipantJoined(ParticipantJoined event) {
    if (event.guest()) { return; }
    if (event.userId() == null) { return; }   // name-only Hub seat — no loyalty account
    loyaltyAccountService.findOrCreate(event.tenantId(), event.userId());
}
```

`loyalty/listener/LoyaltyAccrualListener` already filters
`participant.getUserId() != null && !isGuest(...)` (guest-join T3) — a regression
test locks it.

### 5.7 Backend testing

- **`SessionServiceTest`** (`@ExtendWith(MockitoExtension.class)` — matches the
  existing style in this class's tests):
  - `createSession` with `seatNames = null` seeds no participants (Cloud parity).
  - `createSession` with 2 names + `maxParticipants = 4` seeds `["Ana", "Beto",
    "Asiento 3", "Asiento 4"]`.
  - `createSession` with `seatNames.size() > maxParticipants` → throws.
  - `createSession` with duplicate provided names → throws.
  - `addSeat` blank name picks the lowest free `"Asiento n"`.
  - `renameSeat` rewrites matching `OrderItem.participantName` and
    `SessionActivity.participantName`.
  - `renameSeat` with an existing non-voided bill → throws `IllegalStateException`.
  - `removeSeat` on a `userId != null` participant → `AccessDeniedException`.
  - `removeSeat` drops the seat's DRAFT items, keeps PENDING/READY, publishes
    `ParticipantLeft` with `userId = null` and the seat name.
- **`SessionControllerTest`** (`@WebMvcTest`): the three endpoints — WAITER role
  required (403 for CUSTOMER), `@Valid` failures → 400, `IllegalState` → 409.
  (`GuestUserService` `@MockBean` is already present from guest-join.)
- **Integration** (`@SpringBootTest @AutoConfigureMockMvc @TestPropertySource(
  properties = "ember.ratelimit.enabled=false")`), seeded with a Restaurant +
  waiter + one table:
  - `POST /sessions {tableId, maxParticipants:3, seatNames:["Ana","Beto"]}` →
    `GET /sessions/{id}` shows 3 participants, all `userId == null`.
  - `POST /sessions/{id}/waiter-items` for "Ana" and "Beto", then
    `POST /billing/sessions/{id}/bill {splitMethod:"BY_CONSUMPTION"}` → splits are
    keyed `"Ana"` / `"Beto"` with the right amounts.
  - `PATCH /sessions/{id}/participants {from:"Ana", to:"Ana G."}` before billing →
    the pending item's `participantName` is now `"Ana G."`.
  - `DELETE /sessions/{id}/participants/Beto` after billing with Beto's split
    `UNPAID` → Beto's share is redistributed onto the remaining split(s).
  - Regression: `GET /sessions/{id}` as the seeded waiter with name-only seats
    present does not 500 (the `getSession` participant check no longer NPEs).
- **`LoyaltyAccountJoinListenerTest`**: `ParticipantJoined` with `userId == null`
  → `verifyNoInteractions(loyaltyAccountService)`.

---

## 6. Frontend design (Hub build)

Every change below is gated by `isHubBuild` (defined once, imported, or
recomputed from `import.meta.env.BASE_URL` where a module already does so). Cloud
rendering paths are left byte-identical.

### 6.1 Strip the customer subsystem from the router

`frontend/src/App.tsx`:

- Convert the customer page imports to `lazy()` (same pattern as `ConsoleApp`):
  `Home`, `Menu`, `ComandaView`, `Bill`, `MenuJoin`, `CustomerLayout`. They land
  in their own chunk.
- Wrap the `/customer/*` `<Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>`
  block and the `/menu/join` `<Route>` in `{!isHubBuild && ( … )}`. When
  `isHubBuild`, those routes are never registered — a stray `/menu/join` on the
  Hub falls through to `<NotFound />`.
- `RoleRedirect`: add `if (isHubBuild && role === 'CUSTOMER') return <Navigate to="/login" replace />`
  (defensive; the Hub mints no customer tokens).
- Keep `routerBasename` logic as-is.

Export `isHubBuild` from a tiny shared module
(`frontend/src/lib/isHubBuild.ts`) so `App.tsx`, `Login.tsx` (migrate its local
copy), and the components below all import one definition. One-line module:

```ts
// True only in the Hub-bundled SPA (`vite build --base=/app/`, ember-hub/build-frontend.ps1).
export const isHubBuild = import.meta.env.BASE_URL !== '/'
```

### 6.2 Assign-table modal — Hub variant

`frontend/src/pages/waiter/components/ParticipantsQrModal.tsx` (component
`ParticipantQrModal`). Branch inside the component on `isHubBuild`; do **not**
fork the file.

- **Cloud:** unchanged — count picker, `createSession` → `getQrToken`, render
  `QRCodeSVG` + join code.
- **Hub:**
  - Keep the +/- count picker (drives `maxParticipants`).
  - Render `count` text inputs, each `placeholder={t('seatNamePlaceholder', { n })}`
    (`"Asiento {{n}}"`), value from a `string[]` state, all optional.
  - Submit → `SessionTableService.createSession(tableId, count, seatNames)` where
    `seatNames` is the trimmed array (may contain `''` for blank slots — backend
    fills them). No `getQrToken` call.
  - `onSuccess` → invalidate `['dashboardData']`, `toast.success(t('tableOpenedToast'))`,
    `closeModal()`, `navigate(\`/waiter/tables/${newSession.sessionId}\`)` so the
    waiter lands on the detail view to start ordering.
  - No `QRCodeSVG`, no join-code block, no `clientJoinUrl` (also sidesteps the
    known `window.location.origin` basename bug on the Hub).

`SessionTableService.createSession` signature grows a third optional arg:

```ts
createSession: async (
  tableId: string,
  maxParticipants: number,
  seatNames?: string[],
): Promise<CreateSession> => {
  const { data } = await api.post<CreateSession>('/sessions', {
    tableId, maxParticipants,
    ...(seatNames ? { seatNames } : {}),
  })
  return data
}
```

### 6.3 Seat management on the table-detail view

`frontend/src/pages/waiter/TableInformation.tsx` — the "Participantes" card. All
new controls gated by `isHubBuild` (Cloud keeps the read-only list).

- Card header gets a `+ {t('addSeatLabel')}` button →
  `openModal('SEAT_FORM', { sessionId: id, mode: 'add' })`.
- Each seat row gets two icon buttons (`Pencil`, `Trash2` from lucide-react):
  - Pencil → `openModal('SEAT_FORM', { sessionId: id, mode: 'rename', from: participant.name })`.
  - Trash → `openModal('DELETE_SEAT', { sessionId: id, name: participant.name })`
    (a confirm dialog; can reuse `GlobalDeleteModal`'s pattern or a small
    dedicated `RemoveSeatModal`).
  - Both disabled when `actionsDisabled` (session not `OPEN`).
- The participants card currently keys its `.map` on `participant.userId` — now
  `null` for every Hub seat. Change the key to `participant.name` (unique by
  construction).
- On WS `PARTICIPANT_RENAMED` / `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT` for
  this session, the existing `subscribeToWaiterSession` handler invalidates
  `['sessionDetails', id]` — confirm `PARTICIPANT_RENAMED` is added to the
  handled `type`s in `useWebsocketStore`.

New component `frontend/src/pages/waiter/components/SeatFormModal.tsx`:

- `mode: 'add'` → one text input, submit → `SessionTableService.addSeat(sessionId, name || undefined)`.
- `mode: 'rename'` → prefilled input, submit → `SessionTableService.renameSeat(sessionId, from, to)`.
- `onSuccess` → invalidate `['sessionDetails', sessionId]` + `['bill', sessionId]`,
  toast (`seatAddedToast` / `seatRenamedToast`), close.
- `onError` 409 on rename → `toast.error(t('renameBlockedBillExistsToast'))`;
  other → generic.

New `SessionTableService` methods:

```ts
addSeat: (sessionId, name?) => api.post(`/sessions/${sessionId}/participants`, { name }),
renameSeat: (sessionId, from, to) => api.patch(`/sessions/${sessionId}/participants`, { from, to }),
removeSeat: (sessionId, name) => api.delete(`/sessions/${sessionId}/participants/${encodeURIComponent(name)}`),
```

All three return the `SessionDetailResponseDto` shape (`infoSession`).

### 6.4 Add-item modal — no change

`AddItemModal.tsx` already builds its participant `<select>` from
`modalPayload.participants` filtered to those with a `name`, and posts
`participantName`. Auto-named seats satisfy `Boolean(p.name)`, so they appear in
the dropdown with no change. `TableInformation` already passes
`participants: sessionData?.participants ?? []` into the `ADD_ITEM` modal payload.

### 6.5 Hide the loyalty admin UI in the Hub build

- `frontend/src/components/SettingsBar.tsx` — `SETTINGS_NAV`: filter out the
  `FIDELIZACION` group node when `isHubBuild` (`.filter(n => !isHubBuild || !(n.kind === 'group' && n.group === 'FIDELIZACION'))` at render, or build the array conditionally).
- `frontend/src/pages/admin/Settings.tsx` — `renderContent()` `FIDELIZACION` /
  `LOYALTY_REWARDS` cases return `null` when `isHubBuild` (guard against a
  persisted `activeSettings` pointing there); harmless because the nav entry is
  gone.
- `frontend/src/components/GlobalSearchResults.tsx` — drop loyalty settings
  entries from the searchable set when `isHubBuild` (one `.filter`).
- The customer-side loyalty cards (`Home`, `Bill`) are already gone with the
  customer subsystem (6.1).

### 6.6 i18n

New keys in `frontend/src/locales/{es,en}/waiter.ts` (`waiter` namespace):

| key | ES | EN |
|---|---|---|
| `seatNamesLabel` | Nombres de los asientos (opcional) | Seat names (optional) |
| `seatNamePlaceholder` | Asiento {{n}} | Seat {{n}} |
| `addSeatLabel` | Agregar asiento | Add seat |
| `renameSeatTitle` | Renombrar asiento | Rename seat |
| `removeSeatTitle` | Quitar asiento | Remove seat |
| `removeSeatConfirm` | ¿Quitar a {{name}} de la mesa? | Remove {{name}} from the table? |
| `seatNameInputLabel` | Nombre del asiento | Seat name |
| `seatAddedToast` | Asiento agregado | Seat added |
| `seatRenamedToast` | Asiento renombrado | Seat renamed |
| `seatRemovedToast` | Asiento quitado | Seat removed |
| `seatErrorToast` | No se pudo actualizar el asiento | Could not update the seat |
| `renameBlockedBillExistsToast` | Recalculá la cuenta antes de renombrar asientos | Recalculate the bill before renaming seats |

Reuse existing `assignTableLabel`, `assignTableDescription`,
`selectParticipantCountLabel`, `tableOpenedToast`, `qrSavingLabel`.

### 6.7 Frontend testing (Vitest + Testing Library)

- **`ParticipantsQrModal` Hub branch** (`vi.stubEnv` / mock the `isHubBuild`
  module to `true`): renders `count` name inputs, no `QRCodeSVG`; submit calls
  `SessionTableService.createSession` with `(tableId, 2, ['Ana', ''])` and
  navigates to `/waiter/tables/<id>`.
- **`ParticipantsQrModal` Cloud branch** (module → `false`): unchanged — still
  renders the QR after `createSession` + `getQrToken`.
- **`SeatFormModal`**: `add` mode posts `addSeat`; `rename` mode posts
  `renameSeat`; a 409 on rename surfaces `renameBlockedBillExistsToast`.
- **`TableInformation` Hub branch**: seat rows show pencil + trash; header shows
  "Agregar asiento"; all hidden when the module → `false`.
- **`App` routing**: with `isHubBuild` → `true`, `/menu/join` renders
  `<NotFound />` (or: the route is absent). With `false`, it renders `MenuJoin`.
- Existing `TableInformation.*.test.tsx` and `AddItemModal.test.tsx` must stay
  green (they run with the default Cloud `BASE_URL`).

---

## 7. Rollout

- **No DB migration.** The backend changes ship in the next tagged `v*` release
  (the same release carries guest-join `V9`).
- The Hub `.exe` picks up the frontend changes on the next
  `ember-hub/build-frontend.ps1` → `mvnw package` → installer build.
- Cloud frontend (Cloudflare Pages, `build:pages`) is unaffected — every new
  branch is behind `isHubBuild`, which is `false` there.
- Backwards compatible: an existing Cloud client posting `POST /sessions` without
  `seatNames` gets exactly today's behaviour.

## 8. Assumptions (locked with the user 2026-09-07)

1. Seat names are entered at "asignar mesa" **and** editable afterwards
   (rename / add / remove while OPEN).
2. Rename is **blocked once a bill exists** (409, "recalculá la cuenta");
   pre-bill rename cascades `OrderItem.participantName` + `SessionActivity` only.
3. The assign-table modal on the Hub **replaces** the QR / join-code entirely.
4. The loyalty admin UI (Settings "Fidelización" group + reward catalog) is
   **hidden** in the Hub build.
5. Customer pages become `lazy()` chunks that are unreachable on the Hub — not a
   zero-byte removal. Full code-split is a follow-up, not this effort.
6. `maxParticipants` stays the seat-capacity number; `addSeat` bumps it when
   exceeded, `removeSeat` leaves it.

## 9. Follow-ups (not in this effort)

- True code-split: a Vite build flag / entry that excludes the customer chunk
  from the Hub bundle entirely.
- Per-seat running subtotal on the table-detail view (nice-to-have for the waiter).
- A "merge two seats" action (someone sat at the wrong seat).
- Revisit whether the Hub should keep a lightweight loyalty-by-phone-number
  capability later (explicitly dropped for now).

## 10. Decomposition & task outline (for the plan)

One plan, `EMB-FEAT-HUB`, tasks executed in order. **Backend half is
mergeable on its own** (endpoints are dormant until the Hub frontend calls them).

- **T1 — Seed seats at creation.** `CreateSessionRequest.seatNames`,
  `SessionService.createSession` 4-arg + seeding/validation, controller wiring.
  Tests: `SessionServiceTest` seeding cases + `SessionControllerTest`.
- **T2 — Null-safety sweep + loyalty join guard.** The 7 `Objects.equals` edits,
  `LoyaltyAccountJoinListener` null guard. Tests: `getSession` no-NPE regression,
  `LoyaltyAccountJoinListenerTest` null-userId case.
- **T3 — `ParticipantRenamed` event + WS wiring.** New record, both listeners,
  `useWebsocketStore` handled-type. (Backend test: event published; FE test
  deferred to T7.)
- **T4 — `POST /sessions/{id}/participants` (add seat).** Service + endpoint +
  tests.
- **T5 — `PATCH /sessions/{id}/participants` (rename) + `DELETE …/{name}`
  (remove).** Service (cascade + bill-exists guard + leave-semantics reuse) +
  endpoints + tests, including the redistribute-on-remove integration test.
- **T6 — Frontend: strip customer subsystem.** `lib/isHubBuild.ts`, `App.tsx`
  lazy + route gate, `Login.tsx` migrate to the shared module. Test: `/menu/join`
  absent when `isHubBuild`.
- **T7 — Frontend: assign-table Hub variant.** `ParticipantsQrModal` branch,
  `SessionTableService.createSession` 3rd arg, navigate-to-detail. Tests: both
  branches.
- **T8 — Frontend: seat management on `TableInformation`.** `SeatFormModal`,
  remove-seat confirm, `SessionTableService.addSeat/renameSeat/removeSeat`, card
  key fix, WS invalidation. Tests: `SeatFormModal`, `TableInformation` Hub
  branch.
- **T9 — Frontend: hide loyalty admin UI in the Hub build.** `SettingsBar`,
  `Settings`, `GlobalSearchResults`. Test: nav lacks "Fidelización" when
  `isHubBuild`.
- **T10 — i18n + report + squash.** New `waiter` keys ES/EN, `reports/NN-…`,
  `PROGRESS.md`, one squashed commit, PR.

Each task ends with `cd backend && ./mvnw test` (backend tasks) or
`cd frontend && pnpm run build` + `pnpm run lint` + `pnpm run test:run`
(frontend tasks).
