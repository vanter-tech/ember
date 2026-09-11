# Tenant Data CSV Export — Design

- **Date:** 2026-09-11
- **Branch:** to be created off `main` when implementation starts (`writing-plans`)
- **Status:** design approved in chat 2026-09-11, pending spec review → `writing-plans`
- **Related:** builds on `analytics/service/AnalyticsService.java` (reused, not modified)

## 1. Objective

Let a restaurant admin download their own business-performance data as a `.zip` of CSVs from a
new "Exportar datos" tab in `/admin/settings` — so a tenant that stops using Ember can keep a
record of how their business performed, without needing to ask anyone for it. Available at any
time (not gated behind a cancellation flow, which doesn't exist today).

Scope for this pass, per the approved design: **sales/billing** and **product/catalog
performance**. Tables/sessions and loyalty data are explicitly out of scope — revisit as their own
follow-up if requested.

## 2. Current state (verified)

- **No CSV/export infrastructure exists anywhere in the backend** — no CSV writer, no
  `Content-Disposition` attachment endpoint, no CSV library dependency. Greenfield.
- **No date-range picker exists in the frontend either.** `Analytics.tsx`
  (`pages/admin/analytics/Analytics.tsx`) and its `SalesChart.tsx` only expose a granularity
  toggle (Day/Week/Month/Year) for chart bucketing; nothing on the page actually sends `from`/`to`
  to the API today, even though the backend already accepts them. This feature will be the first
  UI in the app to build a `from`/`to` picker — a plain pair of native `<input type="date">` is the
  pragmatic choice; there is no existing calendar/date-range component to reuse.
- **Tenant scoping is automatic.** Every relevant entity (`Bill`, `Payment`, `MenuItem`,
  `Category`, `Session`) carries Hibernate's `@TenantId`, resolved from `TenantContextHolder` —
  reads are auto-filtered, no manual `WHERE tenant_id=` needed. A "dump this tenant's data" export
  is safe by construction once the query methods exist.
- **`AnalyticsService.getProducts(tenantId, from, to, limit)`** (`analytics/service/AnalyticsService.java:202`)
  already computes exactly the per-item performance this export needs — name, category, quantity
  sold, revenue, revenue share — by joining paid sessions' line items against the catalog. Passing
  `limit = null` returns the full list (see line ~280: `limit == null || limit >= products.size() ?
  products : ...`). **This export reuses that method directly — no new aggregation logic for the
  product side.**
- **No existing method lists raw `Bill` rows for a tenant+date-range.** The closest is
  `BillRepository.findPaidBillActivity(tenantId, from, to)` → `List<PaidBillActivity>`
  (`sessionId`, `total`, `createdAt`) — `PAID` bills only, used today by `getTables()`. The bills
  export needs its own method (see §3.1).
- **`Bill` has no stored subtotal/tax split** — `total` is the single tax-inclusive figure written
  at settle time (`billing/model/Bill.java`). Decomposing it into subtotal+tax would mean
  re-deriving it from the *current* `settings.billing.taxRate`, which would misreport any bill
  settled under a since-changed rate. **Decision: `ventas.csv` exports `total` only, no
  reconstructed tax column.**
- **`PaymentRepository.findByBillId(Long billId)`** already exists (`billing/repository/PaymentRepository.java:23`)
  but is per-bill, not batch — a new `findByBillIdIn(List<Long> billIds)` avoids N+1 across
  potentially thousands of bills.
- **Table number** is on `DiningTables.tableNumber` (Integer), joined via `Session.tableId` — same
  join `getTables()` already performs; this export mirrors it rather than adding a shortcut.
- Expected data volume: low-thousands to tens-of-thousands of rows per tenant even for an
  established restaurant (`SessionRepository.java:23` comment: "a few thousand sessions per
  tenant" at real-world scale). **Synchronous, in-memory generation is fine — no background job.**
- Settings tab pattern: `frontend/src/store/uiStore.ts` `SettingsType` + `frontend/src/components/SettingsBar.tsx`
  `LEAF`/`buildSettingsNav` + `frontend/src/pages/admin/Settings.tsx` `renderContent` switch — a
  flat leaf tab, the same shape as `INFO`, is enough (no sub-navigation needed for one export
  page).

## 3. Backend

### 3.1 New repository methods

`BillRepository`:
```java
List<Bill> findByTenantIdAndCreatedAtBetweenAndStatusInOrderByCreatedAtAsc(
        UUID tenantId, LocalDateTime from, LocalDateTime to, Collection<BillStatus> statuses);
```
Called with `statuses = List.of(PAID, VOIDED)` — **`OPEN` (still-in-progress) bills are excluded**:
an unsettled table isn't yet a historical fact about "how the business performed," and would show
up as a confusing `$0`/no-payments row. `VOIDED` bills ARE included (with their status visible) so
the export is the tenant's complete record, not a rosier-than-reality one.

`PaymentRepository`:
```java
List<Payment> findByBillIdIn(Collection<Long> billIds);
```

### 3.2 New `export` package

`export/service/ExportService.java` — one method, `byte[] buildTenantExportZip(UUID tenantId, LocalDateTime from, LocalDateTime to)`:

1. Resolve the window the same way `AnalyticsService` does (`from`/`to` optional, default whole
   history) — either duplicate the small `resolveWindow` helper or extract it to a shared spot if
   that's cleaner once written; judgment call for the implementation plan, not locked here.
2. **`ventas.csv`** — `billRepository.findByTenantIdAndCreatedAtBetweenAndStatusInOrderByCreatedAtAsc(...)`,
   then batch-load `paymentRepository.findByBillIdIn(billIds)` and group by `bill.id` in memory.
   For each `Bill`, join back to its `Session` (`sessionRepository.findByTenantIdAndIdIn`, same
   pattern `getProducts`/`getTables` use) for `tableId` → `DiningTables.tableNumber`. Columns:

   | column | source |
   |---|---|
   | `bill_id` | `Bill.id` |
   | `mesa` | `DiningTables.tableNumber` via `Session.tableId` (blank if the session/table was since deleted) |
   | `fecha` | `Bill.createdAt` |
   | `total` | `Bill.total` |
   | `estado` | `Bill.status` (`PAID`/`VOIDED`) |
   | `metodos_pago` | distinct `Payment.method` (`DIGITAL`/`PHYSICAL`) among that bill's `CONFIRMED` payments, comma-joined; empty for a `VOIDED` bill with no confirmed payment |
   | `participantes` | count of distinct `Payment.participantName` on that bill |

3. **`productos.csv`** — `analyticsService.getProducts(tenantId, from, to, null)`, one row per
   `ProductPerformance`: `nombre`, `categoria`, `unidades_vendidas`, `ingresos`, `porcentaje_ingresos`
   (`revenueShare`, a nice-to-have already computed for free).
4. Write both as CSV text (helper below), zip them with `java.util.zip.ZipOutputStream` into a
   `ByteArrayOutputStream`, return the bytes. In-memory end-to-end — no temp files, no streaming
   complexity, matching the "low volume" call in §2.

`export/util/CsvWriter.java` — a small (~20-line) hand-rolled helper, **no new dependency**:
`writeRow(List<String> fields)` that comma-joins fields, wrapping any field containing a comma,
double-quote, or newline in double quotes and doubling embedded quotes (the standard RFC 4180
escaping rule). CRLF line endings (`\r\n`) for maximum Excel compatibility. This is the one new
piece of genuinely new logic in the whole feature — everything else is reuse or a thin query.

### 3.3 New controller

`export/controller/ExportController.java`:
```
GET /admin/export     @PreAuthorize("hasRole('ADMIN')")
  ?from=<ISO date-time, optional>&to=<ISO date-time, optional>
-> 200, Content-Type: application/zip,
   Content-Disposition: attachment; filename="ember-export-<yyyy-MM-dd>.zip"
   body: ExportService.buildTenantExportZip(...)
```
Same `@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)` pattern
`AnalyticsController` already uses for `from`/`to`. Filename date is "today" (export-generation
date), not the data window — simpler, and avoids awkward filenames when `from`/`to` are omitted.

## 4. Frontend

### 4.1 New Settings tab

- `uiStore.ts` `SettingsType` `+= 'EXPORT'`.
- `SettingsBar.tsx`: new flat `LEAF` entry (icon: `Download` from `lucide-react`), added to
  `buildSettingsNav()` next to `INFO`.
- `Settings.tsx`: new `case 'EXPORT': return <ExportSettings />`.
- New `pages/admin/components/settings/ExportSettings.tsx`, Card-header layout matching
  `HardwareSettings.tsx`'s convention (icon circle + title + description).

### 4.2 `ExportSettings.tsx`

- Two native `<input type="date">` (from/to), both optional — empty means "no bound" on that side,
  matching the backend's own optionality. No new date-picker component; this is the first such UI
  in the app (see §2), so keep it minimal.
- "Descargar" `Button`, calls a new `exportService.downloadTenantData(from?, to?)` in `api.ts`:
  `axios.get('/admin/export', { params: { from, to }, responseType: 'blob' })`, then builds an
  object URL and clicks a temporary `<a download>` to trigger the browser's save dialog — this app
  has no prior file-download flow, so this is new plumbing, but it's a well-worn few lines with no
  real risk.
- Loading state on the button while the request is in flight (no progress bar — the whole response
  arrives at once, there's nothing granular to show).
- Error toast on failure (network error, unexpected 500) — no special handling needed for "no
  data in range," an empty CSV (headers only) is a perfectly fine, honest result.

## 5. Files touched (anticipated)

**Backend**
- `billing/repository/BillRepository.java` — new finder.
- `billing/repository/PaymentRepository.java` — new finder.
- `export/service/ExportService.java` — new.
- `export/util/CsvWriter.java` — new.
- `export/controller/ExportController.java` — new.
- Flyway: **none** (no schema change).

**Frontend**
- `store/uiStore.ts` — `SettingsType += 'EXPORT'`.
- `components/SettingsBar.tsx` — new leaf entry.
- `components/GlobalSearchResults.tsx` — `SETTINGS_TAB_LABEL_KEYS` needs the new leaf too (it's an
  exhaustive `Record`, TS will force this).
- `pages/admin/Settings.tsx` — new case + import.
- `pages/admin/components/settings/ExportSettings.tsx` — new.
- `lib/api.ts` — `exportService.downloadTenantData`.
- `locales/{es,en}/admin.ts` — new keys (tab label, card title/description, date labels, download
  button, error toast) — parity enforced by the existing `satisfies` check.

## 6. Testing

**Backend**
- `ExportServiceTest`: `ventas.csv` — correct rows for PAID + VOIDED bills in range, OPEN excluded;
  multi-payment bill shows both methods comma-joined; a bill with no confirmed payment (voided
  before payment) shows an empty `metodos_pago`; window defaults (`from`/`to` both null) cover full
  history. `productos.csv` — delegates to `AnalyticsService.getProducts` with `limit = null` and
  every returned `ProductPerformance` becomes exactly one row.
- `CsvWriterTest`: a field containing a comma, a field containing a double-quote, a field
  containing a newline — each escaped per RFC 4180; a plain field round-trips unquoted.
- `ExportControllerTest` (`@WebMvcTest`): non-ADMIN roles → 403; 200 with the right
  `Content-Type`/`Content-Disposition` headers for ADMIN; response body is a valid zip containing
  exactly `ventas.csv` and `productos.csv`.
- Full `./mvnw test` green.

**Frontend**
- `ExportSettings` RTL: date inputs update state; download button calls
  `exportService.downloadTenantData` with the current from/to (or `undefined` when left blank);
  error toast on a rejected request.
- `pnpm run build` + `pnpm run test:run` clean.

## 7. Decisions locked (were the open questions during design)

1. **Scope for this pass** — sales/billing + product/catalog only. Tables/sessions and loyalty
   data are out of scope, follow-up if requested.
2. **Availability** — always on in Settings, not gated behind a (nonexistent) cancellation flow.
3. **Packaging** — one `.zip` containing `ventas.csv` + `productos.csv`, one download button.
4. **Date range** — optional `from`/`to` picker, defaulting to full history.
5. **`ventas.csv` granularity** — one row per `Bill` (not per `Payment`/split).
6. **UI placement** — its own new "Exportar datos" Settings tab, not folded into "Información".
7. **Bill status filter** — `PAID` + `VOIDED` included (with a status column); `OPEN` excluded.
8. **No tax/subtotal column** — `Bill` doesn't store one, and reconstructing it from the *current*
   tax rate would misreport bills settled under a different historical rate.
9. **No new dependency** — CSV written by a small hand-rolled RFC-4180 helper; zipping via the
   JDK's `java.util.zip`. Revisit only if edge cases (embedded newlines, unusual encodings) turn
   out to need more than that.
10. **Synchronous generation** — no background job/async email; data volume doesn't warrant it.
