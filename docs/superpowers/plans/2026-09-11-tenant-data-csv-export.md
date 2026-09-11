# Tenant Data CSV Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a restaurant admin download their sales/billing and product-performance history as a `.zip` of CSVs from a new "Exportar datos" tab in `/admin/settings`.

**Architecture:** A new `export` backend package (service + controller) reuses the existing tenant-scoped repositories and `AnalyticsService.getProducts` to build two hand-written CSVs (no new dependency), zips them in memory with the JDK's `java.util.zip`, and streams the bytes back as a `Content-Disposition: attachment` download. The frontend adds one new flat Settings tab with a date-range picker (the app's first — built from plain `<input type="date">`, no existing calendar component) and a download button that saves the response `Blob` via a temporary anchor.

**Tech Stack:** Spring Boot 3.5.14 (Java 17), Spring Data JPA / Hibernate multi-tenancy (`@TenantId`), React 19 + TypeScript, TanStack Query, shadcn/ui, Vitest + React Testing Library, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-11-tenant-data-csv-export-design.md`

## Global Constraints

- ADMIN role only (`@PreAuthorize("hasRole('ADMIN')")`), matching `AnalyticsController`.
- No new dependency: CSV is hand-written (RFC 4180 escaping), zipping via `java.util.zip.ZipOutputStream`.
- Synchronous, in-memory generation — no background job.
- One `.zip` containing exactly `ventas.csv` (one row per `Bill`, statuses `PAID` and `VOIDED` only — `OPEN` excluded) and `productos.csv` (one row per `ProductPerformance`, reusing `AnalyticsService.getProducts` unmodified).
- `ventas.csv` has no tax/subtotal column — `Bill` stores only a tax-inclusive `total`.
- `from`/`to` are optional; missing bounds default to the tenant's whole history up to now, the same rule `AnalyticsService.resolveWindow` uses (`EPOCH_FLOOR` = 1970-01-01, `to` = now).
- Feature is always available in Settings — not gated behind any cancellation flow.

---

## Task 1: `CsvWriter` — RFC 4180 CSV row writer

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/export/util/CsvWriter.java`
- Test: `backend/src/test/java/com/vanter/ember/export/util/CsvWriterTest.java`

**Interfaces:**
- Produces: `CsvWriter.writeRow(List<String> fields) -> String` — comma-joins the fields, CRLF-terminated, quoting only a field that contains a comma, double quote, or newline (embedded quotes doubled). A `null` field becomes an empty string. Used by Task 3.

- [ ] **Step 1: Write the failing tests**

```java
package com.vanter.ember.export.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void writeRow_plainFieldsAreNotQuoted() {
        assertThat(CsvWriter.writeRow(List.of("a", "b", "3"))).isEqualTo("a,b,3\r\n");
    }

    @Test
    void writeRow_fieldWithCommaIsQuoted() {
        assertThat(CsvWriter.writeRow(List.of("Lomo, saltado", "5")))
                .isEqualTo("\"Lomo, saltado\",5\r\n");
    }

    @Test
    void writeRow_fieldWithDoubleQuoteIsEscapedByDoublingIt() {
        assertThat(CsvWriter.writeRow(List.of("6\" pizza"))).isEqualTo("\"6\"\" pizza\"\r\n");
    }

    @Test
    void writeRow_fieldWithNewlineIsQuoted() {
        assertThat(CsvWriter.writeRow(List.of("line1\nline2"))).isEqualTo("\"line1\nline2\"\r\n");
    }

    @Test
    void writeRow_nullFieldBecomesAnEmptyString() {
        assertThat(CsvWriter.writeRow(Arrays.asList("a", null))).isEqualTo("a,\r\n");
    }

    @Test
    void writeRow_emptyListProducesJustTheLineEnding() {
        assertThat(CsvWriter.writeRow(List.of())).isEqualTo("\r\n");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=CsvWriterTest`
Expected: compile error / FAIL — `CsvWriter` does not exist yet.

- [ ] **Step 3: Write the minimal implementation**

```java
package com.vanter.ember.export.util;

import java.util.List;

/**
 * Minimal RFC 4180 CSV row writer — no external dependency, since this app's only CSV need is
 * two fixed-column files (see {@code ExportService}). A field is quoted only when it contains a
 * comma, a double quote, or a newline; embedded quotes are doubled. Rows are CRLF-terminated for
 * maximum Excel/Sheets compatibility.
 */
public final class CsvWriter {

    private CsvWriter() {}

    public static String writeRow(List<String> fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escape(fields.get(i)));
        }
        row.append("\r\n");
        return row.toString();
    }

    private static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuoting =
                field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
        if (!needsQuoting) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=CsvWriterTest`
Expected: PASS, 6/6.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/export/util/CsvWriter.java backend/src/test/java/com/vanter/ember/export/util/CsvWriterTest.java
git commit -m "feat(export): add dependency-free RFC 4180 CSV row writer"
```

---

## Task 2: Repository finders for the export

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTest.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTest.java`

**Interfaces:**
- Produces: `BillRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(UUID tenantId, LocalDateTime from, LocalDateTime to, Collection<BillStatus> statuses) -> List<Bill>`, ordered oldest-first. Used by Task 3.
- Produces: `PaymentRepository.findByBillIdIn(Collection<Long> billIds) -> List<Payment>`. Used by Task 3.

### 2.1 `BillRepository`

- [ ] **Step 1: Write the failing test**

Add to `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTest.java`, inside the `BillRepositoryTest` class (after the existing `findByStatus_returnsMatchingBills` test):

```java
    @Test
    void findByTenantIdAndCreatedAtBetweenAndStatusIn_returnsOnlyMatchingStatusesInRangeOldestFirst() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 14, 23, 59, 59);

        Bill paidInRange = billRepository.save(Bill.builder()
                .sessionId("sess-paid").total(new BigDecimal("50.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 8, 10, 12, 0)).build());
        Bill voidedInRange = billRepository.save(Bill.builder()
                .sessionId("sess-voided").total(new BigDecimal("20.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.VOIDED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 9, 0)).build());
        billRepository.save(Bill.builder()
                .sessionId("sess-open").total(new BigDecimal("30.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.of(2026, 8, 6, 9, 0)).build());
        billRepository.save(Bill.builder()
                .sessionId("sess-out-of-range").total(new BigDecimal("40.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 7, 1, 9, 0)).build());

        List<Bill> result = billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(
                com.vanter.ember.config.TenantIdentifierResolver.NO_TENANT,
                from, to, List.of(BillStatus.PAID, BillStatus.VOIDED));

        assertThat(result).extracting(Bill::getSessionId)
                .containsExactly("sess-voided", "sess-paid");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=BillRepositoryTest#findByTenantIdAndCreatedAtBetweenAndStatusIn_returnsOnlyMatchingStatusesInRangeOldestFirst`
Expected: compile error — the method doesn't exist on `BillRepository` yet.

- [ ] **Step 3: Add the method**

In `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`, add the import:

```java
import java.util.Collection;
```

(alongside the existing `import java.util.List;` / `import java.util.Optional;` / `import java.util.UUID;`), and add this method at the end of the interface, right after `findPaidBillActivity`:

```java
    /**
     * The tenant's bills between {@code from} and {@code to} (both inclusive) whose status is one
     * of {@code statuses}, oldest first — the row source for the business-data CSV export.
     * {@code OPEN} bills are deliberately excludable: an unsettled table isn't yet a fact about
     * "how the business performed." Carries the same deliberate {@code tenantId} predicate as
     * {@link #findActivityWindow}.
     */
    @Query(
            """
            select b
            from Bill b
            where b.tenantId = :tenantId
              and b.status in :statuses
              and b.createdAt >= :from
              and b.createdAt <= :to
            order by b.createdAt asc
            """)
    List<Bill> findByTenantIdAndCreatedAtBetweenAndStatusIn(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("statuses") Collection<BillStatus> statuses);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=BillRepositoryTest`
Expected: PASS, all tests in the class (including the 3 pre-existing ones).

### 2.2 `PaymentRepository`

- [ ] **Step 5: Write the failing test**

Add to `backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTest.java`, inside the `PaymentRepositoryTest` class (after `findByBillId_returnsAllPaymentsForBill`):

```java
    @Test
    void findByBillIdIn_returnsPaymentsAcrossMultipleBills() {
        Bill billA = savedBill();
        Bill billB = billRepository.save(Bill.builder()
                .sessionId("sess-2").total(new BigDecimal("20.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.PAID)
                .createdAt(LocalDateTime.now()).build());
        paymentRepository.save(Payment.builder()
                .bill(billA).participantName("Ana").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.DIGITAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.now()).build());
        paymentRepository.save(Payment.builder()
                .bill(billB).participantName("Beto").amount(new BigDecimal("20.00"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.now()).build());

        List<Payment> result = paymentRepository.findByBillIdIn(List.of(billA.getId(), billB.getId()));

        assertThat(result).hasSize(2);
    }
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PaymentRepositoryTest#findByBillIdIn_returnsPaymentsAcrossMultipleBills`
Expected: compile error — `findByBillIdIn` doesn't exist yet.

- [ ] **Step 7: Add the method**

In `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`, add the import:

```java
import java.util.Collection;
```

and add this derived-query method right after `findByBillId`:

```java
    /** Batch form of {@link #findByBillId}, for the business-data CSV export. */
    List<Payment> findByBillIdIn(Collection<Long> billIds);
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=PaymentRepositoryTest`
Expected: PASS, all tests in the class.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTest.java backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTest.java
git commit -m "feat(export): add repository finders for the bills CSV export"
```

---

## Task 3: `ExportService`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/export/service/ExportService.java`
- Test: `backend/src/test/java/com/vanter/ember/export/service/ExportServiceTest.java`

**Interfaces:**
- Consumes: `CsvWriter.writeRow(List<String>) -> String` (Task 1); `BillRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(...)` and `PaymentRepository.findByBillIdIn(...)` (Task 2); `SessionRepository.findByTenantIdAndIdIn(UUID, Collection<String>) -> List<Session>` (existing); `DiningTableRepository.findByRestaurantIdAndIdIn(UUID, Collection<UUID>) -> List<DiningTables>` (existing); `AnalyticsService.getProducts(UUID, LocalDateTime, LocalDateTime, Integer) -> AnalyticsProductsResponse` (existing, called with `limit = null`).
- Produces: `ExportService.buildTenantExportZip(UUID tenantId, LocalDateTime from, LocalDateTime to) -> byte[]` — a zip file's bytes containing `ventas.csv` and `productos.csv`. Throws `IllegalArgumentException` if `from` is after `to`. Used by Task 4.

- [ ] **Step 1: Write the failing tests**

```java
package com.vanter.ember.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.ProductPerformance;
import com.vanter.ember.analytics.service.AnalyticsService;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 14, 23, 59, 59);

    @Mock BillRepository billRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SessionRepository sessionRepository;
    @Mock DiningTableRepository diningTableRepository;
    @Mock AnalyticsService analyticsService;

    @InjectMocks ExportService exportService;

    private static Map<String, String> unzip(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static AnalyticsProductsResponse emptyProducts() {
        return new AnalyticsProductsResponse(FROM, TO, BigDecimal.ZERO, 0L, 0, List.of(), List.of());
    }

    private void stubEmptyBillsAndProducts() {
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), any(), any(), any()))
                .thenReturn(List.of());
        when(paymentRepository.findByBillIdIn(any())).thenReturn(List.of());
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of());
        when(analyticsService.getProducts(eq(TENANT_ID), any(), any(), eq(null))).thenReturn(emptyProducts());
    }

    @Test
    void buildTenantExportZip_containsBothCsvFilesEvenWhenEmpty() throws IOException {
        stubEmptyBillsAndProducts();

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        Map<String, String> entries = unzip(zip);
        assertThat(entries).containsKeys("ventas.csv", "productos.csv");
        assertThat(entries.get("ventas.csv"))
                .isEqualTo("bill_id,mesa,fecha,total,estado,metodos_pago,participantes\r\n");
        assertThat(entries.get("productos.csv"))
                .isEqualTo("nombre,categoria,unidades_vendidas,ingresos,porcentaje_ingresos\r\n");
    }

    @Test
    void buildTenantExportZip_ventasCsv_oneRowPerBillWithTableAndDistinctConfirmedPaymentMethods()
            throws IOException {
        Bill bill = Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("50.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 0)).build();
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), eq(FROM), eq(TO), any()))
                .thenReturn(List.of(bill));

        Payment confirmedCash = Payment.builder()
                .bill(bill).participantName("Ana").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 5)).build();
        Payment confirmedDigital = Payment.builder()
                .bill(bill).participantName("Beto").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.DIGITAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 6)).build();
        Payment pendingDigital = Payment.builder()
                .bill(bill).participantName("Cara").amount(new BigDecimal("10.00"))
                .method(PaymentMethod.DIGITAL).status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 7)).build();
        when(paymentRepository.findByBillIdIn(List.of(1L)))
                .thenReturn(List.of(confirmedCash, confirmedDigital, pendingDigital));

        UUID tableId = UUID.randomUUID();
        Session session = Session.builder()
                .id("sess-1").tenantId(TENANT_ID).tableId(tableId).status(SessionStatus.CLOSED)
                .maxParticipants(4).createdAt(LocalDateTime.of(2026, 8, 5, 19, 30)).build();
        when(sessionRepository.findByTenantIdAndIdIn(TENANT_ID, List.of("sess-1"))).thenReturn(List.of(session));

        DiningTables table = DiningTables.builder().id(tableId).restaurantId(TENANT_ID).tableNumber(7).build();
        when(diningTableRepository.findByRestaurantIdAndIdIn(TENANT_ID, List.of(tableId)))
                .thenReturn(List.of(table));

        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(emptyProducts());

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        String[] lines = unzip(zip).get("ventas.csv").split("\r\n");
        assertThat(lines[0]).isEqualTo("bill_id,mesa,fecha,total,estado,metodos_pago,participantes");
        // Only the two CONFIRMED payments count for methods/participants; PENDING is excluded.
        assertThat(lines[1]).isEqualTo("1,7,2026-08-05T20:00,50.00,PAID,DIGITAL/PHYSICAL,2");
    }

    @Test
    void buildTenantExportZip_ventasCsv_billWithNoConfirmedPaymentHasEmptyMethodsAndZeroParticipants()
            throws IOException {
        Bill voidedBill = Bill.builder()
                .id(2L).sessionId("sess-2").total(new BigDecimal("15.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.VOIDED)
                .createdAt(LocalDateTime.of(2026, 8, 6, 13, 0)).build();
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), eq(FROM), eq(TO), any()))
                .thenReturn(List.of(voidedBill));
        when(paymentRepository.findByBillIdIn(List.of(2L))).thenReturn(List.of());
        when(sessionRepository.findByTenantIdAndIdIn(TENANT_ID, List.of("sess-2"))).thenReturn(List.of());
        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(emptyProducts());

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        String[] lines = unzip(zip).get("ventas.csv").split("\r\n");
        assertThat(lines[1]).isEqualTo("2,,2026-08-06T13:00,15.00,VOIDED,,0");
    }

    @Test
    void buildTenantExportZip_productosCsv_oneRowPerProductPerformance() throws IOException {
        stubEmptyBillsAndProducts();
        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(new AnalyticsProductsResponse(
                FROM, TO, new BigDecimal("100.00"), 5L, 1,
                List.of(new ProductPerformance(
                        4L, "Lomo saltado", 2L, "Fondos", 5L,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"))),
                List.of()));

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        String[] lines = unzip(zip).get("productos.csv").split("\r\n");
        assertThat(lines[1]).isEqualTo("Lomo saltado,Fondos,5,100.00,100.00");
    }

    @Test
    void buildTenantExportZip_missingBoundsDefaultToTheWholeHistoryUpToNow() {
        stubEmptyBillsAndProducts();
        LocalDateTime beforeCall = LocalDateTime.now();

        exportService.buildTenantExportZip(TENANT_ID, null, null);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(billRepository).findByTenantIdAndCreatedAtBetweenAndStatusIn(
                eq(TENANT_ID), from.capture(), to.capture(), any());

        assertThat(from.getValue()).isEqualTo(ExportService.EPOCH_FLOOR);
        assertThat(to.getValue()).isAfterOrEqualTo(beforeCall);
    }

    @Test
    void buildTenantExportZip_invertedWindowThrows() {
        assertThatThrownBy(() -> exportService.buildTenantExportZip(TENANT_ID, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=ExportServiceTest`
Expected: compile error — `ExportService` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.vanter.ember.export.service;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.ProductPerformance;
import com.vanter.ember.analytics.service.AnalyticsService;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.export.util.CsvWriter;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the tenant business-data export: a zip of {@code ventas.csv} (one row per {@code PAID}
 * or {@code VOIDED} bill) and {@code productos.csv} (delegates entirely to
 * {@link AnalyticsService#getProducts}, the same computation the admin dashboard's product-
 * performance view already uses, just with no top-N {@code limit}).
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    /** Same sentinel {@link AnalyticsService} uses for "the tenant's whole history". */
    static final LocalDateTime EPOCH_FLOOR = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final SessionRepository sessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public byte[] buildTenantExportZip(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime windowStart = from == null ? EPOCH_FLOOR : from;
        LocalDateTime windowEnd = to == null ? LocalDateTime.now() : to;
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("Export range 'from' must not be after 'to'");
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("ventas.csv", buildVentasCsv(tenantId, windowStart, windowEnd));
        files.put("productos.csv", buildProductosCsv(tenantId, windowStart, windowEnd));
        return zip(files);
    }

    private byte[] buildVentasCsv(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        List<Bill> bills = billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(
                tenantId, from, to, List.of(BillStatus.PAID, BillStatus.VOIDED));

        List<Long> billIds = bills.stream().map(Bill::getId).toList();
        Map<Long, List<Payment>> paymentsByBillId = paymentRepository.findByBillIdIn(billIds).stream()
                .collect(Collectors.groupingBy(p -> p.getBill().getId()));

        List<String> sessionIds = bills.stream().map(Bill::getSessionId).distinct().toList();
        Map<String, Session> sessionsById = sessionRepository.findByTenantIdAndIdIn(tenantId, sessionIds).stream()
                .collect(Collectors.toMap(Session::getId, s -> s));

        List<UUID> tableIds = sessionsById.values().stream()
                .map(Session::getTableId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Integer> tableNumbersById =
                diningTableRepository.findByRestaurantIdAndIdIn(tenantId, tableIds).stream()
                        .collect(Collectors.toMap(DiningTables::getId, DiningTables::getTableNumber));

        StringBuilder csv = new StringBuilder();
        csv.append(CsvWriter.writeRow(
                List.of("bill_id", "mesa", "fecha", "total", "estado", "metodos_pago", "participantes")));

        for (Bill bill : bills) {
            Session session = sessionsById.get(bill.getSessionId());
            UUID tableId = session == null ? null : session.getTableId();
            Integer tableNumber = tableId == null ? null : tableNumbersById.get(tableId);

            List<Payment> confirmedPayments = paymentsByBillId.getOrDefault(bill.getId(), List.of()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                    .toList();
            String methods = confirmedPayments.stream()
                    .map(p -> p.getMethod().name())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("/"));
            long participantCount = confirmedPayments.stream()
                    .map(Payment::getParticipantName)
                    .distinct()
                    .count();

            csv.append(CsvWriter.writeRow(List.of(
                    String.valueOf(bill.getId()),
                    tableNumber == null ? "" : String.valueOf(tableNumber),
                    bill.getCreatedAt().toString(),
                    bill.getTotal().toString(),
                    bill.getStatus().name(),
                    methods,
                    String.valueOf(participantCount))));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildProductosCsv(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        AnalyticsProductsResponse products = analyticsService.getProducts(tenantId, from, to, null);

        StringBuilder csv = new StringBuilder();
        csv.append(CsvWriter.writeRow(
                List.of("nombre", "categoria", "unidades_vendidas", "ingresos", "porcentaje_ingresos")));

        for (ProductPerformance product : products.products()) {
            csv.append(CsvWriter.writeRow(List.of(
                    product.name(),
                    product.categoryName() == null ? "" : product.categoryName(),
                    String.valueOf(product.quantitySold()),
                    product.revenue().toString(),
                    product.revenueShare().toString())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] zip(Map<String, byte[]> files) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(file.getKey()));
                zos.write(file.getValue());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=ExportServiceTest`
Expected: PASS, 6/6.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/export/service/ExportService.java backend/src/test/java/com/vanter/ember/export/service/ExportServiceTest.java
git commit -m "feat(export): add ExportService building the tenant CSV zip"
```

---

## Task 4: `ExportController`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/export/controller/ExportController.java`
- Test: `backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java`

**Interfaces:**
- Consumes: `ExportService.buildTenantExportZip(UUID, LocalDateTime, LocalDateTime) -> byte[]` (Task 3).
- Produces: `GET /admin/export?from=&to=` (ADMIN-only) → `200`, `Content-Type: application/zip`, `Content-Disposition: attachment; filename="ember-export-<yyyy-MM-dd>.zip"`, body = the zip bytes. Consumed by Task 5's frontend `exportService.downloadTenantData`.

- [ ] **Step 1: Write the failing tests**

```java
package com.vanter.ember.export.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.export.service.ExportService;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExportController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class ExportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ExportService exportService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_returnsTheZipWithAttachmentHeaders() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        byte[] fixtureZip = {80, 75, 3, 4};
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(exportService.buildTenantExportZip(TENANT_ID, from, to)).thenReturn(fixtureZip);

        mockMvc.perform(get("/admin/export")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-14T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(fixtureZip));

        verify(exportService).buildTenantExportZip(TENANT_ID, from, to);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_withoutParams_passesNullBoundsToTheService() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(exportService.buildTenantExportZip(TENANT_ID, null, null)).thenReturn(new byte[0]);

        mockMvc.perform(get("/admin/export")).andExpect(status().isOk());

        verify(exportService).buildTenantExportZip(TENANT_ID, null, null);
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void export_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/export")).andExpect(status().isForbidden());

        verify(exportService, never()).buildTenantExportZip(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_withoutTenantBound_isRejected() throws Exception {
        mockMvc.perform(get("/admin/export")).andExpect(status().isConflict());

        verify(exportService, never()).buildTenantExportZip(any(), any(), any());
    }

    @Test
    void export_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/export")).andExpect(status().isUnauthorized());

        verify(exportService, never()).buildTenantExportZip(any(), any(), any());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=ExportControllerTest`
Expected: compile error — `ExportController` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.vanter.ember.export.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Export", description = "Tenant business-data CSV export (ADMIN only)")
@RestController
@RequestMapping("/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ExportController {

    private final ExportService exportService;

    @Operation(
            summary = "Download the tenant's sales and product-performance history as a CSV zip",
            description = "'from'/'to' are optional inclusive ISO date-times; they default to the "
                    + "tenant's whole history up to now, the same rule every analytics read uses.")
    @GetMapping
    public ResponseEntity<byte[]> exportData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to) {
        byte[] zip = exportService.buildTenantExportZip(TenantContextHolder.requireTenantId(), from, to);
        String filename = "ember-export-" + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.valueOf("application/zip"))
                .body(zip);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=ExportControllerTest`
Expected: PASS, 5/5.

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS, no regressions (existing count + 17 new tests across the 4 backend tasks).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/export/controller/ExportController.java backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java
git commit -m "feat(export): add GET /admin/export endpoint"
```

---

## Task 5: Frontend `exportService` + `ExportSettings` component

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/pages/admin/components/settings/ExportSettings.tsx`
- Test: `frontend/src/pages/admin/components/settings/ExportSettings.test.tsx`
- Modify: `frontend/src/locales/es/admin.ts`
- Modify: `frontend/src/locales/en/admin.ts`

**Interfaces:**
- Consumes: `GET /admin/export?from=&to=` (Task 4).
- Produces: `exportService.downloadTenantData(from?: string, to?: string) -> Promise<Blob>`. `ExportSettings` component (default export... actually named export `ExportSettings`). Both consumed by Task 6.

- [ ] **Step 1: Add the i18n keys**

In `frontend/src/locales/es/admin.ts`, insert after the `infoSupportLinkText: 'Contactar a soporte',` line (right before `tourSettingsSidebarTitle`):

```ts
  exportLabel: 'Exportar datos',
  exportCardTitle: 'Exportar datos',
  exportCardDescription: 'Descarga el historial de ventas y productos de tu negocio en un .zip con archivos CSV.',
  exportFromLabel: 'Desde',
  exportToLabel: 'Hasta',
  exportHint: 'Deja las fechas en blanco para exportar todo el historial.',
  exportDownloadButton: 'Descargar',
  exportDownloadingLabel: 'Descargando...',
  exportDownloadedToast: 'Exportación descargada.',
  exportErrorToast: 'No se pudo generar la exportación. Intenta de nuevo.',
```

In `frontend/src/locales/en/admin.ts`, insert after the `infoSupportLinkText: 'Contact support',` line (right before `tourSettingsSidebarTitle`):

```ts
  exportLabel: 'Export data',
  exportCardTitle: 'Export data',
  exportCardDescription: "Download your business's sales and product history as a .zip of CSV files.",
  exportFromLabel: 'From',
  exportToLabel: 'To',
  exportHint: 'Leave the dates blank to export the whole history.',
  exportDownloadButton: 'Download',
  exportDownloadingLabel: 'Downloading...',
  exportDownloadedToast: 'Export downloaded.',
  exportErrorToast: "Couldn't generate the export. Please try again.",
```

- [ ] **Step 2: Add `exportService` to `api.ts`**

In `frontend/src/lib/api.ts`, add near `analyticsService` (after its closing `}`):

```ts
export const exportService = {
  downloadTenantData: async (from?: string, to?: string): Promise<Blob> => {
    const { data } = await api.get('/admin/export', {
      params: { from, to },
      responseType: 'blob',
    })
    return data
  },
}
```

- [ ] **Step 3: Write the failing component test**

```tsx
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ExportSettings } from './ExportSettings'
import { exportService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    exportService: {
      downloadTenantData: vi.fn(),
    },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {ui}
    </QueryClientProvider>,
  )

describe('ExportSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn().mockReturnValue('blob:mock-url')
    URL.revokeObjectURL = vi.fn()
  })

  test('downloading with no dates picked sends undefined bounds', async () => {
    vi.mocked(exportService.downloadTenantData).mockResolvedValue(new Blob(['zip']))

    wrap(<ExportSettings />)
    fireEvent.click(screen.getByRole('button', { name: 'Descargar' }))

    await waitFor(() =>
      expect(exportService.downloadTenantData).toHaveBeenCalledWith(undefined, undefined),
    )
  })

  test('picking a date range sends full-day ISO bounds', async () => {
    vi.mocked(exportService.downloadTenantData).mockResolvedValue(new Blob(['zip']))

    wrap(<ExportSettings />)
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-08-01' } })
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-08-14' } })
    fireEvent.click(screen.getByRole('button', { name: 'Descargar' }))

    await waitFor(() =>
      expect(exportService.downloadTenantData).toHaveBeenCalledWith(
        '2026-08-01T00:00:00',
        '2026-08-14T23:59:59',
      ),
    )
  })

  test('a failed download does not throw out of the component', async () => {
    vi.mocked(exportService.downloadTenantData).mockRejectedValue(new Error('network error'))

    wrap(<ExportSettings />)
    fireEvent.click(screen.getByRole('button', { name: 'Descargar' }))

    await waitFor(() => expect(exportService.downloadTenantData).toHaveBeenCalled())
  })
})
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd frontend && pnpm vitest run ExportSettings.test.tsx`
Expected: FAIL — `./ExportSettings` does not exist yet.

- [ ] **Step 5: Write the component**

```tsx
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Download } from 'lucide-react'
import { exportService } from '@/lib/api'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useTranslation } from '@/lib/i18n'

const toIsoStart = (date: string): string | undefined => (date ? `${date}T00:00:00` : undefined)
const toIsoEnd = (date: string): string | undefined => (date ? `${date}T23:59:59` : undefined)

export const ExportSettings = () => {
  const { t } = useTranslation('admin')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')

  const downloadMutation = useMutation({
    mutationFn: () => exportService.downloadTenantData(toIsoStart(fromDate), toIsoEnd(toDate)),
    onSuccess: (blob) => {
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `ember-export-${new Date().toISOString().slice(0, 10)}.zip`
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      toast.success(t('exportDownloadedToast'))
    },
    onError: () => toast.error(t('exportErrorToast')),
  })

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <Download className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('exportCardTitle')}</CardTitle>
          <CardDescription>{t('exportCardDescription')}</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-6">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="export-from">{t('exportFromLabel')}</Label>
              <Input
                id="export-from"
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="export-to">{t('exportToLabel')}</Label>
              <Input
                id="export-to"
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
              />
            </div>
          </div>
          <p className="text-xs text-muted-foreground">{t('exportHint')}</p>
          <Button onClick={() => downloadMutation.mutate()} disabled={downloadMutation.isPending}>
            <Download className="w-4 h-4 mr-2" />
            {downloadMutation.isPending ? t('exportDownloadingLabel') : t('exportDownloadButton')}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd frontend && pnpm vitest run ExportSettings.test.tsx`
Expected: PASS, 3/3.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/pages/admin/components/settings/ExportSettings.tsx frontend/src/pages/admin/components/settings/ExportSettings.test.tsx frontend/src/locales/es/admin.ts frontend/src/locales/en/admin.ts
git commit -m "feat(export): add ExportSettings component and exportService"
```

---

## Task 6: Wire the "Exportar datos" Settings tab

**Files:**
- Modify: `frontend/src/store/uiStore.ts`
- Modify: `frontend/src/components/SettingsBar.tsx`
- Modify: `frontend/src/components/GlobalSearchResults.tsx`
- Modify: `frontend/src/pages/admin/Settings.tsx`

**Interfaces:**
- Consumes: `ExportSettings` component (Task 5).

This task is pure mechanical wiring into 4 places — the same shape every other flat Settings tab (e.g. `INFO`) already follows, with no dedicated test of its own in this codebase (no test asserts on `SettingsBar`'s per-leaf wiring beyond the Hub-build visibility test, which this tab doesn't touch). Two of the four are TypeScript-enforced: `SettingsBar.tsx`'s `LEAF` and `GlobalSearchResults.tsx`'s `SETTINGS_TAB_LABEL_KEYS` are both non-partial `Record<LeafType, …>` keyed on every `SettingsType` value, so `pnpm run build` fails if either is skipped. The other two are **not** compiler-enforced — `buildSettingsNav()`'s array literal and the `switch` in `Settings.tsx` both silently accept a missing `'EXPORT'` case (the tab just wouldn't render or wouldn't appear in the sidebar) — so Steps 5-6 (build + full suite) plus the final manual smoke test are what actually catch those two if skipped.

- [ ] **Step 1: Add `EXPORT` to `SettingsType`**

In `frontend/src/store/uiStore.ts`, change:

```ts
export type SettingsType = 'BRANDING' | 'MENU' | 'BILLING' | 'PAYMENT_GATEWAY' | 'TICKET' | 'PRINTING' | 'HARDWARE'|
                            'SPACE'| 'HORARIO'| 'FIDELIZACION' | 'LOYALTY_REWARDS'| 'INFO'| null;
```

to:

```ts
export type SettingsType = 'BRANDING' | 'MENU' | 'BILLING' | 'PAYMENT_GATEWAY' | 'TICKET' | 'PRINTING' | 'HARDWARE'|
                            'SPACE'| 'HORARIO'| 'FIDELIZACION' | 'LOYALTY_REWARDS'| 'INFO'| 'EXPORT'| null;
```

- [ ] **Step 2: Add the leaf entry to `SettingsBar.tsx`**

In `frontend/src/components/SettingsBar.tsx`, add `Download` to the `lucide-react` import list (after `Info`):

```ts
  Info,
  Download,
  Menu,
```

Add the leaf's label + icon to the `LEAF` record, right after the `INFO` entry:

```ts
  INFO: { labelKey: 'infoLabel', Icon: Info },
  EXPORT: { labelKey: 'exportLabel', Icon: Download },
```

Add it to `buildSettingsNav()`, right after the `INFO` leaf:

```ts
  { kind: 'leaf', type: 'INFO' },
  { kind: 'leaf', type: 'EXPORT' },
]
```

- [ ] **Step 3: Add the leaf to `GlobalSearchResults.tsx`**

In `frontend/src/components/GlobalSearchResults.tsx`, add to `SETTINGS_TAB_LABEL_KEYS`, right after `INFO`:

```ts
  INFO: 'infoLabel',
  EXPORT: 'exportLabel',
}
```

- [ ] **Step 4: Wire the case in `Settings.tsx`**

In `frontend/src/pages/admin/Settings.tsx`, add the import (after `InfoSettings`):

```ts
import { InfoSettings } from "./components/settings/InfoSettings";
import { ExportSettings } from "./components/settings/ExportSettings";
```

Add the case, right after the `INFO` case:

```ts
            case 'INFO':
                return <InfoSettings />;
            case 'EXPORT':
                return <ExportSettings />;
        }
```

- [ ] **Step 5: Verify the build**

Run: `cd frontend && pnpm run build`
Expected: clean — no TypeScript errors (this is what catches a missed switch/`Record` case).

- [ ] **Step 6: Run the full frontend test suite**

Run: `cd frontend && pnpm run test:run`
Expected: PASS, no regressions (existing count + the 3 `ExportSettings` tests from Task 5).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/store/uiStore.ts frontend/src/components/SettingsBar.tsx frontend/src/components/GlobalSearchResults.tsx frontend/src/pages/admin/Settings.tsx
git commit -m "feat(export): wire the Exportar datos tab into Settings"
```

---

## Final verification

- [ ] `cd backend && ./mvnw test` — full suite green.
- [ ] `cd frontend && pnpm run build` — clean.
- [ ] `cd frontend && pnpm run test:run` — full suite green.
- [ ] Manual smoke test in a running app: log in as ADMIN, open Settings → "Exportar datos", click "Descargar" with no dates, confirm a `.zip` downloads and contains `ventas.csv` + `productos.csv` with a header row each; pick a date range and confirm the download still succeeds.
