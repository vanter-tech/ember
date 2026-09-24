package com.vanter.ember.printing.service;

import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings.DaySchedule;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The business-info block printed under the receipt header: opening hours and, optionally, RUC,
 * address and phone. Pure — takes settings, returns plain ASCII lines (no accents: thermal
 * printers print whatever code page they default to), each wrapped and centered by
 * {@link ReceiptLayout}.
 */
final class ReceiptBusinessInfo {

    private static final Map<DayOfWeek, String> DAY_ABBR = Map.of(
            DayOfWeek.MONDAY, "Lun", DayOfWeek.TUESDAY, "Mar", DayOfWeek.WEDNESDAY, "Mie",
            DayOfWeek.THURSDAY, "Jue", DayOfWeek.FRIDAY, "Vie", DayOfWeek.SATURDAY, "Sab",
            DayOfWeek.SUNDAY, "Dom");

    private ReceiptBusinessInfo() {}

    static List<String> lines(SettingsPayload settings) {
        List<String> lines = new ArrayList<>();
        SettingsPayload.TicketSettings ticket = settings.getTicket();
        SettingsPayload.BrandingSettings branding = settings.getBranding();

        if (ticket.isShowBusinessInfo()) {
            addIfPresent(lines, "RUC: ", branding.getRuc());
            addIfPresent(lines, "", branding.getAddress());
            addIfPresent(lines, "Tel: ", branding.getPhone());
        }
        if (ticket.isShowBusinessHours()) {
            lines.addAll(hoursLines(settings));
        }
        return lines;
    }

    private static void addIfPresent(List<String> lines, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(prefix + value.strip());
        }
    }

    /**
     * The per-day schedule collapsed into runs of consecutive days with identical hours
     * ("Lun-Vie 12:00-23:00"); falls back to the single opening/closing range from Branding when
     * no per-day schedule was ever saved; empty when neither exists.
     */
    static List<String> hoursLines(SettingsPayload settings) {
        List<String> groups = scheduleGroups(settings.getBusinessHours().getSchedule());
        if (groups.isEmpty()) {
            String open = settings.getBranding().getOpeningTime();
            String close = settings.getBranding().getClosingTime();
            if (open != null && !open.isBlank() && close != null && !close.isBlank()) {
                return List.of("Horario: " + open.strip() + " - " + close.strip());
            }
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        if (groups.size() == 1) {
            lines.add("Horario: " + groups.get(0));
        } else {
            lines.add("Horario:");
            lines.addAll(groups);
        }
        return lines;
    }

    private static List<String> scheduleGroups(List<DaySchedule> schedule) {
        List<String> groups = new ArrayList<>();
        if (schedule == null || schedule.isEmpty()) {
            return groups;
        }
        String runSignature = null;
        DayOfWeek runStart = null;
        DayOfWeek runEnd = null;
        for (DayOfWeek day : DayOfWeek.values()) {
            String signature = schedule.stream()
                    .filter(s -> s.getDay() == day)
                    .findFirst()
                    .map(ReceiptBusinessInfo::signature)
                    .orElse(null);
            if (signature != null && signature.equals(runSignature)) {
                runEnd = day;
                continue;
            }
            flush(groups, runSignature, runStart, runEnd);
            runSignature = signature;
            runStart = signature == null ? null : day;
            runEnd = runStart;
        }
        flush(groups, runSignature, runStart, runEnd);
        return groups;
    }

    private static String signature(DaySchedule s) {
        if (s.isClosed()) {
            return "cerrado";
        }
        if (s.getOpenTime() == null || s.getOpenTime().isBlank()
                || s.getCloseTime() == null || s.getCloseTime().isBlank()) {
            return null;
        }
        return s.getOpenTime().strip() + "-" + s.getCloseTime().strip();
    }

    private static void flush(List<String> groups, String signature, DayOfWeek start, DayOfWeek end) {
        if (signature == null || start == null) {
            return;
        }
        String label = start == end ? DAY_ABBR.get(start) : DAY_ABBR.get(start) + "-" + DAY_ABBR.get(end);
        groups.add(label + " " + signature);
    }
}
