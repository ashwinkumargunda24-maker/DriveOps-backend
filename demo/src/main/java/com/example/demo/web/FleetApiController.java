package com.example.demo.web;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.domain.Codes.DocumentKind;
import com.example.demo.domain.Codes.DocumentOwnerType;
import com.example.demo.dto.PageResponse;
import com.example.demo.service.FleetService;
import com.example.demo.service.MonthEndReportService;
import com.example.demo.service.MonthlyFinanceService;

@RestController
@RequestMapping("/api")
public class FleetApiController {
    private final FleetService fleet;
    private final MonthlyFinanceService finance;
    private final MonthEndReportService monthlyReports;

    public FleetApiController(FleetService fleet, MonthlyFinanceService finance, MonthEndReportService monthlyReports) {
        this.fleet = fleet;
        this.finance = finance;
        this.monthlyReports = monthlyReports;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return fleet.dashboard();
    }

    @GetMapping("/organizations")
    public PageResponse<Map<String, Object>> organizations(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return fleet.organizations(page, size);
    }

    @PostMapping("/organizations")
    public Map<String, Object> createOrg(@RequestBody Map<String, Object> body) {
        return fleet.saveOrganization(null, body);
    }

    @PutMapping("/organizations/{id}")
    public Map<String, Object> updateOrg(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveOrganization(id, body);
    }

    @GetMapping("/branches")
    public PageResponse<Map<String, Object>> branches(@RequestParam(required = false) String q,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return fleet.branches(q, page, size);
    }

    @PostMapping("/branches")
    public Map<String, Object> createBranch(@RequestBody Map<String, Object> body) {
        return fleet.saveBranch(null, body);
    }

    @PutMapping("/branches/{id}")
    public Map<String, Object> updateBranch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveBranch(id, body);
    }

    @GetMapping("/branches/{id}/stats")
    public Map<String, Object> branchStats(@PathVariable Long id) {
        return fleet.branchStats(id);
    }

    @GetMapping("/users")
    public PageResponse<Map<String, Object>> users(@RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return fleet.users(q, page, size);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        return fleet.saveUser(null, body);
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveUser(id, body);
    }

    @PostMapping("/users/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return fleet.resetPassword(id, body.get("password"));
    }

    @GetMapping("/vehicles")
    public PageResponse<Map<String, Object>> vehicles(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long branchId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return fleet.vehicles(q, status, branchId, page, size);
    }

    @PostMapping("/vehicles")
    public Map<String, Object> createVehicle(@RequestBody Map<String, Object> body) {
        return fleet.saveVehicle(null, body);
    }

    @PutMapping("/vehicles/{id}")
    public Map<String, Object> updateVehicle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveVehicle(id, body);
    }

    @GetMapping("/vehicles/{id}/history")
    public Map<String, Object> vehicleHistory(@PathVariable Long id) {
        return fleet.vehicleHistory(id);
    }

    @GetMapping("/drivers")
    public PageResponse<Map<String, Object>> drivers(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long branchId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return fleet.drivers(q, status, branchId, page, size);
    }

    @PostMapping("/drivers")
    public Map<String, Object> createDriver(@RequestBody Map<String, Object> body) {
        return fleet.saveDriver(null, body);
    }

    @PutMapping("/drivers/{id}")
    public Map<String, Object> updateDriver(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveDriver(id, body);
    }

    @GetMapping("/drivers/{id}/history")
    public Map<String, Object> driverHistory(@PathVariable Long id) {
        return fleet.driverHistory(id);
    }

    @GetMapping("/routes")
    public PageResponse<Map<String, Object>> routes(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Long branchId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return fleet.routes(q, branchId, page, size);
    }

    @PostMapping("/routes")
    public Map<String, Object> createRoute(@RequestBody Map<String, Object> body) {
        return fleet.saveRoute(null, body);
    }

    @PutMapping("/routes/{id}")
    public Map<String, Object> updateRoute(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveRoute(id, body);
    }

    @DeleteMapping("/routes/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable Long id) {
        fleet.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trips")
    public PageResponse<Map<String, Object>> trips(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long branchId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return fleet.trips(q, status, branchId, page, size);
    }

    @PostMapping("/trips")
    public Map<String, Object> createTrip(@RequestBody Map<String, Object> body) {
        return fleet.saveTrip(null, body);
    }

    @PutMapping("/trips/{id}")
    public Map<String, Object> updateTrip(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveTrip(id, body);
    }

    @PostMapping("/trips/{id}/assign")
    public Map<String, Object> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.assignTrip(id, Long.parseLong(String.valueOf(body.get("vehicleId"))),
                Long.parseLong(String.valueOf(body.get("driverId"))));
    }

    @PostMapping("/trips/{id}/status")
    public Map<String, Object> tripStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return fleet.updateTripStatus(id, body.get("status"), body.get("remarks"));
    }

    @GetMapping("/trips/{id}/history")
    public Object tripHistory(@PathVariable Long id) {
        return fleet.tripHistory(id);
    }

    @GetMapping("/maintenance")
    public PageResponse<Map<String, Object>> maintenance(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Long branchId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return fleet.maintenance(q, status, branchId, page, size);
    }

    @PostMapping("/maintenance")
    public Map<String, Object> createMaintenance(@RequestBody Map<String, Object> body) {
        return fleet.saveMaintenance(null, body);
    }

    @PutMapping("/maintenance/{id}")
    public Map<String, Object> updateMaintenance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.saveMaintenance(id, body);
    }

    @GetMapping("/fuel")
    public PageResponse<Map<String, Object>> fuel(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Long branchId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return fleet.fuel(q, branchId, page, size);
    }

    @PostMapping("/fuel")
    public Map<String, Object> createFuel(@RequestBody Map<String, Object> body) {
        return fleet.saveFuel(body);
    }

    @GetMapping("/expenses")
    public PageResponse<Map<String, Object>> expenses(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long branchId,
                                                      @RequestParam(required = false) Long vehicleId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return fleet.expenses(q, status, branchId, vehicleId, page, size);
    }

    @PostMapping("/expenses")
    public Map<String, Object> createExpense(@RequestBody Map<String, Object> body) {
        return fleet.saveExpense(body);
    }

    @PostMapping("/expenses/{id}/review")
    public Map<String, Object> reviewExpense(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.reviewExpense(id, Boolean.TRUE.equals(body.get("approve")));
    }

    @GetMapping("/incidents")
    public PageResponse<Map<String, Object>> incidents(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String severity,
                                                       @RequestParam(required = false) Long branchId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return fleet.incidents(q, severity, branchId, page, size);
    }

    @PostMapping("/incidents")
    public Map<String, Object> createIncident(@RequestBody Map<String, Object> body) throws IOException {
        return fleet.saveIncident(body);
    }

    @PostMapping(value = "/incidents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createIncidentWithPhoto(@RequestParam Map<String, String> body,
                                                       @RequestParam(required = false) MultipartFile photo) throws IOException {
        return fleet.saveIncident(new java.util.LinkedHashMap<>(body), photo);
    }

    @GetMapping("/incidents/{id}/photo")
    public ResponseEntity<Resource> incidentPhoto(@PathVariable Long id) throws IOException {
        var path = fleet.incidentPhoto(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    @PutMapping("/incidents/{id}")
    public Map<String, Object> updateIncident(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return fleet.updateIncident(id, body);
    }

    @GetMapping("/documents")
    public PageResponse<Map<String, Object>> documents(@RequestParam(required = false) Long branchId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return fleet.documents(branchId, page, size);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadDocument(@RequestParam DocumentOwnerType ownerType,
                                              @RequestParam Long ownerId,
                                              @RequestParam DocumentKind kind,
                                              @RequestParam(required = false) String expiryDate,
                                              @RequestParam(required = false) MultipartFile file) throws IOException {
        LocalDate expiry = expiryDate == null || expiryDate.isBlank() ? null : LocalDate.parse(expiryDate);
        return fleet.saveDocument(ownerType, ownerId, kind, expiry, file);
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        var path = fleet.documentFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/notifications")
    public Object notifications() {
        return fleet.notifications();
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> readNotification(@PathVariable Long id) {
        fleet.markNotificationRead(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audits")
    public PageResponse<Map<String, Object>> audits(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "15") int size) {
        return fleet.audits(page, size);
    }

    @GetMapping(value = "/export/{type}", produces = "text/csv")
    public ResponseEntity<String> export(@PathVariable String type, @RequestParam(required = false) Long branchId) {
        String csv = fleet.exportCsv(type, branchId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + ".csv")
                .body(csv);
    }

    @PostMapping("/alerts/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        fleet.refreshAlerts();
        return ResponseEntity.ok(Map.of("message", "Alerts refreshed"));
    }

    @GetMapping("/finance/summary")
    public Map<String, Object> financeSummary(@RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) Integer month,
                                              @RequestParam(required = false) Long branchId) {
        return finance.authorizedSummary(year, month, branchId);
    }

    @GetMapping("/finance/history")
    public Map<String, Object> financeHistory(@RequestParam(defaultValue = "12") int months,
                                              @RequestParam(required = false) Long branchId) {
        return finance.authorizedHistory(months, branchId);
    }

    @GetMapping("/finance/details")
    public Map<String, Object> financeDetails(@RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) Integer month,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) Long vehicleId,
                                              @RequestParam(required = false) String tripStatus,
                                              @RequestParam(required = false) String expenseCategory,
                                              @RequestParam(required = false) String expenseStatus,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return finance.authorizedDetails(year, month, branchId, vehicleId, tripStatus, expenseCategory, expenseStatus, page, size);
    }

    @GetMapping(value = "/finance/export", produces = "text/csv")
    public ResponseEntity<String> financeExport(@RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                @RequestParam(required = false) Long branchId,
                                                @RequestParam(required = false) Long vehicleId,
                                                @RequestParam(required = false) String tripStatus,
                                                @RequestParam(required = false) String expenseCategory,
                                                @RequestParam(required = false) String expenseStatus) {
        String csv = finance.exportCsv(year, month, branchId, vehicleId, tripStatus, expenseCategory, expenseStatus);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=finance-" + year + "-" + month + ".csv")
                .body(csv);
    }

    @PostMapping("/trips/{id}/revenue")
    public Map<String, Object> recordRevenue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return finance.recordTripRevenue(id, body.get("revenueAmount") == null ? body.get("amount") : body.get("revenueAmount"));
    }

    @GetMapping("/reports/monthly")
    public Object monthlyReports() {
        return monthlyReports.listAuthorized();
    }

    @GetMapping("/reports/monthly/{id}")
    public Map<String, Object> monthlyReport(@PathVariable Long id) {
        return monthlyReports.detailAuthorized(id);
    }

    @GetMapping(value = "/reports/monthly/{id}/export", produces = "text/csv")
    public ResponseEntity<String> monthlyReportExport(@PathVariable Long id) {
        String csv = monthlyReports.exportAuthorized(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=monthly-report-" + id + ".csv")
                .body(csv);
    }

    @PostMapping("/reports/monthly/generate")
    public Map<String, Object> generateMonthlyReport(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        Integer year = payload.get("year") == null ? null : Integer.parseInt(String.valueOf(payload.get("year")));
        Integer month = payload.get("month") == null ? null : Integer.parseInt(String.valueOf(payload.get("month")));
        boolean regenerate = Boolean.TRUE.equals(payload.get("regenerate"));
        return monthlyReports.generateAuthorized(year, month, regenerate);
    }
}
