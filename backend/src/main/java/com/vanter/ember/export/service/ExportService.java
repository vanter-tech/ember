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
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.settings.service.SettingService;
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
    private final SettingService settingService;

    @Transactional(readOnly = true)
    public byte[] buildTenantExportZip(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime windowStart = from == null ? EPOCH_FLOOR : from;
        LocalDateTime windowEnd = to == null ? LocalDateTime.now() : to;
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("Export range 'from' must not be after 'to'");
        }

        SettingsPayload.BrandingSettings branding = settingService.getSettings(tenantId).getPayload().getBranding();
        String headerBlock = buildHeaderBlock(branding, windowStart, windowEnd);

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("ventas.csv", buildVentasCsv(tenantId, windowStart, windowEnd, headerBlock));
        files.put("productos.csv", buildProductosCsv(tenantId, windowStart, windowEnd, headerBlock));
        return zip(files);
    }

    /**
     * A small label/value block identifying the business behind the report, prepended to every
     * CSV so a file opened on its own is still self-describing — pulled from the same Branding
     * settings the admin already fills in under Settings > Marca y negocio. CSV tolerates the
     * ragged row lengths this produces against the column table below; every spreadsheet app
     * just shows fewer values on the shorter rows.
     */
    private String buildHeaderBlock(SettingsPayload.BrandingSettings branding, LocalDateTime from, LocalDateTime to) {
        StringBuilder header = new StringBuilder();
        header.append(CsvWriter.writeRow(List.of("Negocio", blankToEmpty(branding.getBusinessName()))));
        header.append(CsvWriter.writeRow(List.of("Nombre legal", blankToEmpty(branding.getLegalName()))));
        header.append(CsvWriter.writeRow(List.of("RUC", blankToEmpty(branding.getRuc()))));
        header.append(CsvWriter.writeRow(List.of("Teléfono", blankToEmpty(branding.getPhone()))));
        header.append(CsvWriter.writeRow(List.of("Dirección", blankToEmpty(branding.getAddress()))));
        header.append(CsvWriter.writeRow(List.of("Rango exportado", from + " a " + to)));
        header.append(CsvWriter.writeRow(List.of()));
        return header.toString();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private byte[] buildVentasCsv(UUID tenantId, LocalDateTime from, LocalDateTime to, String headerBlock) {
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

        StringBuilder csv = new StringBuilder(headerBlock);
        csv.append(CsvWriter.writeRow(
                List.of("ID Cuenta", "Mesa", "Fecha", "Total", "Estado", "Métodos de pago", "Participantes")));

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

    private byte[] buildProductosCsv(UUID tenantId, LocalDateTime from, LocalDateTime to, String headerBlock) {
        AnalyticsProductsResponse products = analyticsService.getProducts(tenantId, from, to, null);

        StringBuilder csv = new StringBuilder(headerBlock);
        csv.append(CsvWriter.writeRow(
                List.of("Producto", "Categoría", "Unidades vendidas", "Ingresos", "% Ingresos")));

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
