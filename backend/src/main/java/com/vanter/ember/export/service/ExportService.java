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
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.settings.service.SettingService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the tenant business-data export: a single {@code .xlsx} workbook with a "Ventas" sheet
 * (one row per {@code PAID}/{@code VOIDED} bill) and a "Productos" sheet (delegates entirely to
 * {@link AnalyticsService#getProducts}, the same computation the admin dashboard's product-
 * performance view already uses, just with no top-N {@code limit}). A real spreadsheet — not a
 * CSV — so numbers stay numbers (summable/sortable in Excel), dates stay dates, and every cell is
 * bordered and banded so the sheet reads as a structured report instead of loose values.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    /** Same sentinel {@link AnalyticsService} uses for "the tenant's whole history". */
    static final LocalDateTime EPOCH_FLOOR = LocalDateTime.of(1970, 1, 1, 0, 0);

    private static final byte[] BRAND_RED = {(byte) 0x8c, (byte) 0x17, (byte) 0x17};
    private static final byte[] BAND_GRAY = {(byte) 0xF2, (byte) 0xF2, (byte) 0xF2};
    private static final int VENTAS_LAST_COLUMN = 6;
    private static final int PRODUCTOS_LAST_COLUMN = 4;

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final SessionRepository sessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final AnalyticsService analyticsService;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    public byte[] buildTenantExportWorkbook(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime windowStart = from == null ? EPOCH_FLOOR : from;
        LocalDateTime windowEnd = to == null ? LocalDateTime.now() : to;
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("Export range 'from' must not be after 'to'");
        }

        SettingsPayload.BrandingSettings branding = settingService.getSettings(tenantId).getPayload().getBranding();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle labelStyle = boldStyle(workbook, null);
            CellStyle valueStyle = plainStyle(workbook, null);
            CellStyle columnHeaderStyle = boldStyle(workbook, BRAND_RED);
            RowStyles bandA = rowStyles(workbook, null);
            RowStyles bandB = rowStyles(workbook, BAND_GRAY);

            writeVentasSheet(workbook, tenantId, windowStart, windowEnd, branding,
                    titleStyle, labelStyle, valueStyle, columnHeaderStyle, bandA, bandB);
            writeProductosSheet(workbook, tenantId, windowStart, windowEnd, branding,
                    titleStyle, labelStyle, valueStyle, columnHeaderStyle, bandA, bandB);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeVentasSheet(
            XSSFWorkbook workbook,
            UUID tenantId,
            LocalDateTime from,
            LocalDateTime to,
            SettingsPayload.BrandingSettings branding,
            CellStyle titleStyle,
            CellStyle labelStyle,
            CellStyle valueStyle,
            CellStyle columnHeaderStyle,
            RowStyles bandA,
            RowStyles bandB) {
        Sheet sheet = workbook.createSheet("Ventas");
        writeTitleRow(sheet, "Reporte de Ventas", titleStyle, VENTAS_LAST_COLUMN);
        int rowIndex = writeBusinessHeaderBlock(sheet, branding, from, to, labelStyle, valueStyle);

        writeColumnHeaderRow(sheet, rowIndex, columnHeaderStyle,
                "ID Cuenta", "Mesa", "Fecha", "Total", "Estado", "Métodos de pago", "Participantes");
        rowIndex++;

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

        int dataRowCount = 0;
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

            RowStyles styles = dataRowCount % 2 == 0 ? bandA : bandB;
            dataRowCount++;

            Row row = sheet.createRow(rowIndex++);
            numericCell(row, 0, bill.getId(), styles.count());
            if (tableNumber != null) {
                numericCell(row, 1, tableNumber, styles.count());
            }
            Cell dateCell = row.createCell(2);
            dateCell.setCellValue(bill.getCreatedAt());
            dateCell.setCellStyle(styles.date());
            numericCell(row, 3, bill.getTotal().doubleValue(), styles.money());
            textCell(row, 4, bill.getStatus().name(), styles.text());
            textCell(row, 5, methods, styles.text());
            numericCell(row, 6, participantCount, styles.count());
        }

        for (int col = 0; col <= VENTAS_LAST_COLUMN; col++) {
            sheet.autoSizeColumn(col);
            sheet.setColumnWidth(col, sheet.getColumnWidth(col) + 640);
        }
    }

    private void writeProductosSheet(
            XSSFWorkbook workbook,
            UUID tenantId,
            LocalDateTime from,
            LocalDateTime to,
            SettingsPayload.BrandingSettings branding,
            CellStyle titleStyle,
            CellStyle labelStyle,
            CellStyle valueStyle,
            CellStyle columnHeaderStyle,
            RowStyles bandA,
            RowStyles bandB) {
        Sheet sheet = workbook.createSheet("Productos");
        writeTitleRow(sheet, "Reporte de Productos", titleStyle, PRODUCTOS_LAST_COLUMN);
        int rowIndex = writeBusinessHeaderBlock(sheet, branding, from, to, labelStyle, valueStyle);

        writeColumnHeaderRow(sheet, rowIndex, columnHeaderStyle,
                "Producto", "Categoría", "Unidades vendidas", "Ingresos", "% Ingresos");
        rowIndex++;

        AnalyticsProductsResponse products = analyticsService.getProducts(tenantId, from, to, null);
        int dataRowCount = 0;
        for (ProductPerformance product : products.products()) {
            RowStyles styles = dataRowCount % 2 == 0 ? bandA : bandB;
            dataRowCount++;

            Row row = sheet.createRow(rowIndex++);
            textCell(row, 0, product.name(), styles.text());
            textCell(row, 1, product.categoryName() == null ? "" : product.categoryName(), styles.text());
            numericCell(row, 2, product.quantitySold(), styles.count());
            numericCell(row, 3, product.revenue().doubleValue(), styles.money());
            numericCell(row, 4, product.revenueShare().doubleValue(), styles.money());
        }

        for (int col = 0; col <= PRODUCTOS_LAST_COLUMN; col++) {
            sheet.autoSizeColumn(col);
            sheet.setColumnWidth(col, sheet.getColumnWidth(col) + 640);
        }
    }

    /** A merged, brand-colored title bar spanning columns 0..{@code lastColumn} at row 0. */
    private void writeTitleRow(Sheet sheet, String title, CellStyle style, int lastColumn) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(24f);
        for (int col = 0; col <= lastColumn; col++) {
            row.createCell(col).setCellStyle(style);
        }
        row.getCell(0).setCellValue(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
    }

    /**
     * A small label/value block identifying the business behind the report, right under the title
     * bar so it's self-describing on its own — pulled from the same Branding settings the admin
     * already fills in under Settings > Marca y negocio. Returns the next free row index (a blank
     * row after the block).
     */
    private int writeBusinessHeaderBlock(
            Sheet sheet, SettingsPayload.BrandingSettings branding, LocalDateTime from, LocalDateTime to,
            CellStyle labelStyle, CellStyle valueStyle) {
        writeLabelRow(sheet, 1, "Negocio", blankToEmpty(branding.getBusinessName()), labelStyle, valueStyle);
        writeLabelRow(sheet, 2, "Nombre legal", blankToEmpty(branding.getLegalName()), labelStyle, valueStyle);
        writeLabelRow(sheet, 3, "RUC", blankToEmpty(branding.getRuc()), labelStyle, valueStyle);
        writeLabelRow(sheet, 4, "Teléfono", blankToEmpty(branding.getPhone()), labelStyle, valueStyle);
        writeLabelRow(sheet, 5, "Dirección", blankToEmpty(branding.getAddress()), labelStyle, valueStyle);
        writeLabelRow(sheet, 6, "Rango exportado", from + " a " + to, labelStyle, valueStyle);
        return 8;
    }

    private void writeLabelRow(
            Sheet sheet, int rowIndex, String label, String value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        textCell(row, 1, value, valueStyle);
    }

    private void writeColumnHeaderRow(Sheet sheet, int rowIndex, CellStyle style, String... columns) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(18f);
        for (int col = 0; col < columns.length; col++) {
            Cell cell = row.createCell(col);
            cell.setCellValue(columns[col]);
            cell.setCellStyle(style);
        }
    }

    private void numericCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void textCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** The four styled cell kinds a data row can contain, banded to a single background color. */
    private record RowStyles(CellStyle text, CellStyle date, CellStyle money, CellStyle count) {}

    private static RowStyles rowStyles(XSSFWorkbook workbook, byte[] fillRgb) {
        return new RowStyles(
                plainStyle(workbook, fillRgb),
                dateStyle(workbook, fillRgb),
                numberStyle(workbook, "#,##0.00", fillRgb),
                numberStyle(workbook, "#,##0", fillRgb));
    }

    /** Bold label style; {@code fillRgb == null} leaves the background transparent (plain bold text). */
    private static CellStyle boldStyle(XSSFWorkbook workbook, byte[] fillRgb) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        if (fillRgb != null) {
            font.setColor(new XSSFColor(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
            style.setFillForegroundColor(new XSSFColor(fillRgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setFont(font);
        applyThinBorder(style);
        return style;
    }

    private static CellStyle titleStyle(XSSFWorkbook workbook) {
        CellStyle style = boldStyle(workbook, BRAND_RED);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** Plain (non-bold) style with a thin border and optional band fill; {@code fillRgb == null} for no fill. */
    private static CellStyle plainStyle(XSSFWorkbook workbook, byte[] fillRgb) {
        XSSFCellStyle style = workbook.createCellStyle();
        if (fillRgb != null) {
            style.setFillForegroundColor(new XSSFColor(fillRgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        applyThinBorder(style);
        return style;
    }

    private static CellStyle dateStyle(XSSFWorkbook workbook, byte[] fillRgb) {
        CellStyle style = plainStyle(workbook, fillRgb);
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return style;
    }

    private static CellStyle numberStyle(XSSFWorkbook workbook, String format, byte[] fillRgb) {
        CellStyle style = plainStyle(workbook, fillRgb);
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(format));
        return style;
    }

    private static void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
