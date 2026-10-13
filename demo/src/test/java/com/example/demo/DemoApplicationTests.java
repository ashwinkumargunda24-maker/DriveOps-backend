package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.config.ProductionConfigurationValidator;
import com.example.demo.domain.AppUser;
import com.example.demo.domain.Branch;
import com.example.demo.domain.Codes.DriverStatus;
import com.example.demo.domain.Codes.ExpenseCategory;
import com.example.demo.domain.Codes.ExpenseStatus;
import com.example.demo.domain.Codes.Role;
import com.example.demo.domain.Codes.TripStatus;
import com.example.demo.domain.Codes.VehicleStatus;
import com.example.demo.domain.Driver;
import com.example.demo.domain.Expense;
import com.example.demo.domain.Organization;
import com.example.demo.domain.Route;
import com.example.demo.domain.Trip;
import com.example.demo.domain.Vehicle;
import com.example.demo.repo.BranchRepository;
import com.example.demo.repo.DriverRepository;
import com.example.demo.repo.ExpenseRepository;
import com.example.demo.repo.OrganizationRepository;
import com.example.demo.repo.RouteRepository;
import com.example.demo.repo.TripRepository;
import com.example.demo.repo.UserRepository;
import com.example.demo.repo.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class DemoApplicationTests {
    private static final String PASSWORD = "Test-password-123";

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationRepository organizations;
    @Autowired private BranchRepository branches;
    @Autowired private UserRepository users;
    @Autowired private VehicleRepository vehicles;
    @Autowired private DriverRepository drivers;
        @Autowired private ExpenseRepository expenses;
    @Autowired private RouteRepository routes;
    @Autowired private TripRepository trips;

    private Organization organization;
    private Branch homeBranch;
    private Branch otherBranch;
    private AppUser branchManager;
    private AppUser superAdmin;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        organization = new Organization();
        organization.setName("Test org " + unique);
        organization = organizations.save(organization);

        homeBranch = saveBranch("Home " + unique, organization);
        otherBranch = saveBranch("Other " + unique, organization);
        branchManager = saveUser("manager-" + unique, Role.BRANCH_MANAGER, homeBranch, organization);
        superAdmin = saveUser("admin-" + unique, Role.SUPER_ADMIN, null, organization);
        homeBranch.setManagerUserId(branchManager.getId());
        branches.save(homeBranch);
    }

    @Test
    void loginUsesBcryptAndIssuesJwt() throws Exception {
        assertTrue(passwordEncoder.matches(PASSWORD, branchManager.getPassword()));

        String token = login(branchManager.getUsername());

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BRANCH_MANAGER"))
                .andExpect(jsonPath("$.branchId").value(homeBranch.getId()));
    }

    @Test
    void publicRegistrationCannotGrantPrivilegedRole() throws Exception {
        String username = "registered-" + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.invalid\",\"password\":\"registration-pass\",\"phone\":\" +91 98765 43210 \",\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.phone").value("+91 98765 43210"))
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        AppUser registered = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertEquals("+91 98765 43210", registered.getPhone());
        assertEquals("+91 98765 43210", drivers.findByUserId(registered.getId()).orElseThrow().getPhone());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+91 98765 43210"));
        mockMvc.perform(get("/api/organizations").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicRegistrationRequiresPhoneNumber() throws Exception {
        String username = "registered-no-phone-" + UUID.randomUUID();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.invalid\",\"password\":\"registration-pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productionConfigurationRejectsWeakSecretsAndDevelopmentCors() {
        assertDoesNotThrow(() -> new ProductionConfigurationValidator(
                "a-long-random-production-secret-value", "jdbc:postgresql://db.example:5432/driveops", "app", "secret",
                "https://app.example.com", "/var/lib/driveops/uploads"));
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationValidator(
                "short", "jdbc:postgresql://db.example:5432/driveops", "app", "secret",
                "https://app.example.com", "/var/lib/driveops/uploads"));
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationValidator(
                "a-long-random-production-secret-value", "jdbc:postgresql://db.example:5432/driveops", "app", "secret",
                "http://localhost:3000", "/var/lib/driveops/uploads"));
    }

    @Test
    void protectsApisAndRestrictsBranchManagerToOwnBranch() throws Exception {
        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isUnauthorized());

        String token = login(branchManager.getUsername());
        mockMvc.perform(get("/api/organizations").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/vehicles")
                        .param("branchId", otherBranch.getId().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/branches").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(homeBranch.getId()));
    }

    @Test
    void superAdminCanAccessOrganizationManagement() throws Exception {
        String token = login(superAdmin.getUsername());
        mockMvc.perform(get("/api/organizations").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void driverCanOnlyReadOwnProfileVehicleAndTrips() throws Exception {
        AppUser driverUser = saveUser("driver-" + UUID.randomUUID(), Role.DRIVER, homeBranch, organization);
        Driver driver = saveDriver("linked", homeBranch);
        driver.setUserId(driverUser.getId());
        Vehicle assignedVehicle = saveVehicle("driver-assigned", homeBranch);
        driver.setAssignedVehicleId(assignedVehicle.getId());
        drivers.save(driver);

        saveDriver("another", homeBranch);
        saveVehicle("another", homeBranch);
        Route route = saveRoute(homeBranch);
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        Trip ownTrip = saveTrip(route, start, start.plusHours(1));
        ownTrip.setDriver(driver);
        ownTrip.setVehicle(assignedVehicle);
        ownTrip.setStatus(TripStatus.ASSIGNED);
        trips.save(ownTrip);
        Trip anotherTrip = saveTrip(route, start.plusDays(1), start.plusDays(1).plusHours(1));
        anotherTrip.setDriver(saveDriver("different-trip", homeBranch));
        trips.save(anotherTrip);

        String token = login(driverUser.getUsername());
        mockMvc.perform(get("/api/vehicles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(assignedVehicle.getId()));
        mockMvc.perform(get("/api/drivers").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(driver.getId()));
        mockMvc.perform(get("/api/trips").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownTrip.getId()))
                .andExpect(jsonPath("$.content[0].routeSource").value("Test source"))
                .andExpect(jsonPath("$.content[0].routeDestination").value("Test destination"));
        mockMvc.perform(get("/api/trips").param("branchId", otherBranch.getId().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/trips/{id}/status", ownTrip.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"STARTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));
        mockMvc.perform(post("/api/trips/{id}/status", ownTrip.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELAYED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"));
        mockMvc.perform(post("/api/trips/{id}/status", ownTrip.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/vehicles/{id}/history", assignedVehicle.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/fuel")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + assignedVehicle.getId()
                                + ",\"quantity\":20,\"pricePerLiter\":100,\"odometer\":12345}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + assignedVehicle.getId()
                                + ",\"category\":\"TOLL\",\"amount\":350,\"description\":\"Trip toll\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/expenses").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverProfile.licenseNumber").exists());
        mockMvc.perform(get("/api/branches").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"New-password-123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + driverUser.getUsername() + "\",\"password\":\"New-password-123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void driverCanSubmitTypedMultipartIncidentReport() throws Exception {
        AppUser driverUser = saveUser("incident-driver-" + UUID.randomUUID(), Role.DRIVER, homeBranch, organization);
        Driver driver = saveDriver("incident", homeBranch);
        driver.setUserId(driverUser.getId());
        drivers.save(driver);
        String token = login(driverUser.getUsername());
        MockMultipartFile photo = new MockMultipartFile("photo", "", "image/png", new byte[0]);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/incidents")
                        .file(photo)
                        .param("incidentType", "VEHICLE_BREAKDOWN")
                        .param("severity", "HIGH")
                        .param("location", "Highway")
                        .param("description", "Engine stopped")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentType").value("VEHICLE_BREAKDOWN"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.hasPhoto").value(false));
    }

    @Test
    void financeOfficerCanReadOnlyTheirOrganizationFleetData() throws Exception {
        Organization foreignOrganization = new Organization();
        foreignOrganization.setName("Foreign org " + UUID.randomUUID());
        foreignOrganization = organizations.save(foreignOrganization);
        Branch foreignBranch = saveBranch("Foreign branch " + UUID.randomUUID(), foreignOrganization);
        AppUser finance = saveUser("finance-" + UUID.randomUUID(), Role.FINANCE_OFFICER, homeBranch, organization);
        Vehicle visibleVehicle = saveVehicle("finance-visible", homeBranch);
        saveVehicle("finance-hidden", foreignBranch);

        String token = login(finance.getUsername());
        mockMvc.perform(get("/api/branches").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/vehicles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(visibleVehicle.getId()));
        mockMvc.perform(get("/api/finance/summary")
                        .param("branchId", otherBranch.getId().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/finance/summary")
                        .param("branchId", foreignBranch.getId().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(0));
        mockMvc.perform(get("/api/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\":\"FINANCE-UNAUTHORIZED\",\"branchId\":" + homeBranch.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeApisEnforceRoleMatrixAndScopeArchivedReportsByOrganization() throws Exception {
        Organization foreignOrganization = new Organization();
        foreignOrganization.setName("Report isolation " + UUID.randomUUID());
        foreignOrganization = organizations.save(foreignOrganization);
        Branch foreignBranch = saveBranch("Report foreign branch " + UUID.randomUUID(), foreignOrganization);

        YearMonth period = YearMonth.now();
        LocalDateTime start = period.atDay(2).atTime(10, 0);
        Trip ownTrip = saveTrip(saveRoute(homeBranch), start, start.plusHours(2));
        ownTrip.setStatus(TripStatus.COMPLETED);
        ownTrip.setActualEndDate(start.plusHours(1));
        ownTrip.setRevenueAmount(new BigDecimal("125.00"));
        trips.save(ownTrip);

        Trip foreignTrip = saveTrip(saveRoute(foreignBranch), start, start.plusHours(2));
        foreignTrip.setStatus(TripStatus.COMPLETED);
        foreignTrip.setActualEndDate(start.plusHours(1));
        foreignTrip.setRevenueAmount(new BigDecimal("9000.00"));
        trips.save(foreignTrip);

        String periodBody = "{\"year\":" + period.getYear() + ",\"month\":" + period.getMonthValue() + "}";
        MvcResult generated = mockMvc.perform(post("/api/reports/monthly/generate")
                        .header("Authorization", bearer(login(superAdmin.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody))
                .andExpect(status().isOk())
                .andReturn();
        long reportId = objectMapper.readTree(generated.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/finance/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/monthly")).andExpect(status().isUnauthorized());

        for (Role role : Role.values()) {
            AppUser user = role == Role.SUPER_ADMIN ? superAdmin
                    : saveUser("finance-access-" + role + "-" + UUID.randomUUID(), role, homeBranch, organization);
            Driver driver = null;
            if (role == Role.DRIVER) {
                driver = saveDriver("finance-access", homeBranch);
                driver.setUserId(user.getId());
                drivers.save(driver);
            }
            String token = login(user.getUsername());
            boolean canReadFinance = role == Role.SUPER_ADMIN || role == Role.FINANCE_OFFICER;
            boolean canListExpenseRecords = canReadFinance || role == Role.DRIVER;

            if (!canListExpenseRecords) {
                mockMvc.perform(post("/api/expenses")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(get("/api/finance/summary").header("Authorization", bearer(token)))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());
            mockMvc.perform(get("/api/finance/history").header("Authorization", bearer(token)))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());
            mockMvc.perform(get("/api/finance/details").header("Authorization", bearer(token)))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());
            mockMvc.perform(get("/api/finance/export").header("Authorization", bearer(token)))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());
            mockMvc.perform(get("/api/reports/monthly").header("Authorization", bearer(token)))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());
            mockMvc.perform(get("/api/reports/monthly/{id}", reportId).header("Authorization", bearer(token)))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());
            mockMvc.perform(get("/api/reports/monthly/{id}/export", reportId)
                            .header("Authorization", bearer(token)))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());
            mockMvc.perform(post("/api/reports/monthly/generate")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(periodBody))
                    .andExpect(role == Role.SUPER_ADMIN ? status().isOk() : status().isForbidden());

            mockMvc.perform(post("/api/trips/{id}/revenue", ownTrip.getId())
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revenueAmount\":125.00}"))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());

            Expense pending = new Expense();
            pending.setBranch(homeBranch);
            pending.setTrip(ownTrip);
            pending.setDriver(driver);
            pending.setCategory(ExpenseCategory.TOLL);
            pending.setAmount(25.0);
            pending.setDate(period.atDay(2));
            pending.setStatus(ExpenseStatus.PENDING);
            expenses.save(pending);
            mockMvc.perform(get("/api/expenses").header("Authorization", bearer(token)))
                    .andExpect(canListExpenseRecords ? status().isOk() : status().isForbidden());
            mockMvc.perform(post("/api/expenses/{id}/review", pending.getId())
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"approve\":true}"))
                    .andExpect(canReadFinance ? status().isOk() : status().isForbidden());

            MvcResult dashboardResult = mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode dashboard = objectMapper.readTree(dashboardResult.getResponse().getContentAsString());
            assertTrue(dashboard.has("totalRevenue") == canReadFinance);
            assertTrue(dashboard.has("totalExpenses") == canReadFinance);
            assertTrue(dashboard.has("finance") == canReadFinance);

            if (role == Role.FLEET_MANAGER || role == Role.BRANCH_MANAGER) {
                MvcResult tripsResult = mockMvc.perform(get("/api/trips").header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn();
                JsonNode tripRows = objectMapper.readTree(tripsResult.getResponse().getContentAsString()).get("content");
                assertTrue(tripRows.isEmpty() || !tripRows.get(0).has("revenueAmount"));
            }
        }

        AppUser financeOfficer = saveUser("finance-report-scope-" + UUID.randomUUID(),
                Role.FINANCE_OFFICER, homeBranch, organization);
        String financeToken = login(financeOfficer.getUsername());
        MvcResult scopedReportResult = mockMvc.perform(get("/api/reports/monthly/{id}", reportId)
                        .header("Authorization", bearer(financeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(125.00))
                .andReturn();
        JsonNode scopedReport = objectMapper.readTree(scopedReportResult.getResponse().getContentAsString());
        assertTrue(scopedReport.get("branches").size() == 2);
        assertTrue(scopedReport.get("branches").findValuesAsText("branchName").stream()
                .noneMatch(name -> name.contains("Report foreign branch")));

        MvcResult scopedExport = mockMvc.perform(get("/api/reports/monthly/{id}/export", reportId)
                        .header("Authorization", bearer(financeToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(!scopedExport.getResponse().getContentAsString().contains("9000.00"));
    }

    @Test
    void fleetManagerAndFinanceWorkflowUsesRealTripRevenueAndMonthEndReport() throws Exception {
        AppUser fleetManager = saveUser("fleet-" + UUID.randomUUID(), Role.FLEET_MANAGER, homeBranch, organization);
        AppUser financeOfficer = saveUser("finance-" + UUID.randomUUID(), Role.FINANCE_OFFICER, homeBranch, organization);
        AppUser driverUser = saveUser("driver-finance-" + UUID.randomUUID(), Role.DRIVER, homeBranch, organization);
        Driver driver = saveDriver("finance-flow", homeBranch);
        driver.setUserId(driverUser.getId());
        drivers.save(driver);
        Vehicle vehicle = saveVehicle("finance-flow", homeBranch);
        Route route = saveRoute(homeBranch);
        LocalDateTime start = LocalDateTime.now().plusDays(7).withNano(0);
        Trip trip = saveTrip(route, start, start.plusHours(2));

        mockMvc.perform(post("/api/trips/{id}/revenue", trip.getId())
                        .header("Authorization", bearer(login(financeOfficer.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revenueAmount\":1500.00}"))
                .andExpect(status().isBadRequest());

        String fleetToken = login(fleetManager.getUsername());
        mockMvc.perform(post("/api/trips/{id}/assign", trip.getId())
                        .header("Authorization", bearer(fleetToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicle.getId() + ",\"driverId\":" + driver.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        String driverToken = login(driverUser.getUsername());
        mockMvc.perform(post("/api/trips/{id}/status", trip.getId())
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"STARTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));
        mockMvc.perform(post("/api/trips/{id}/status", trip.getId())
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/trips/{id}/revenue", trip.getId())
                        .header("Authorization", bearer(login(financeOfficer.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revenueAmount\":1500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueAmount").value(1500.00));

        String todayIso = java.time.LocalDate.now().toString();
        mockMvc.perform(post("/api/fuel")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicle.getId() + ",\"driverId\":" + driver.getId() + ",\"quantity\":30,\"pricePerLiter\":12.50,\"odometer\":20000,\"date\":\"" + todayIso + "\"}"))
                .andExpect(status().isOk());

        MvcResult expensesResult = mockMvc.perform(get("/api/expenses")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode expensesBody = objectMapper.readTree(expensesResult.getResponse().getContentAsString());
        long fuelExpenseId = -1;
        for (JsonNode expense : expensesBody.get("content")) {
            if ("FUEL".equals(expense.get("category").asText())) {
                fuelExpenseId = expense.get("id").asLong();
                break;
            }
        }
        assertTrue(fuelExpenseId > 0, "Fuel expense should be present for approval.");
        mockMvc.perform(post("/api/expenses/{id}/review", fuelExpenseId)
                        .header("Authorization", bearer(login(financeOfficer.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk());

        MvcResult tollResult = mockMvc.perform(post("/api/expenses")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicle.getId() + ",\"tripId\":" + trip.getId() + ",\"category\":\"TOLL\",\"amount\":250.00,\"date\":\"" + todayIso + "\",\"description\":\"Trip toll\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long tollExpenseId = objectMapper.readTree(tollResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/expenses/{id}/review", tollExpenseId)
                        .header("Authorization", bearer(login(financeOfficer.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/expenses/{id}/review", tollExpenseId)
                        .header("Authorization", bearer(login(financeOfficer.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isConflict());

        String financeToken = login(financeOfficer.getUsername());
        mockMvc.perform(get("/api/finance/summary")
                        .header("Authorization", bearer(financeToken))
                        .param("year", String.valueOf(java.time.LocalDate.now().getYear()))
                        .param("month", String.valueOf(java.time.LocalDate.now().getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1500.00))
                .andExpect(jsonPath("$.totalExpenses").value(625.00))
                .andExpect(jsonPath("$.netProfit").value(875.00));

        MvcResult reportResult = mockMvc.perform(post("/api/reports/monthly/generate")
                        .header("Authorization", bearer(login(superAdmin.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\":" + java.time.LocalDate.now().getYear() + ",\"month\":" + java.time.LocalDate.now().getMonthValue() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reportBody = objectMapper.readTree(reportResult.getResponse().getContentAsString());
        long reportId = reportBody.get("id").asLong();
        mockMvc.perform(get("/api/reports/monthly/{id}", reportId)
                        .header("Authorization", bearer(login(superAdmin.getUsername()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.totalRevenue").value(1500.00))
                .andExpect(jsonPath("$.totalExpenses").value(625.00))
                .andExpect(jsonPath("$.netProfit").value(875.00));

        mockMvc.perform(post("/api/reports/monthly/generate")
                        .header("Authorization", bearer(login(superAdmin.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\":" + java.time.LocalDate.now().getYear() + ",\"month\":" + java.time.LocalDate.now().getMonthValue() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId));

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(login(superAdmin.getUsername()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("MONTHLY_REPORT"));
    }

    @Test
    void historicalFinanceAndReportSnapshotUsePersistedRecordsOnce() throws Exception {
        YearMonth period = YearMonth.now().minusMonths(2);
        Route route = saveRoute(homeBranch);
        LocalDateTime completedAt = period.atDay(15).atTime(12, 0);
        Trip trip = saveTrip(route, completedAt.minusHours(2), completedAt.plusHours(1));
        trip.setStatus(TripStatus.COMPLETED);
        trip.setActualEndDate(completedAt);
        trip.setRevenueAmount(new BigDecimal("1000.00"));
        trip.setDistanceKm(80.0);
        trips.save(trip);

        Expense approvedExpense = new Expense();
        approvedExpense.setBranch(homeBranch);
        approvedExpense.setTrip(trip);
        approvedExpense.setCategory(ExpenseCategory.TOLL);
        approvedExpense.setAmount(125.0);
        approvedExpense.setDate(period.atDay(16));
        approvedExpense.setStatus(ExpenseStatus.APPROVED);
        expenses.save(approvedExpense);

        Expense pendingExpense = new Expense();
        pendingExpense.setBranch(homeBranch);
        pendingExpense.setTrip(trip);
        pendingExpense.setCategory(ExpenseCategory.PARKING);
        pendingExpense.setAmount(40.0);
        pendingExpense.setDate(period.atDay(17));
        pendingExpense.setStatus(ExpenseStatus.PENDING);
        expenses.save(pendingExpense);

        AppUser finance = saveUser("historical-finance-" + UUID.randomUUID(), Role.FINANCE_OFFICER, homeBranch, organization);
        String financeToken = login(finance.getUsername());
        mockMvc.perform(get("/api/finance/summary").header("Authorization", bearer(financeToken))
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", String.valueOf(period.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1000.00))
                .andExpect(jsonPath("$.totalExpenses").value(125.00))
                .andExpect(jsonPath("$.netProfit").value(875.00));
        mockMvc.perform(get("/api/finance/summary").header("Authorization", bearer(financeToken))
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", "13"))
                .andExpect(status().isBadRequest());

        String reportRequest = "{\"year\":" + period.getYear() + ",\"month\":" + period.getMonthValue() + "}";
        mockMvc.perform(post("/api/reports/monthly/generate")
                        .header("Authorization", bearer(login(superAdmin.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\":" + period.getYear() + ",\"month\":13}"))
                .andExpect(status().isBadRequest());
        MvcResult generated = mockMvc.perform(post("/api/reports/monthly/generate")
                        .header("Authorization", bearer(login(superAdmin.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1000.00))
                .andExpect(jsonPath("$.totalExpenses").value(125.00))
                .andExpect(jsonPath("$.netProfit").value(875.00))
                .andReturn();
        long reportId = objectMapper.readTree(generated.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/reports/monthly/generate")
                        .header("Authorization", bearer(login(superAdmin.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.snapshotVersion").value(1))
                .andExpect(jsonPath("$.totalExpenses").value(125.00));

        mockMvc.perform(post("/api/reports/monthly/generate")
                        .header("Authorization", bearer(login(superAdmin.getUsername())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\":" + period.getYear() + ",\"month\":" + period.getMonthValue() + ",\"regenerate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.snapshotVersion").value(2))
                .andExpect(jsonPath("$.totalRevenue").value(1000.00))
                .andExpect(jsonPath("$.totalExpenses").value(125.00))
                .andExpect(jsonPath("$.netProfit").value(875.00));
    }

    @Test
    void fleetManagerCanAccessVisibleOperationsWithoutCrossBranchAccess() throws Exception {
        AppUser fleetManager = saveUser("manager-fleet-" + UUID.randomUUID(), Role.FLEET_MANAGER, homeBranch, organization);
        AppUser otherUser = saveUser("other-" + UUID.randomUUID(), Role.BRANCH_MANAGER, otherBranch, organization);
        String token = login(fleetManager.getUsername());

        mockMvc.perform(get("/api/branches").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/vehicles").param("branchId", otherBranch.getId().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users").param("branchId", otherBranch.getId().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/branches").header("Authorization", bearer(login(otherUser.getUsername()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void assignmentRejectsOverlappingVehicleOrDriverBookings() throws Exception {
        String token = login(branchManager.getUsername());
        Route route = saveRoute(homeBranch);
        Vehicle reservedVehicle = saveVehicle("reserved", homeBranch);
        Vehicle availableVehicle = saveVehicle("available", homeBranch);
        Driver reservedDriver = saveDriver("reserved", homeBranch);
        Driver availableDriver = saveDriver("available", homeBranch);
        LocalDateTime start = LocalDateTime.now().plusDays(3).withNano(0);
        Trip existing = saveTrip(route, start, start.plusHours(2));
        existing.setVehicle(reservedVehicle);
        existing.setDriver(reservedDriver);
        existing.setStatus(TripStatus.ASSIGNED);
        trips.save(existing);

        Trip vehicleConflict = saveTrip(route, start.plusMinutes(30), start.plusHours(3));
        mockMvc.perform(post("/api/trips/{id}/assign", vehicleConflict.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + reservedVehicle.getId()
                                + ",\"driverId\":" + availableDriver.getId() + "}"))
                .andExpect(status().isConflict());

        Trip driverConflict = saveTrip(route, start.plusMinutes(30), start.plusHours(3));
        mockMvc.perform(post("/api/trips/{id}/assign", driverConflict.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + availableVehicle.getId()
                                + ",\"driverId\":" + reservedDriver.getId() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void assignmentUpdatesVehicleAndDriverAvailability() throws Exception {
        String token = login(branchManager.getUsername());
        Route route = saveRoute(homeBranch);
        Vehicle vehicle = saveVehicle("assign", homeBranch);
        Driver driver = saveDriver("assign", homeBranch);
        LocalDateTime start = LocalDateTime.now().plusDays(5).withNano(0);
        Trip trip = saveTrip(route, start, start.plusHours(2));

        mockMvc.perform(post("/api/trips/{id}/assign", trip.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicle.getId() + ",\"driverId\":" + driver.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.vehicleId").value(vehicle.getId()))
                .andExpect(jsonPath("$.driverId").value(driver.getId()));

        assertTrue(vehicles.findById(vehicle.getId()).orElseThrow().getStatus() == VehicleStatus.ASSIGNED);
        assertTrue(drivers.findById(driver.getId()).orElseThrow().getStatus() == DriverStatus.ASSIGNED);
    }

    private Branch saveBranch(String name, Organization owner) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setOrganization(owner);
        return branches.save(branch);
    }

    private AppUser saveUser(String username, Role role, Branch branch, Organization owner) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@test.invalid");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setBranch(branch);
        user.setOrganization(owner);
        return users.save(user);
    }

    private Vehicle saveVehicle(String suffix, Branch branch) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("TEST-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setBranch(branch);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        return vehicles.save(vehicle);
    }

    private Driver saveDriver(String suffix, Branch branch) {
        Driver driver = new Driver();
        driver.setName("Test driver " + suffix);
        driver.setLicenseNumber("TEST-LIC-" + UUID.randomUUID().toString().substring(0, 8));
        driver.setBranch(branch);
        driver.setStatus(DriverStatus.AVAILABLE);
        return drivers.save(driver);
    }

    private Route saveRoute(Branch branch) {
        Route route = new Route();
        route.setSource("Test source");
        route.setDestination("Test destination");
        route.setBranch(branch);
        return routes.save(route);
    }

    private Trip saveTrip(Route route, LocalDateTime start, LocalDateTime end) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setBranch(route.getBranch());
        trip.setStartDate(start);
        trip.setExpectedEndDate(end);
        trip.setStatus(TripStatus.PLANNED);
        return trips.save(trip);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
