package com.example.demo.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.service.FleetService;

@Component
public class AlertScheduler {
    private final FleetService fleetService;

    public AlertScheduler(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void dailyAlerts() {
        fleetService.refreshAlertsInternal();
    }
}
