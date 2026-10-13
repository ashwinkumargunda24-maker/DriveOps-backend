package com.example.demo.domain;

public final class Codes {
    private Codes() {
    }

    public enum Role {
        SUPER_ADMIN, FLEET_MANAGER, BRANCH_MANAGER, DRIVER, FINANCE_OFFICER
    }

    public enum VehicleStatus {
        AVAILABLE, ASSIGNED, ON_TRIP, MAINTENANCE, INACTIVE
    }

    public enum DriverStatus {
        AVAILABLE, ASSIGNED, ON_TRIP, ON_LEAVE, INACTIVE
    }

    public enum TripStatus {
        PLANNED, ASSIGNED, STARTED, DELAYED, COMPLETED, CANCELLED
    }

    public enum MaintenanceType {
        REGULAR_SERVICE, OIL_CHANGE, TIRE_REPLACEMENT, BRAKE_SERVICE, ENGINE_SERVICE, GENERAL_REPAIR, INSPECTION
    }

    public enum MaintenanceStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, OVERDUE
    }

    public enum ExpenseCategory {
        FUEL, MAINTENANCE, TOLL, PARKING, DRIVER_ALLOWANCE, REPAIR, INSURANCE, OTHER
    }

    public enum ExpenseStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum IncidentSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum IncidentStatus {
        REPORTED, UNDER_INVESTIGATION, RESOLVED, CLOSED
    }

    public enum DocumentOwnerType {
        VEHICLE, DRIVER
    }

    public enum DocumentKind {
        REGISTRATION, INSURANCE, POLLUTION_CERTIFICATE, FITNESS_CERTIFICATE, DRIVING_LICENSE, ID_PROOF, OTHER
    }

    public enum MonthlyReportStatus {
        GENERATING, FINALIZED, FAILED
    }
}
