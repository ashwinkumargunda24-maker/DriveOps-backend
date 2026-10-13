package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.service.MonthEndReportService;

@Component
public class MonthEndReportScheduler {
    private static final Logger log = LoggerFactory.getLogger(MonthEndReportScheduler.class);
    private final MonthEndReportService reports;

    public MonthEndReportScheduler(MonthEndReportService reports) {
        this.reports = reports;
    }

    @Scheduled(cron = "${app.reporting.cron:0 15 0 1 * *}", zone = "${app.timezone:Asia/Kolkata}")
    public void generatePreviousMonth() {
        try {
            reports.generatePreviousMonthIfAbsent();
        } catch (RuntimeException ex) {
            log.error("Scheduled month-end report generation failed.", ex);
        }
    }
}
