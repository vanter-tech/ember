package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings.DaySchedule;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptBusinessInfoTest {

    private static DaySchedule day(DayOfWeek day, String open, String close) {
        DaySchedule s = new DaySchedule();
        s.setDay(day);
        s.setOpenTime(open);
        s.setCloseTime(close);
        return s;
    }

    private static DaySchedule closed(DayOfWeek day) {
        DaySchedule s = new DaySchedule();
        s.setDay(day);
        s.setClosed(true);
        return s;
    }

    private static SettingsPayload settingsWith(DaySchedule... schedule) {
        SettingsPayload settings = new SettingsPayload();
        settings.getBusinessHours().setSchedule(List.of(schedule));
        return settings;
    }

    @Test
    void sameHoursEveryDay_isOneLine() {
        SettingsPayload settings = settingsWith(
                day(DayOfWeek.MONDAY, "12:00", "23:00"), day(DayOfWeek.TUESDAY, "12:00", "23:00"),
                day(DayOfWeek.WEDNESDAY, "12:00", "23:00"), day(DayOfWeek.THURSDAY, "12:00", "23:00"),
                day(DayOfWeek.FRIDAY, "12:00", "23:00"), day(DayOfWeek.SATURDAY, "12:00", "23:00"),
                day(DayOfWeek.SUNDAY, "12:00", "23:00"));

        assertThat(ReceiptBusinessInfo.lines(settings)).containsExactly("Horario: Lun-Dom 12:00-23:00");
    }

    @Test
    void differentHours_areCollapsedIntoRunsOfConsecutiveDays_andClosedDaysSaySo() {
        SettingsPayload settings = settingsWith(
                day(DayOfWeek.MONDAY, "12:00", "22:00"), day(DayOfWeek.TUESDAY, "12:00", "22:00"),
                day(DayOfWeek.WEDNESDAY, "12:00", "22:00"), day(DayOfWeek.THURSDAY, "12:00", "22:00"),
                day(DayOfWeek.FRIDAY, "12:00", "22:00"), day(DayOfWeek.SATURDAY, "12:00", "23:30"),
                closed(DayOfWeek.SUNDAY));

        assertThat(ReceiptBusinessInfo.lines(settings)).containsExactly(
                "Horario:", "Lun-Vie 12:00-22:00", "Sab 12:00-23:30", "Dom cerrado");
    }

    @Test
    void sameHoursSeparatedByADifferentDay_stayAsSeparateRuns() {
        SettingsPayload settings = settingsWith(
                day(DayOfWeek.MONDAY, "10:00", "20:00"), closed(DayOfWeek.TUESDAY),
                day(DayOfWeek.WEDNESDAY, "10:00", "20:00"));

        assertThat(ReceiptBusinessInfo.lines(settings)).containsExactly(
                "Horario:", "Lun 10:00-20:00", "Mar cerrado", "Mie 10:00-20:00");
    }

    @Test
    void withoutAPerDaySchedule_fallsBackToTheBrandingRange() {
        SettingsPayload settings = new SettingsPayload();
        settings.getBranding().setOpeningTime("12:00");
        settings.getBranding().setClosingTime("23:00");

        assertThat(ReceiptBusinessInfo.lines(settings)).containsExactly("Horario: 12:00 - 23:00");
    }

    @Test
    void withNoHoursAnywhere_addsNothing() {
        assertThat(ReceiptBusinessInfo.lines(new SettingsPayload())).isEmpty();
    }

    @Test
    void hoursCanBeSwitchedOff() {
        SettingsPayload settings = new SettingsPayload();
        settings.getBranding().setOpeningTime("12:00");
        settings.getBranding().setClosingTime("23:00");
        settings.getTicket().setShowBusinessHours(false);

        assertThat(ReceiptBusinessInfo.lines(settings)).isEmpty();
    }

    @Test
    void rucAddressAndPhone_areOffByDefault_andPrintedWhenSwitchedOn() {
        SettingsPayload settings = new SettingsPayload();
        settings.getBranding().setRuc("J0310000000001");
        settings.getBranding().setAddress("Calle Principal 123");
        settings.getBranding().setPhone("2222-3333");

        assertThat(ReceiptBusinessInfo.lines(settings)).isEmpty();

        settings.getTicket().setShowBusinessInfo(true);
        assertThat(ReceiptBusinessInfo.lines(settings))
                .containsExactly("RUC: J0310000000001", "Calle Principal 123", "Tel: 2222-3333");
    }

    @Test
    void blankBrandingFields_areSkipped() {
        SettingsPayload settings = new SettingsPayload();
        settings.getBranding().setRuc("  ");
        settings.getBranding().setPhone("2222-3333");
        settings.getTicket().setShowBusinessInfo(true);

        assertThat(ReceiptBusinessInfo.lines(settings)).containsExactly("Tel: 2222-3333");
    }
}
