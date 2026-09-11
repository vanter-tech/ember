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
