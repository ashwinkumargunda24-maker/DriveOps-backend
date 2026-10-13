package com.example.demo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.domain.AppNotification;
import com.example.demo.domain.AppUser;
import com.example.demo.domain.Branch;
import com.example.demo.domain.Codes.DocumentKind;
import com.example.demo.domain.Codes.DocumentOwnerType;
import com.example.demo.domain.Codes.DriverStatus;
import com.example.demo.domain.Codes.ExpenseCategory;
import com.example.demo.domain.Codes.ExpenseStatus;
import com.example.demo.domain.Codes.IncidentSeverity;
import com.example.demo.domain.Codes.IncidentStatus;
import com.example.demo.domain.Codes.MaintenanceStatus;
import com.example.demo.domain.Codes.MaintenanceType;
import com.example.demo.domain.Codes.Role;
import com.example.demo.domain.Codes.TripStatus;
import com.example.demo.domain.Codes.VehicleStatus;
import com.example.demo.domain.Driver;
import com.example.demo.domain.Expense;
import com.example.demo.domain.FleetDocument;
import com.example.demo.domain.FuelRecord;
import com.example.demo.domain.Incident;
import com.example.demo.domain.MaintenanceRecord;
import com.example.demo.domain.Organization;
import com.example.demo.domain.Route;
import com.example.demo.domain.Trip;
import com.example.demo.domain.TripStatusHistory;
import com.example.demo.domain.Vehicle;
import com.example.demo.dto.PageResponse;
import com.example.demo.repo.Repos;
import com.example.demo.security.AccessGuard;
import com.example.demo.web.ApiException;

@Service
@Transactional
public class FleetService {
    private final Repos repos;
    private final AccessGuard access;
    private final PasswordEncoder encoder;
    private final Path uploadDir;
    private final MonthlyFinanceService monthlyFinance;
    private final java.time.Clock clock;

    public FleetService(Repos repos, AccessGuard access, PasswordEncoder encoder,
                        @Value("${app.upload-dir}") String uploadDir,
                        MonthlyFinanceService monthlyFinance,
                        java.time.Clock clock) {
        this.repos = repos;
        this.access = access;
        this.encoder = encoder;
        this.uploadDir = Path.of(uploadDir);
        this.monthlyFinance = monthlyFinance;
        this.clock = clock;
    }

    public PageResponse<Map<String, Object>> organizations(int page, int size) {
        access.require(Role.SUPER_ADMIN);
        var result = repos.organizations.findAll(PageRequest.of(page, size, Sort.by("id").descending()));
        return pageOf(result, this::orgMap);
    }

    public Map<String, Object> saveOrganization(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN);
        Organization org = id == null ? new Organization() : repos.organizations.findById(id)
                .orElseThrow(() -> new ApiException(404, "Organization not found."));
        org.setName(required(body, "name", "Organization name is required."));
        org.setContactEmail(str(body, "contactEmail"));
        org.setContactPhone(str(body, "contactPhone"));
        org.setAddress(str(body, "address"));
        if (body.get("active") instanceof Boolean active) {
            org.setActive(active);
        }
        repos.organizations.save(org);
        access.audit(repos, id == null ? "Created organization" : "Updated organization", "Organization", org.getId());
        return orgMap(org);
    }

    public PageResponse<Map<String, Object>> branches(String q, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.FINANCE_OFFICER, Role.BRANCH_MANAGER);
        Long scoped = access.scopedBranchId(null);
        if (scoped != null) {
            Branch branch = repos.branches.findById(scoped)
                    .orElseThrow(() -> new ApiException(404, "Branch not found."));
            return new PageResponse<>(List.of(branchMap(branch)), 0, 1, 1, 1);
        }
        var result = access.is(Role.SUPER_ADMIN)
                ? repos.branches.findByNameContainingIgnoreCase(q == null ? "" : q, PageRequest.of(page, size))
                : repos.branches.findByOrganization_IdAndNameContainingIgnoreCase(
                        access.organizationScopeId(), q == null ? "" : q, PageRequest.of(page, size));
        return pageOf(result, this::branchMap);
    }

    public Map<String, Object> saveBranch(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        if (id == null && !access.is(Role.SUPER_ADMIN)) {
            throw new ApiException(403, "Only a Super Admin can create a branch.");
        }
        Branch branch = id == null ? new Branch() : repos.branches.findById(id)
                .orElseThrow(() -> new ApiException(404, "Branch not found."));
        if (id != null) {
            access.assertBranch(branch);
        }
        if (access.is(Role.SUPER_ADMIN)) {
            Organization org = repos.organizations.findById(longVal(body.get("organizationId")))
                    .orElseThrow(() -> new ApiException(400, "Organization is required."));
            branch.setOrganization(org);
        }
        branch.setName(required(body, "name", "Branch name is required."));
        branch.setLocation(str(body, "location"));
        branch.setAddress(str(body, "address"));
        branch.setContactNumber(str(body, "contactNumber"));
        if (access.is(Role.SUPER_ADMIN) && body.get("managerUserId") != null) {
            branch.setManagerUserId(longVal(body.get("managerUserId")));
        }
        if (body.get("active") instanceof Boolean active) {
            branch.setActive(active);
        }
        repos.branches.save(branch);
        access.audit(repos, id == null ? "Created branch" : "Updated branch", "Branch", branch.getId());
        return branchMap(branch);
    }

    public Map<String, Object> branchStats(Long id) {
        Branch branch = repos.branches.findById(id).orElseThrow(() -> new ApiException(404, "Branch not found."));
        access.assertBranch(branch);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("branch", branchMap(branch));
        stats.put("vehicles", repos.vehicles.countByBranch_Id(id));
        stats.put("drivers", repos.drivers.countByBranch_Id(id));
        stats.put("trips", repos.trips.countByBranch_Id(id));
        return stats;
    }

    public PageResponse<Map<String, Object>> users(String q, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Long organizationId = access.organizationScopeId();
        Long branchId = access.is(Role.BRANCH_MANAGER) ? access.scopedBranchId(null) : null;
        return pageOf(repos.users.searchScoped(organizationId, branchId, blankToNull(q),
                PageRequest.of(page, size)), this::userMap);
    }

    public Map<String, Object> saveUser(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN);
        AppUser user = id == null ? new AppUser() : repos.users.findById(id)
                .orElseThrow(() -> new ApiException(404, "User not found."));
        String username = required(body, "username", "Username is required.");
        String email = required(body, "email", "Email is required.");
        if (id == null && (repos.users.existsByUsernameIgnoreCase(username) || repos.users.existsByEmailIgnoreCase(email))) {
            throw new ApiException(409, "Username or email is already in use.");
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(str(body, "fullName"));
        user.setPhone(str(body, "phone"));
        user.setRole(Role.valueOf(required(body, "role", "Role is required.")));
        if (body.get("organizationId") != null) {
            user.setOrganization(repos.organizations.findById(longVal(body.get("organizationId"))).orElse(null));
        }
        if (body.get("branchId") != null) {
            user.setBranch(repos.branches.findById(longVal(body.get("branchId"))).orElse(null));
        }
        if (body.get("active") instanceof Boolean active) {
            user.setActive(active);
        }
        if (id == null) {
            String password = str(body, "password");
            if (password == null || password.length() < 6) {
                throw new ApiException(400, "Password must be at least 6 characters.");
            }
            user.setPassword(encoder.encode(password));
        }
        repos.users.save(user);
        access.audit(repos, id == null ? "Created user" : "Updated user", "User", user.getId());
        return userMap(user);
    }

    public Map<String, Object> resetPassword(Long id, String password) {
        access.require(Role.SUPER_ADMIN);
        if (password == null || password.length() < 6) {
            throw new ApiException(400, "Password must be at least 6 characters.");
        }
        AppUser user = repos.users.findById(id).orElseThrow(() -> new ApiException(404, "User not found."));
        user.setPassword(encoder.encode(password));
        repos.users.save(user);
        access.audit(repos, "Reset password", "User", id);
        return Map.of("message", "Password reset.");
    }

    public PageResponse<Map<String, Object>> vehicles(String q, String status, Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER, Role.FINANCE_OFFICER);
        Long scoped = access.scopedBranchId(branchId);
        Long assignedVehicleId = access.is(Role.DRIVER)
                ? java.util.Objects.requireNonNullElse(currentDriver().getAssignedVehicleId(), -1L) : null;
        VehicleStatus st = parseEnum(VehicleStatus.class, status);
        var result = repos.vehicles.search(scoped, access.organizationScopeId(), assignedVehicleId, st,
                blankToNull(q), PageRequest.of(page, size, Sort.by("id").descending()));
        return pageOf(result, this::vehicleMap);
    }

    public Map<String, Object> saveVehicle(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Vehicle vehicle = id == null ? new Vehicle() : loadVehicle(id);

        String reg = required(body, "registrationNumber", "Registration number is required.");
        Long branchId = longVal(body.get("branchId"));
        if (branchId == null) {
            throw new ApiException(400, "Branch is required.");
        }
        Branch branch = repos.branches.findById(branchId)
                .orElseThrow(() -> new ApiException(404, "Branch not found."));
        access.assertBranch(branch);

        String vehicleType = required(body, "vehicleType", "Vehicle type is required.");
        String manufacturer = required(body, "manufacturer", "Manufacturer is required.");
        String model = required(body, "model", "Model is required.");
        Integer year = intVal(body.get("year"));
        if (year == null || year < 1900 || year > LocalDate.now().getYear() + 1) {
            throw new ApiException(400, "Manufacturing year is required and must be valid.");
        }
        String fuelType = required(body, "fuelType", "Fuel type is required.");
        Integer currentMileage = intVal(body.get("currentMileage"));
        if (currentMileage == null || currentMileage < 0) {
            throw new ApiException(400, "Current odometer reading is required and cannot be negative.");
        }

        if (id == null) {
            if (repos.vehicles.existsByRegistrationNumberIgnoreCase(reg)) {
                throw new ApiException(409, "Registration number already exists.");
            }
        } else if (!vehicle.getRegistrationNumber().equalsIgnoreCase(reg)
                && repos.vehicles.existsByRegistrationNumberIgnoreCase(reg)) {
            throw new ApiException(409, "Registration number already exists.");
        }

        vehicle.setRegistrationNumber(reg);
        vehicle.setVehicleType(vehicleType);
        vehicle.setManufacturer(manufacturer);
        vehicle.setModel(model);
        vehicle.setYear(year);
        vehicle.setFuelType(fuelType);
        vehicle.setCapacity(intVal(body.get("capacity")));
        vehicle.setCurrentMileage(currentMileage);
        vehicle.setBranch(branch);
        if (body.get("status") != null && !String.valueOf(body.get("status")).isBlank()) {
            vehicle.setStatus(VehicleStatus.valueOf(String.valueOf(body.get("status"))));
        } else if (id == null) {
            vehicle.setStatus(VehicleStatus.AVAILABLE);
        }
        vehicle.setInsuranceExpiry(date(body.get("insuranceExpiry")));
        vehicle.setRegistrationExpiry(date(body.get("registrationExpiry")));
        vehicle.setPollutionExpiry(date(body.get("pollutionExpiry")));
        if (body.get("active") instanceof Boolean active) {
            vehicle.setActive(active);
            if (!active) {
                vehicle.setStatus(VehicleStatus.INACTIVE);
            }
        }
        repos.vehicles.save(vehicle);
        access.audit(repos, id == null ? "Created vehicle" : "Updated vehicle", "Vehicle", vehicle.getId());
        return vehicleMap(vehicle);
    }

    public Map<String, Object> vehicleHistory(Long id) {
        Vehicle vehicle = loadVehicle(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicle", vehicleMap(vehicle));
        body.put("trips", repos.trips.search(vehicle.getBranch().getId(), access.organizationScopeId(), null, null, null, PageRequest.of(0, 50))
                .getContent().stream().filter(t -> t.getVehicle() != null && t.getVehicle().getId().equals(id)).map(this::tripMap).toList());
        body.put("maintenance", repos.maintenance.findByVehicle_IdOrderByServiceDateDesc(id).stream().map(this::maintenanceMap).toList());
        body.put("fuel", repos.fuel.findByVehicle_IdOrderByDateAsc(id).stream().map(this::fuelMap).toList());
        body.put("documents", repos.documents.findByOwnerTypeAndOwnerId(DocumentOwnerType.VEHICLE, id).stream().map(this::documentMap).toList());
        return body;
    }

    public PageResponse<Map<String, Object>> drivers(String q, String status, Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER, Role.FINANCE_OFFICER);
        Long scoped = access.scopedBranchId(branchId);
        Long userId = access.is(Role.DRIVER) ? access.user().getId() : null;
        var result = repos.drivers.search(scoped, access.organizationScopeId(), userId,
                parseEnum(DriverStatus.class, status), blankToNull(q),
                PageRequest.of(page, size, Sort.by("id").descending()));
        return pageOf(result, this::driverMap);
    }

    public Map<String, Object> saveDriver(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Driver driver = id == null ? new Driver() : loadDriver(id);
        String license = required(body, "licenseNumber", "License number is required.");
        LocalDate expiry = date(body.get("licenseExpiry"));
        if (expiry != null && expiry.isBefore(LocalDate.now())) {
            throw new ApiException(400, "License expiry date cannot be in the past.");
        }
        if (id == null && repos.drivers.existsByLicenseNumberIgnoreCase(license)) {
            throw new ApiException(409, "License number already exists.");
        }
        Branch branch = repos.branches.findById(longVal(body.get("branchId")))
                .orElseThrow(() -> new ApiException(400, "Branch is required."));
        access.assertBranch(branch);
        driver.setName(required(body, "name", "Driver name is required."));
        driver.setPhone(str(body, "phone"));
        driver.setEmail(str(body, "email"));
        driver.setAddress(str(body, "address"));
        driver.setLicenseNumber(license);
        driver.setLicenseType(str(body, "licenseType"));
        driver.setLicenseExpiry(expiry);
        driver.setJoiningDate(date(body.get("joiningDate")));
        driver.setBranch(branch);
        if (body.get("status") != null) {
            driver.setStatus(DriverStatus.valueOf(String.valueOf(body.get("status"))));
        }
        if (body.get("userId") != null) {
            driver.setUserId(longVal(body.get("userId")));
        }
        if (body.get("active") instanceof Boolean active) {
            driver.setActive(active);
            if (!active) {
                driver.setStatus(DriverStatus.INACTIVE);
            }
        }
        repos.drivers.save(driver);
        access.audit(repos, id == null ? "Created driver" : "Updated driver", "Driver", driver.getId());
        return driverMap(driver);
    }

    public Map<String, Object> driverHistory(Long id) {
        Driver driver = loadDriver(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("driver", driverMap(driver));
        body.put("trips", repos.trips.findByDriver_IdOrderByStartDateDesc(id).stream().map(this::tripMap).toList());
        body.put("incidents", repos.incidents.findByDriver_IdOrderByOccurredAtDesc(id).stream().map(this::incidentMap).toList());
        body.put("fuel", repos.fuel.findByDriver_IdOrderByDateDesc(id).stream().map(this::fuelMap).toList());
        return body;
    }

    public PageResponse<Map<String, Object>> routes(String q, Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER, Role.FINANCE_OFFICER);
        Long scoped = access.scopedBranchId(branchId);
        Long driverId = access.is(Role.DRIVER) ? currentDriver().getId() : null;
        return pageOf(repos.routes.search(scoped, access.organizationScopeId(), driverId,
                blankToNull(q), PageRequest.of(page, size)), this::routeMap);
    }

    public Map<String, Object> saveRoute(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Route route = id == null ? new Route() : repos.routes.findById(id)
                .orElseThrow(() -> new ApiException(404, "Route not found."));
        if (id != null) {
            access.assertBranch(route.getBranch());
        }
        Branch branch = repos.branches.findById(longVal(body.get("branchId")))
                .orElseThrow(() -> new ApiException(400, "Branch is required."));
        access.assertBranch(branch);
        route.setSource(required(body, "source", "Source is required."));
        route.setDestination(required(body, "destination", "Destination is required."));
        route.setDistanceKm(doubleVal(body.get("distanceKm")));
        route.setEstimatedDurationMinutes(intVal(body.get("estimatedDurationMinutes")));
        if (route.getEstimatedDurationMinutes() == null && route.getDistanceKm() != null) {
            route.setEstimatedDurationMinutes((int) Math.round(route.getDistanceKm() / 40.0 * 60));
        }
        route.setDescription(str(body, "description"));
        route.setBranch(branch);
        repos.routes.save(route);
        access.audit(repos, id == null ? "Created route" : "Updated route", "Route", route.getId());
        return routeMap(route);
    }

    public void deleteRoute(Long id) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Route route = repos.routes.findById(id).orElseThrow(() -> new ApiException(404, "Route not found."));
        access.assertBranch(route.getBranch());
        repos.routes.delete(route);
        access.audit(repos, "Deleted route", "Route", id);
    }

    public PageResponse<Map<String, Object>> trips(String q, String status, Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.FINANCE_OFFICER, Role.DRIVER);
        Long scoped = access.scopedBranchId(branchId);
        Long driverId = null;
        if (access.is(Role.DRIVER)) {
            driverId = currentDriver().getId();
        }
        return pageOf(repos.trips.search(scoped, access.organizationScopeId(), parseEnum(TripStatus.class, status), driverId, blankToNull(q),
                PageRequest.of(page, size, Sort.by("id").descending())), this::tripMap);
    }

    public Map<String, Object> saveTrip(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Trip trip = id == null ? new Trip() : loadTrip(id);
        Route route = repos.routes.findById(longVal(body.get("routeId")))
                .orElseThrow(() -> new ApiException(400, "Route is required."));
        access.assertBranch(route.getBranch());
        trip.setRoute(route);
        trip.setBranch(route.getBranch());
        trip.setStartDate(dateTime(body.get("startDate")));
        trip.setExpectedEndDate(dateTime(body.get("expectedEndDate")));
        trip.setPurpose(str(body, "purpose"));
        trip.setDistanceKm(body.get("distanceKm") == null ? route.getDistanceKm() : doubleVal(body.get("distanceKm")));
        trip.setNotes(str(body, "notes"));
        if (id == null) {
            trip.setStatus(TripStatus.PLANNED);
        }
        repos.trips.save(trip);
        if (id == null) {
            recordStatus(trip, null, TripStatus.PLANNED, "Trip created");
        }
        access.audit(repos, id == null ? "Created trip" : "Updated trip", "Trip", trip.getId());
        return tripMap(trip);
    }

    public Map<String, Object> assignTrip(Long id, Long vehicleId, Long driverId) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Trip trip = loadTrip(id);
        Vehicle vehicle = loadVehicle(vehicleId);
        Driver driver = loadDriver(driverId);
        access.assertBranch(vehicle.getBranch());
        access.assertBranch(driver.getBranch());
        if (!vehicle.getBranch().getId().equals(trip.getBranch().getId())) {
            throw new ApiException(400, "The vehicle does not belong to the trip branch.");
        }
        if (!driver.getBranch().getId().equals(trip.getBranch().getId())) {
            throw new ApiException(400, "The driver does not belong to the trip branch.");
        }
        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE || vehicle.getStatus() == VehicleStatus.INACTIVE) {
            throw new ApiException(409, "Vehicle is under maintenance or inactive.");
        }
        if (driver.getStatus() == DriverStatus.ON_LEAVE || driver.getStatus() == DriverStatus.INACTIVE) {
            throw new ApiException(409, "Driver is unavailable.");
        }
        if (trip.getStartDate() == null || trip.getExpectedEndDate() == null) {
            throw new ApiException(400, "Trip start and end dates are required before assignment.");
        }
        if (repos.trips.countVehicleOverlap(vehicle.getId(), trip.getStartDate(), trip.getExpectedEndDate(), trip.getId()) > 0) {
            throw new ApiException(409, "Vehicle already assigned during this period.");
        }
        if (repos.trips.countDriverOverlap(driver.getId(), trip.getStartDate(), trip.getExpectedEndDate(), trip.getId()) > 0) {
            throw new ApiException(409, "Driver is already assigned during this period.");
        }
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        changeTripStatus(trip, TripStatus.ASSIGNED, "Vehicle and driver assigned");
        vehicle.setStatus(VehicleStatus.ASSIGNED);
        vehicle.setAssignedDriverId(driver.getId());
        driver.setStatus(DriverStatus.ASSIGNED);
        driver.setAssignedVehicleId(vehicle.getId());
        repos.vehicles.save(vehicle);
        repos.drivers.save(driver);
        notifyUser(driver.getUserId(), driver.getBranch().getId(), "TRIP", "New trip assigned to you.",
                "Trip #" + trip.getId() + " has been assigned.");
        access.audit(repos, "Assigned trip", "Trip", trip.getId());
        return tripMap(trip);
    }

    public Map<String, Object> updateTripStatus(Long id, String next, String remarks) {
        Trip trip = loadTrip(id);
        if (access.is(Role.DRIVER)) {
            Driver me = currentDriver();
            if (trip.getDriver() == null || !trip.getDriver().getId().equals(me.getId())) {
                throw new ApiException(403, "You can only update your own trips.");
            }
        } else {
            access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER);
        }
        TripStatus target = TripStatus.valueOf(next);
        changeTripStatus(trip, target, remarks);
        if (target == TripStatus.STARTED || target == TripStatus.DELAYED) {
            if (trip.getVehicle() != null) {
                trip.getVehicle().setStatus(VehicleStatus.ON_TRIP);
            }
            if (trip.getDriver() != null) {
                trip.getDriver().setStatus(DriverStatus.ON_TRIP);
            }
            if (target == TripStatus.DELAYED) {
                notifyManagers(trip, "TRIP", "Trip delayed.", "Trip #" + trip.getId() + " is delayed.");
            }
        }
        if (target == TripStatus.COMPLETED || target == TripStatus.CANCELLED) {
            trip.setActualEndDate(LocalDateTime.now(clock));
            if (trip.getVehicle() != null) {
                trip.getVehicle().setStatus(VehicleStatus.AVAILABLE);
                trip.getVehicle().setAssignedDriverId(null);
            }
            if (trip.getDriver() != null) {
                trip.getDriver().setStatus(DriverStatus.AVAILABLE);
                trip.getDriver().setAssignedVehicleId(null);
            }
        }
        access.audit(repos, "Updated trip status to " + target, "Trip", trip.getId());
        return tripMap(trip);
    }

    public List<Map<String, Object>> tripHistory(Long id) {
        loadTrip(id);
        return repos.tripHistory.findByTrip_IdOrderByChangedAtAsc(id).stream().map(h -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", h.getId());
            row.put("oldStatus", h.getOldStatus());
            row.put("newStatus", h.getNewStatus());
            row.put("changedBy", h.getChangedBy());
            row.put("changedAt", h.getChangedAt());
            row.put("remarks", h.getRemarks());
            return row;
        }).toList();
    }

    public PageResponse<Map<String, Object>> maintenance(String q, String status, Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.FINANCE_OFFICER, Role.DRIVER);
        Long vehicleId = access.is(Role.DRIVER)
                ? java.util.Objects.requireNonNullElse(currentDriver().getAssignedVehicleId(), -1L) : null;
        return pageOf(repos.maintenance.search(access.scopedBranchId(branchId), access.organizationScopeId(), vehicleId,
                parseEnum(MaintenanceStatus.class, status),
                blankToNull(q), PageRequest.of(page, size, Sort.by("id").descending())), this::maintenanceMap);
    }

    public Map<String, Object> saveMaintenance(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        MaintenanceRecord record = id == null ? new MaintenanceRecord() : repos.maintenance.findById(id)
                .orElseThrow(() -> new ApiException(404, "Maintenance record not found."));
        if (id != null) {
            access.assertBranch(record.getVehicle().getBranch());
        }
        Vehicle vehicle = loadVehicle(longVal(body.get("vehicleId")));
        access.assertBranch(vehicle.getBranch());
        record.setVehicle(vehicle);
        record.setServiceType(MaintenanceType.valueOf(required(body, "serviceType", "Service type is required.")));
        record.setServiceDate(date(body.get("serviceDate")));
        record.setCurrentMileage(intVal(body.get("currentMileage")));
        record.setServiceCost(doubleVal(body.get("serviceCost")));
        record.setNextServiceDate(date(body.get("nextServiceDate")));
        record.setNextServiceMileage(intVal(body.get("nextServiceMileage")));
        record.setDescription(str(body, "description"));
        MaintenanceStatus status = body.get("status") == null ? MaintenanceStatus.SCHEDULED
                : MaintenanceStatus.valueOf(String.valueOf(body.get("status")));
        if (status == MaintenanceStatus.SCHEDULED && record.getServiceDate() != null
                && record.getServiceDate().isBefore(LocalDate.now(clock))) {
            status = MaintenanceStatus.OVERDUE;
        }
        record.setStatus(status);
        if (status == MaintenanceStatus.IN_PROGRESS) {
            vehicle.setStatus(VehicleStatus.MAINTENANCE);
        }
        if (status == MaintenanceStatus.COMPLETED && vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            vehicle.setStatus(VehicleStatus.AVAILABLE);
        }
        repos.maintenance.save(record);
        if (id == null && record.getServiceCost() != null && record.getServiceCost() > 0) {
            Expense expense = new Expense();
            expense.setVehicle(vehicle);
            expense.setBranch(vehicle.getBranch());
            expense.setCategory(ExpenseCategory.MAINTENANCE);
            expense.setAmount(record.getServiceCost());
            expense.setDate(record.getServiceDate() == null ? LocalDate.now(clock) : record.getServiceDate());
            expense.setDescription("Maintenance " + record.getServiceType() + " @ " + vehicle.getRegistrationNumber());
            expense.setStatus(ExpenseStatus.PENDING);
            repos.expenses.save(expense);
            notifyFinance("Expense submitted for approval.", expense.getDescription());
        }
        access.audit(repos, id == null ? "Scheduled maintenance" : "Updated maintenance", "Maintenance", record.getId());
        return maintenanceMap(record);
    }

    public PageResponse<Map<String, Object>> fuel(String q, Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER, Role.FINANCE_OFFICER);
        Long driverId = access.is(Role.DRIVER) ? currentDriver().getId() : null;
        return pageOf(repos.fuel.search(access.scopedBranchId(branchId), access.organizationScopeId(), driverId, blankToNull(q),
                PageRequest.of(page, size, Sort.by("id").descending())), this::fuelMap);
    }

    public Map<String, Object> saveFuel(Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER);
        Vehicle vehicle = loadVehicle(longVal(body.get("vehicleId")));
        access.assertBranch(vehicle.getBranch());
        Double quantity = doubleVal(body.get("quantity"));
        Double price = doubleVal(body.get("pricePerLiter"));
        if (quantity == null || quantity <= 0) {
            throw new ApiException(400, "Fuel quantity must be greater than 0.");
        }
        if (price == null || price <= 0) {
            throw new ApiException(400, "Price per liter must be greater than 0.");
        }
        FuelRecord record = new FuelRecord();
        record.setVehicle(vehicle);
        if (body.get("driverId") != null) {
            record.setDriver(loadDriver(longVal(body.get("driverId"))));
        } else if (access.is(Role.DRIVER)) {
            record.setDriver(currentDriver());
        }
        record.setDate(date(body.get("date")) == null ? LocalDate.now(clock) : date(body.get("date")));
        record.setOdometer(intVal(body.get("odometer")));
        record.setFuelType(str(body, "fuelType") == null ? vehicle.getFuelType() : str(body, "fuelType"));
        record.setQuantity(quantity);
        record.setPricePerLiter(price);
        record.setTotalCost(quantity * price);
        record.setFuelStation(str(body, "fuelStation"));
        repos.fuel.save(record);
        Expense expense = new Expense();
        expense.setVehicle(vehicle);
        expense.setDriver(record.getDriver());
        expense.setBranch(vehicle.getBranch());
        expense.setCategory(ExpenseCategory.FUEL);
        expense.setAmount(record.getTotalCost());
        expense.setDate(record.getDate());
        expense.setDescription("Fuel " + quantity + " L @ " + vehicle.getRegistrationNumber());
        expense.setStatus(ExpenseStatus.PENDING);
        repos.expenses.save(expense);
        notifyFinance("Expense submitted for approval.", expense.getDescription());
        access.audit(repos, "Submitted fuel record", "Fuel", record.getId());
        Map<String, Object> result = fuelMap(record);
        result.put("efficiencyKmPerLitre", fuelEfficiency(vehicle.getId()));
        return result;
    }

    public PageResponse<Map<String, Object>> expenses(String q, String status, Long branchId, Long vehicleId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FINANCE_OFFICER, Role.DRIVER);
        Long scoped = access.scopedBranchId(branchId);
        var pageable = PageRequest.of(page, size, Sort.by("id").descending());
        var result = access.is(Role.DRIVER)
                ? repos.expenses.searchByDriver(currentDriver().getId(), scoped, access.organizationScopeId(),
                        parseEnum(ExpenseStatus.class, status),
                        vehicleId, blankToNull(q), pageable)
                : repos.expenses.search(scoped, access.organizationScopeId(), parseEnum(ExpenseStatus.class, status),
                        vehicleId, blankToNull(q), pageable);
        return pageOf(result, this::expenseMap);
    }

    public Map<String, Object> saveExpense(Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FINANCE_OFFICER, Role.DRIVER);
        Expense expense = new Expense();
        if (body.get("vehicleId") != null) {
            expense.setVehicle(loadVehicle(longVal(body.get("vehicleId"))));
            expense.setBranch(expense.getVehicle().getBranch());
        }
        if (body.get("tripId") != null) {
            expense.setTrip(loadTrip(longVal(body.get("tripId"))));
            expense.setBranch(expense.getTrip().getBranch());
        }
        if (body.get("driverId") != null) {
            expense.setDriver(loadDriver(longVal(body.get("driverId"))));
        }
        if (access.is(Role.DRIVER)) {
            Driver current = currentDriver();
            if (expense.getDriver() != null && !current.getId().equals(expense.getDriver().getId())) {
                throw new ApiException(403, "You can only submit your own expenses.");
            }
            expense.setDriver(current);
        }
        if (expense.getBranch() == null && body.get("branchId") != null) {
            expense.setBranch(repos.branches.findById(longVal(body.get("branchId"))).orElseThrow());
        }
        if (expense.getBranch() == null) {
            throw new ApiException(400, "Branch is required.");
        }
        access.assertBranch(expense.getBranch());
        expense.setCategory(ExpenseCategory.valueOf(required(body, "category", "Category is required.")));
        Double amount = doubleVal(body.get("amount"));
        if (amount == null || amount <= 0) {
            throw new ApiException(400, "Amount must be greater than 0.");
        }
        expense.setAmount(amount);
        expense.setDate(date(body.get("date")) == null ? LocalDate.now(clock) : date(body.get("date")));
        expense.setDescription(str(body, "description"));
        expense.setStatus(ExpenseStatus.PENDING);
        repos.expenses.save(expense);
        notifyFinance("Expense submitted for approval.", expense.getDescription());
        access.audit(repos, "Created expense", "Expense", expense.getId());
        return expenseMap(expense);
    }

    public Map<String, Object> reviewExpense(Long id, boolean approve) {
        access.require(Role.FINANCE_OFFICER, Role.SUPER_ADMIN);
        Expense expense = repos.expenses.findById(id).orElseThrow(() -> new ApiException(404, "Expense not found."));
        access.assertBranch(expense.getBranch());
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new ApiException(409, "This expense has already been reviewed.");
        }
        expense.setStatus(approve ? ExpenseStatus.APPROVED : ExpenseStatus.REJECTED);
        repos.expenses.save(expense);
        access.audit(repos, approve ? "Approved expense" : "Rejected expense", "Expense", id);
        return expenseMap(expense);
    }

    public PageResponse<Map<String, Object>> incidents(String q, String severity, Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER, Role.FINANCE_OFFICER);
        Long driverId = access.is(Role.DRIVER) ? currentDriver().getId() : null;
        return pageOf(repos.incidents.search(access.scopedBranchId(branchId), access.organizationScopeId(),
                parseEnum(IncidentSeverity.class, severity),
                driverId, blankToNull(q), PageRequest.of(page, size, Sort.by("id").descending())), this::incidentMap);
    }

    public Map<String, Object> saveIncident(Map<String, Object> body) throws IOException {
        return saveIncident(body, null);
    }

    public Map<String, Object> saveIncident(Map<String, Object> body, MultipartFile photo) throws IOException {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER);
        if (photo != null && !photo.isEmpty()) {
            if (photo.getSize() > 5 * 1024 * 1024) {
                throw new ApiException(400, "Incident photo must be 5 MB or smaller.");
            }
            if (photo.getContentType() == null || !photo.getContentType().startsWith("image/")) {
                throw new ApiException(400, "Incident photo must be an image.");
            }
        }
        Incident incident = new Incident();
        if (body.get("vehicleId") != null) {
            incident.setVehicle(loadVehicle(longVal(body.get("vehicleId"))));
        }
        if (access.is(Role.DRIVER)) {
            incident.setDriver(currentDriver());
            incident.setBranch(currentDriver().getBranch());
        } else if (body.get("driverId") != null) {
            incident.setDriver(loadDriver(longVal(body.get("driverId"))));
            incident.setBranch(incident.getDriver().getBranch());
        }
        if (body.get("tripId") != null) {
            incident.setTrip(loadTrip(longVal(body.get("tripId"))));
            incident.setBranch(incident.getTrip().getBranch());
        }
        if (incident.getBranch() == null) {
            throw new ApiException(400, "Branch could not be determined.");
        }
        access.assertBranch(incident.getBranch());
        incident.setOccurredAt(dateTime(body.get("occurredAt")) == null ? LocalDateTime.now() : dateTime(body.get("occurredAt")));
        incident.setLocation(str(body, "location"));
        incident.setIncidentType(required(body, "incidentType", "Incident type is required."));
        incident.setSeverity(IncidentSeverity.valueOf(required(body, "severity", "Severity is required.")));
        incident.setDescription(required(body, "description", "Description is required."));
        incident.setStatus(IncidentStatus.REPORTED);
        if (photo != null && !photo.isEmpty()) {
            Files.createDirectories(uploadDir);
            String filename = photo.getOriginalFilename();
            String extension = filename != null && filename.lastIndexOf('.') >= 0
                    ? filename.substring(filename.lastIndexOf('.')) : "";
            incident.setEvidencePath(UUID.randomUUID() + extension);
            Files.copy(photo.getInputStream(), uploadDir.resolve(incident.getEvidencePath()));
        }
        repos.incidents.save(incident);
        notifyManagersFromBranch(incident.getBranch(), "INCIDENT", "Incident reported.", incident.getDescription());
        access.audit(repos, "Reported incident", "Incident", incident.getId());
        return incidentMap(incident);
    }

    public Path incidentPhoto(Long id) {
        Incident incident = repos.incidents.findById(id).orElseThrow(() -> new ApiException(404, "Incident not found."));
        access.assertBranch(incident.getBranch());
        if (access.is(Role.DRIVER)
                && (incident.getDriver() == null || !incident.getDriver().getId().equals(currentDriver().getId()))) {
            throw new ApiException(403, "You can only access your own incident photos.");
        }
        if (incident.getEvidencePath() == null) {
            throw new ApiException(404, "No photo is attached to this incident.");
        }
        return uploadDir.resolve(incident.getEvidencePath());
    }

    public Map<String, Object> updateIncident(Long id, Map<String, Object> body) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER);
        Incident incident = repos.incidents.findById(id).orElseThrow(() -> new ApiException(404, "Incident not found."));
        access.assertBranch(incident.getBranch());
        if (body.get("status") != null) {
            incident.setStatus(IncidentStatus.valueOf(String.valueOf(body.get("status"))));
        }
        if (body.containsKey("investigationNotes")) {
            incident.setInvestigationNotes(str(body, "investigationNotes"));
        }
        if (body.containsKey("assignedInvestigator")) {
            incident.setAssignedInvestigator(str(body, "assignedInvestigator"));
        }
        if (body.containsKey("resolutionNotes")) {
            incident.setResolutionNotes(str(body, "resolutionNotes"));
        }
        repos.incidents.save(incident);
        access.audit(repos, "Updated incident", "Incident", id);
        return incidentMap(incident);
    }

    public PageResponse<Map<String, Object>> documents(Long branchId, int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.FINANCE_OFFICER, Role.DRIVER);
        Long scoped = access.scopedBranchId(branchId);
        if (access.is(Role.DRIVER)) {
            Driver driver = currentDriver();
            List<FleetDocument> own = new ArrayList<>(repos.documents.findByOwnerTypeAndOwnerId(DocumentOwnerType.DRIVER, driver.getId()));
            if (driver.getAssignedVehicleId() != null) {
                own.addAll(repos.documents.findByOwnerTypeAndOwnerId(DocumentOwnerType.VEHICLE, driver.getAssignedVehicleId()));
            }
            int from = Math.min(page * size, own.size());
            int to = Math.min(from + size, own.size());
            return new PageResponse<>(own.subList(from, to).stream().map(this::documentMap).toList(),
                    page, size, own.size(), (int) Math.ceil((double) own.size() / size));
        }
        return pageOf(repos.documents.searchScoped(scoped, access.organizationScopeId(),
                PageRequest.of(page, size, Sort.by("id").descending())), this::documentMap);
    }

    public Map<String, Object> saveDocument(DocumentOwnerType ownerType, Long ownerId, DocumentKind kind,
                                            LocalDate expiry, MultipartFile file) throws IOException {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.DRIVER);
        Long branchId;
        if (ownerType == DocumentOwnerType.VEHICLE) {
            Vehicle vehicle = loadVehicle(ownerId);
            branchId = vehicle.getBranch().getId();
        } else {
            Driver driver = loadDriver(ownerId);
            branchId = driver.getBranch().getId();
        }
        if (access.is(Role.DRIVER)) {
            Driver driver = currentDriver();
            boolean ownDriverDoc = ownerType == DocumentOwnerType.DRIVER && driver.getId().equals(ownerId);
            boolean ownVehicleDoc = ownerType == DocumentOwnerType.VEHICLE
                    && driver.getAssignedVehicleId() != null && driver.getAssignedVehicleId().equals(ownerId);
            if (!ownDriverDoc && !ownVehicleDoc) {
                throw new ApiException(403, "You can only upload your own driver or assigned vehicle documents.");
            }
        }
        Files.createDirectories(uploadDir);
        String stored = UUID.randomUUID() + "_" + (file == null ? "meta.txt" : file.getOriginalFilename());
        if (file != null && !file.isEmpty()) {
            Files.write(uploadDir.resolve(stored), file.getBytes());
        }
        FleetDocument doc = new FleetDocument();
        doc.setOwnerType(ownerType);
        doc.setOwnerId(ownerId);
        doc.setBranchId(branchId);
        doc.setKind(kind);
        doc.setFileName(file == null ? kind.name() : file.getOriginalFilename());
        doc.setStoredPath(stored);
        doc.setExpiryDate(expiry);
        repos.documents.save(doc);
        access.audit(repos, "Uploaded document", "Document", doc.getId());
        return documentMap(doc);
    }

    public Path documentFile(Long id) {
        FleetDocument doc = repos.documents.findById(id).orElseThrow(() -> new ApiException(404, "Document not found."));
        access.assertBranch(doc.getBranch());
        if (access.is(Role.DRIVER)) {
            Driver driver = currentDriver();
            boolean ownDriverDoc = doc.getOwnerType() == DocumentOwnerType.DRIVER
                    && driver.getId().equals(doc.getOwnerId());
            boolean ownVehicleDoc = doc.getOwnerType() == DocumentOwnerType.VEHICLE
                    && driver.getAssignedVehicleId() != null && driver.getAssignedVehicleId().equals(doc.getOwnerId());
            if (!ownDriverDoc && !ownVehicleDoc) {
                throw new ApiException(403, "You can only access your own documents.");
            }
        }
        return uploadDir.resolve(doc.getStoredPath());
    }

    public List<Map<String, Object>> notifications() {
        return repos.notifications.findByUserIdOrderByCreatedAtDesc(access.user().getId()).stream().map(n -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", n.getId());
            row.put("title", n.getTitle());
            row.put("message", n.getMessage());
            row.put("type", n.getType());
            row.put("read", n.isReadFlag());
            row.put("createdAt", n.getCreatedAt());
            return row;
        }).toList();
    }

    public void markNotificationRead(Long id) {
        AppNotification n = repos.notifications.findById(id).orElseThrow();
        if (!java.util.Objects.equals(n.getUserId(), access.user().getId())) {
            throw new ApiException(403, "You can only update your own notifications.");
        }
        n.setReadFlag(true);
        repos.notifications.save(n);
    }

    public PageResponse<Map<String, Object>> audits(int page, int size) {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER, Role.BRANCH_MANAGER, Role.FINANCE_OFFICER);
        var logs = access.is(Role.SUPER_ADMIN)
                ? repos.audits.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                : repos.audits.findByActorOrderByCreatedAtDesc(access.user().getUsername(), PageRequest.of(page, size));
        return pageOf(logs, log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("actor", log.getActor());
            row.put("role", log.getRole());
            row.put("action", log.getAction());
            row.put("entityType", log.getEntityType());
            row.put("entityId", log.getEntityId());
            row.put("createdAt", log.getCreatedAt());
            return row;
        });
    }

    public Map<String, Object> dashboard() {
        AppUser user = access.user();
        Long branchId = access.scopedBranchId(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", user.getRole().name());
        data.put("user", userMap(user));
        if (access.is(Role.DRIVER)) {
            Driver me = currentDriver();
            data.put("myTrips", repos.trips.findByDriver_IdOrderByStartDateDesc(me.getId()).stream().map(this::tripMap).toList());
            data.put("myVehicleId", me.getAssignedVehicleId());
            data.put("myDriverId", me.getId());
            data.put("driverProfile", driverMap(me));
            return data;
        }
        Long organizationId = access.organizationScopeId();
        java.util.function.Predicate<Branch> visibleBranch = branch -> branchId != null
                ? branch.getId().equals(branchId)
                : organizationId == null || (branch.getOrganization() != null
                        && organizationId.equals(branch.getOrganization().getId()));
        List<Vehicle> vehicles = repos.vehicles.findAll().stream().filter(v -> visibleBranch.test(v.getBranch())).toList();
        List<Driver> drivers = repos.drivers.findAll().stream().filter(d -> visibleBranch.test(d.getBranch())).toList();
        Map<String, Long> vehicleUtil = new LinkedHashMap<>();
        for (VehicleStatus status : VehicleStatus.values()) {
            vehicleUtil.put(status.name(), vehicles.stream().filter(v -> v.getStatus() == status).count());
        }
        data.put("vehicleUtilization", vehicleUtil);
        data.put("totalVehicles", vehicles.size());
        data.put("availableVehicles", vehicleUtil.get(VehicleStatus.AVAILABLE.name()));
        data.put("onTripVehicles", vehicleUtil.get(VehicleStatus.ON_TRIP.name()));
        data.put("maintenanceVehicles", vehicleUtil.get(VehicleStatus.MAINTENANCE.name()));
        data.put("totalDrivers", drivers.size());
        List<Trip> visibleTrips = repos.trips.findAll().stream().filter(t -> visibleBranch.test(t.getBranch())).toList();
        data.put("totalTrips", visibleTrips.size());
        data.put("activeTrips", visibleTrips.stream().filter(t -> t.getStatus() == TripStatus.STARTED || t.getStatus() == TripStatus.DELAYED).count());
        data.put("organizations", access.is(Role.SUPER_ADMIN) ? repos.organizations.count() : 0);
        data.put("branches", repos.branches.findAll().stream().filter(visibleBranch).count());
        data.put("totalUsers", repos.users.searchScoped(organizationId, branchId, null, PageRequest.of(0, 1)).getTotalElements());
        if (access.is(Role.SUPER_ADMIN, Role.FINANCE_OFFICER)) {
            List<Expense> expenses = repos.expenses.findAll().stream()
                    .filter(e -> e.getBranch() != null && visibleBranch.test(e.getBranch())).toList();
            Map<String, Object> month = monthlyFinance.summarize(monthlyFinance.currentMonth(),
                    organizationId, branchId);
            data.put("finance", month);
            data.put("fuelCost", month.get("fuelExpenses"));
            data.put("maintenanceCost", month.get("maintenanceExpenses"));
            data.put("otherExpenses", month.get("otherExpenses"));
            data.put("totalRevenue", month.get("totalRevenue"));
            data.put("netProfit", month.get("netProfit"));
            data.put("profitAvailable", month.get("profitAvailable"));
            data.put("revenueComplete", month.get("revenueComplete"));
            data.put("completedTripsMonth", month.get("completedTrips"));
            LocalDate monthStart = LocalDate.parse(String.valueOf(month.get("startInclusive")));
            LocalDate monthEnd = LocalDate.parse(String.valueOf(month.get("endExclusive")));
            data.put("tripCost", expenses.stream()
                    .filter(e -> e.getTrip() != null && e.getStatus() == ExpenseStatus.APPROVED && e.getDate() != null)
                    .filter(e -> !e.getDate().isBefore(monthStart) && e.getDate().isBefore(monthEnd))
                    .mapToDouble(e -> nz(e.getAmount())).sum());
            data.put("totalExpenses", month.get("totalExpenses"));
            data.put("costPerKm", month.get("costPerKm"));
            data.put("monthlyExpenses", monthlyExpenses(expenses.stream()
                    .filter(e -> e.getStatus() == ExpenseStatus.APPROVED).toList()));
            data.put("maintenanceByVehicle", repos.maintenance.findAll().stream()
                    .filter(m -> visibleBranch.test(m.getVehicle().getBranch()))
                    .collect(java.util.stream.Collectors.groupingBy(m -> m.getVehicle().getRegistrationNumber(),
                            java.util.stream.Collectors.summingDouble(m -> nz(m.getServiceCost())))));
            data.put("pendingExpenses", repos.expenses.search(branchId, organizationId,
                    ExpenseStatus.PENDING, null, null, PageRequest.of(0, 1)).getTotalElements());
        }
        data.put("driverActivity", drivers.stream().map(d -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", d.getName());
            row.put("completed", repos.trips.findByDriver_IdOrderByStartDateDesc(d.getId()).stream()
                    .filter(t -> t.getStatus() == TripStatus.COMPLETED).count());
            row.put("delayed", repos.trips.findByDriver_IdOrderByStartDateDesc(d.getId()).stream()
                    .filter(t -> t.getStatus() == TripStatus.DELAYED).count());
            row.put("incidents", repos.incidents.findByDriver_IdOrderByOccurredAtDesc(d.getId()).size());
            return row;
        }).toList());
        data.put("upcomingMaintenance", repos.maintenance.search(branchId, access.organizationScopeId(), null,
                MaintenanceStatus.SCHEDULED, null, PageRequest.of(0, 8)).getContent()
                .stream().map(this::maintenanceMap).toList());
        data.put("overdueMaintenance", repos.maintenance.search(branchId, access.organizationScopeId(), null,
                MaintenanceStatus.OVERDUE, null, PageRequest.of(0, 8)).getContent()
                .stream().map(this::maintenanceMap).toList());
        data.put("expiringDocuments", repos.documents.findAll().stream()
                .filter(d -> d.getExpiryDate() != null && !d.getExpiryDate().isAfter(LocalDate.now(clock).plusDays(30)))
                .filter(d -> d.getBranch() != null && visibleBranch.test(d.getBranch()))
                .map(this::documentMap).toList());
        data.put("pendingAssignments", repos.trips.search(branchId, access.organizationScopeId(),
                TripStatus.PLANNED, null, null, PageRequest.of(0, 20)).getTotalElements());
        return data;
    }

    public String exportCsv(String type, Long branchId) {
        Long scoped = access.scopedBranchId(branchId);
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case "vehicles" -> {
                sb.append("id,registration,type,model,branch,status,mileage\n");
                vehicles("", null, scoped, 0, 500).content().forEach(v -> sb.append(v.get("id")).append(',')
                        .append(v.get("registrationNumber")).append(',').append(v.get("vehicleType")).append(',')
                        .append(v.get("model")).append(',').append(v.get("branchName")).append(',')
                        .append(v.get("status")).append(',').append(v.get("currentMileage")).append('\n'));
            }
            case "trips" -> {
                sb.append("id,vehicle,driver,status,start,end,distance\n");
                trips("", null, scoped, 0, 500).content().forEach(t -> sb.append(t.get("id")).append(',')
                        .append(t.get("vehicleReg")).append(',').append(t.get("driverName")).append(',')
                        .append(t.get("status")).append(',').append(t.get("startDate")).append(',')
                        .append(t.get("expectedEndDate")).append(',').append(t.get("distanceKm")).append('\n'));
            }
            case "expenses" -> {
                sb.append("id,category,amount,status,date,branch\n");
                expenses("", null, scoped, null, 0, 500).content().forEach(e -> sb.append(e.get("id")).append(',')
                        .append(e.get("category")).append(',').append(e.get("amount")).append(',')
                        .append(e.get("status")).append(',').append(e.get("date")).append(',')
                        .append(e.get("branchName")).append('\n'));
            }
            case "maintenance" -> {
                sb.append("id,vehicle,type,status,cost,date\n");
                maintenance("", null, scoped, 0, 500).content().forEach(m -> sb.append(m.get("id")).append(',')
                        .append(m.get("registrationNumber")).append(',').append(m.get("serviceType")).append(',')
                        .append(m.get("status")).append(',').append(m.get("serviceCost")).append(',')
                        .append(m.get("serviceDate")).append('\n'));
            }
            default -> throw new ApiException(400, "Unknown export type.");
        }
        return sb.toString();
    }

    public void refreshAlerts() {
        access.require(Role.SUPER_ADMIN, Role.FLEET_MANAGER);
        refreshAlertsInternal();
    }

    public void refreshAlertsInternal() {
        LocalDate soon = LocalDate.now(clock).plusDays(14);
        for (Vehicle vehicle : repos.vehicles.findAll()) {
            if (vehicle.getInsuranceExpiry() != null && !vehicle.getInsuranceExpiry().isAfter(soon)) {
                notifyManagersFromBranch(vehicle.getBranch(), "DOCUMENT",
                        "Vehicle insurance expires soon.",
                        vehicle.getRegistrationNumber() + " insurance expires " + vehicle.getInsuranceExpiry());
            }
        }
        for (Driver driver : repos.drivers.findAll()) {
            if (driver.getLicenseExpiry() != null && !driver.getLicenseExpiry().isAfter(soon)) {
                notifyManagersFromBranch(driver.getBranch(), "DOCUMENT",
                        "Driver license expires soon.",
                        driver.getName() + " license expires " + driver.getLicenseExpiry());
            }
        }
        for (MaintenanceRecord record : repos.maintenance.findAll()) {
            if (record.getStatus() == MaintenanceStatus.SCHEDULED && record.getNextServiceDate() != null
                    && !record.getNextServiceDate().isAfter(LocalDate.now().plusDays(7))) {
                notifyManagersFromBranch(record.getVehicle().getBranch(), "MAINTENANCE",
                        "Vehicle requires maintenance.",
                        record.getVehicle().getRegistrationNumber() + " requires maintenance.");
            }
        }
    }

    private void changeTripStatus(Trip trip, TripStatus next, String remarks) {
        TripStatus current = trip.getStatus();
        if (current == next) {
            return;
        }
        boolean allowed = switch (current) {
            case PLANNED -> next == TripStatus.ASSIGNED || next == TripStatus.CANCELLED;
            case ASSIGNED -> next == TripStatus.STARTED || next == TripStatus.CANCELLED;
            case STARTED -> next == TripStatus.DELAYED || next == TripStatus.COMPLETED;
            case DELAYED -> next == TripStatus.COMPLETED;
            default -> false;
        };
        if (!allowed) {
            throw new ApiException(400, "Cannot change trip from " + current + " to " + next + ".");
        }
        trip.setStatus(next);
        repos.trips.save(trip);
        recordStatus(trip, current, next, remarks);
    }

    private void recordStatus(Trip trip, TripStatus oldStatus, TripStatus newStatus, String remarks) {
        TripStatusHistory history = new TripStatusHistory();
        history.setTrip(trip);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(access.user().getUsername());
        history.setRemarks(remarks);
        repos.tripHistory.save(history);
    }

    private Vehicle loadVehicle(Long id) {
        Vehicle vehicle = repos.vehicles.findById(id).orElseThrow(() -> new ApiException(404, "Vehicle not found."));
        access.assertBranch(vehicle.getBranch());
        if (access.is(Role.DRIVER) && !java.util.Objects.equals(currentDriver().getAssignedVehicleId(), id)
                && !repos.trips.existsByDriver_IdAndVehicle_Id(currentDriver().getId(), id)) {
            throw new ApiException(403, "You can only access your assigned vehicle.");
        }
        return vehicle;
    }

    private Driver loadDriver(Long id) {
        Driver driver = repos.drivers.findById(id).orElseThrow(() -> new ApiException(404, "Driver not found."));
        access.assertBranch(driver.getBranch());
        if (access.is(Role.DRIVER) && !java.util.Objects.equals(currentDriver().getId(), id)) {
            throw new ApiException(403, "You can only access your own driver profile.");
        }
        return driver;
    }

    private Trip loadTrip(Long id) {
        Trip trip = repos.trips.findById(id).orElseThrow(() -> new ApiException(404, "Trip not found."));
        access.assertBranch(trip.getBranch());
        if (access.is(Role.DRIVER) && (trip.getDriver() == null
                || !trip.getDriver().getId().equals(currentDriver().getId()))) {
            throw new ApiException(403, "You can only access your own trips.");
        }
        return trip;
    }

    private Driver currentDriver() {
        return repos.drivers.findByUserId(access.user().getId())
                .orElseThrow(() -> new ApiException(400, "No driver profile is linked to this user."));
    }

    private Double fuelEfficiency(Long vehicleId) {
        List<FuelRecord> rows = repos.fuel.findByVehicle_IdOrderByDateAsc(vehicleId);
        if (rows.size() < 2) {
            return null;
        }
        FuelRecord first = rows.get(rows.size() - 2);
        FuelRecord last = rows.get(rows.size() - 1);
        if (first.getOdometer() == null || last.getOdometer() == null || last.getQuantity() == null || last.getQuantity() == 0) {
            return null;
        }
        return (last.getOdometer() - first.getOdometer()) / last.getQuantity();
    }

    private void notifyUser(Long userId, Long branchId, String type, String title, String message) {
        if (userId == null) {
            return;
        }
        AppNotification n = new AppNotification();
        n.setUserId(userId);
        n.setBranchId(branchId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        repos.notifications.save(n);
    }

    private void notifyManagers(Trip trip, String type, String title, String message) {
        notifyManagersFromBranch(trip.getBranch(), type, title, message);
    }

    private void notifyManagersFromBranch(Branch branch, String type, String title, String message) {
        repos.users.findAll().stream()
                .filter(u -> u.getRole() == Role.FLEET_MANAGER || u.getRole() == Role.SUPER_ADMIN
                        || (u.getRole() == Role.BRANCH_MANAGER && u.getBranch() != null && u.getBranch().getId().equals(branch.getId())))
                .forEach(u -> notifyUser(u.getId(), branch.getId(), type, title, message));
    }

    private void notifyFinance(String title, String message) {
        repos.users.findAll().stream().filter(u -> u.getRole() == Role.FINANCE_OFFICER || u.getRole() == Role.SUPER_ADMIN)
                .forEach(u -> notifyUser(u.getId(), u.getBranch() == null ? null : u.getBranch().getId(), "EXPENSE", title, message));
    }

    private Map<String, Double> monthlyExpenses(List<Expense> expenses) {
        Map<String, Double> months = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            months.put(YearMonth.now(clock).withMonth(i).getMonth().name(), 0.0);
        }
        for (Expense expense : expenses) {
            if (expense.getDate() != null && expense.getDate().getYear() == LocalDate.now(clock).getYear()) {
                String key = expense.getDate().getMonth().name();
                months.put(key, months.getOrDefault(key, 0.0) + nz(expense.getAmount()));
            }
        }
        return months;
    }

    private double sum(List<Expense> expenses, ExpenseCategory category) {
        return expenses.stream().filter(e -> e.getCategory() == category).mapToDouble(e -> nz(e.getAmount())).sum();
    }

    private Map<String, Object> orgMap(Organization org) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", org.getId());
        map.put("name", org.getName());
        map.put("contactEmail", org.getContactEmail());
        map.put("contactPhone", org.getContactPhone());
        map.put("address", org.getAddress());
        map.put("active", org.isActive());
        map.put("createdAt", org.getCreatedAt());
        return map;
    }

    private Map<String, Object> branchMap(Branch branch) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", branch.getId());
        map.put("name", branch.getName());
        map.put("location", branch.getLocation());
        map.put("address", branch.getAddress());
        map.put("contactNumber", branch.getContactNumber());
        map.put("managerUserId", branch.getManagerUserId());
        map.put("status", branch.isActive() ? "ACTIVE" : "INACTIVE");
        map.put("organizationId", branch.getOrganization() == null ? null : branch.getOrganization().getId());
        map.put("organizationName", branch.getOrganization() == null ? null : branch.getOrganization().getName());
        map.put("createdAt", branch.getCreatedAt());
        return map;
    }

    private Map<String, Object> userMap(AppUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("phone", user.getPhone());
        map.put("role", user.getRole().name());
        map.put("active", user.isActive());
        map.put("branchId", user.getBranch() == null ? null : user.getBranch().getId());
        map.put("branchName", user.getBranch() == null ? null : user.getBranch().getName());
        map.put("organizationId", user.getOrganization() == null ? null : user.getOrganization().getId());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }

    private Map<String, Object> vehicleMap(Vehicle vehicle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vehicle.getId());
        map.put("registrationNumber", vehicle.getRegistrationNumber());
        map.put("vehicleType", vehicle.getVehicleType());
        map.put("manufacturer", vehicle.getManufacturer());
        map.put("model", vehicle.getModel());
        map.put("year", vehicle.getYear());
        map.put("fuelType", vehicle.getFuelType());
        map.put("capacity", vehicle.getCapacity());
        map.put("currentMileage", vehicle.getCurrentMileage());
        map.put("branchId", vehicle.getBranch().getId());
        map.put("branchName", vehicle.getBranch().getName());
        map.put("status", vehicle.getStatus().name());
        map.put("insuranceExpiry", vehicle.getInsuranceExpiry());
        map.put("registrationExpiry", vehicle.getRegistrationExpiry());
        map.put("pollutionExpiry", vehicle.getPollutionExpiry());
        map.put("assignedDriverId", vehicle.getAssignedDriverId());
        map.put("active", vehicle.isActive());
        map.put("documentStatus", expiryLabel(vehicle.getInsuranceExpiry()));
        return map;
    }

    private Map<String, Object> driverMap(Driver driver) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", driver.getId());
        map.put("name", driver.getName());
        map.put("phone", driver.getPhone());
        map.put("email", driver.getEmail());
        map.put("address", driver.getAddress());
        map.put("licenseNumber", driver.getLicenseNumber());
        map.put("licenseType", driver.getLicenseType());
        map.put("licenseExpiry", driver.getLicenseExpiry());
        map.put("joiningDate", driver.getJoiningDate());
        map.put("branchId", driver.getBranch().getId());
        map.put("branchName", driver.getBranch().getName());
        map.put("status", driver.getStatus().name());
        map.put("assignedVehicleId", driver.getAssignedVehicleId());
        map.put("userId", driver.getUserId());
        map.put("active", driver.isActive());
        map.put("licenseAlert", expiryLabel(driver.getLicenseExpiry()));
        return map;
    }

    private Map<String, Object> routeMap(Route route) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", route.getId());
        map.put("source", route.getSource());
        map.put("destination", route.getDestination());
        map.put("distanceKm", route.getDistanceKm());
        map.put("estimatedDurationMinutes", route.getEstimatedDurationMinutes());
        map.put("description", route.getDescription());
        map.put("branchId", route.getBranch().getId());
        map.put("branchName", route.getBranch().getName());
        return map;
    }

    private Map<String, Object> tripMap(Trip trip) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", trip.getId());
        map.put("vehicleId", trip.getVehicle() == null ? null : trip.getVehicle().getId());
        map.put("vehicleReg", trip.getVehicle() == null ? null : trip.getVehicle().getRegistrationNumber());
        map.put("driverId", trip.getDriver() == null ? null : trip.getDriver().getId());
        map.put("driverName", trip.getDriver() == null ? null : trip.getDriver().getName());
        map.put("routeId", trip.getRoute().getId());
        map.put("routeSource", trip.getRoute().getSource());
        map.put("routeDestination", trip.getRoute().getDestination());
        map.put("routeLabel", trip.getRoute().getSource() + " → " + trip.getRoute().getDestination());
        map.put("branchId", trip.getBranch().getId());
        map.put("branchName", trip.getBranch().getName());
        map.put("startDate", trip.getStartDate());
        map.put("expectedEndDate", trip.getExpectedEndDate());
        map.put("actualEndDate", trip.getActualEndDate());
        map.put("purpose", trip.getPurpose());
        map.put("distanceKm", trip.getDistanceKm());
        if (access.is(Role.SUPER_ADMIN, Role.FINANCE_OFFICER)) {
            map.put("revenueAmount", trip.getRevenueAmount());
        }
        map.put("notes", trip.getNotes());
        map.put("status", trip.getStatus().name());
        return map;
    }

    private Map<String, Object> maintenanceMap(MaintenanceRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("vehicleId", record.getVehicle().getId());
        map.put("registrationNumber", record.getVehicle().getRegistrationNumber());
        map.put("serviceType", record.getServiceType());
        map.put("serviceDate", record.getServiceDate());
        map.put("currentMileage", record.getCurrentMileage());
        if (access.is(Role.SUPER_ADMIN, Role.FINANCE_OFFICER)) {
            map.put("serviceCost", record.getServiceCost());
        }
        map.put("nextServiceDate", record.getNextServiceDate());
        map.put("nextServiceMileage", record.getNextServiceMileage());
        map.put("description", record.getDescription());
        map.put("status", record.getStatus().name());
        return map;
    }

    private Map<String, Object> fuelMap(FuelRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("vehicleId", record.getVehicle().getId());
        map.put("registrationNumber", record.getVehicle().getRegistrationNumber());
        map.put("driverId", record.getDriver() == null ? null : record.getDriver().getId());
        map.put("driverName", record.getDriver() == null ? null : record.getDriver().getName());
        map.put("date", record.getDate());
        map.put("odometer", record.getOdometer());
        map.put("fuelType", record.getFuelType());
        map.put("quantity", record.getQuantity());
        if (access.is(Role.SUPER_ADMIN, Role.FINANCE_OFFICER, Role.DRIVER)) {
            map.put("pricePerLiter", record.getPricePerLiter());
            map.put("totalCost", record.getTotalCost());
        }
        map.put("fuelStation", record.getFuelStation());
        map.put("efficiencyKmPerLitre", fuelEfficiency(record.getVehicle().getId()));
        return map;
    }

    private Map<String, Object> expenseMap(Expense expense) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", expense.getId());
        map.put("vehicleId", expense.getVehicle() == null ? null : expense.getVehicle().getId());
        map.put("vehicleReg", expense.getVehicle() == null ? null : expense.getVehicle().getRegistrationNumber());
        map.put("tripId", expense.getTrip() == null ? null : expense.getTrip().getId());
        map.put("driverId", expense.getDriver() == null ? null : expense.getDriver().getId());
        map.put("driverName", expense.getDriver() == null ? null : expense.getDriver().getName());
        map.put("branchId", expense.getBranch().getId());
        map.put("branchName", expense.getBranch().getName());
        map.put("category", expense.getCategory());
        map.put("amount", expense.getAmount());
        map.put("date", expense.getDate());
        map.put("description", expense.getDescription());
        map.put("status", expense.getStatus().name());
        return map;
    }

    private Map<String, Object> incidentMap(Incident incident) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", incident.getId());
        map.put("vehicleId", incident.getVehicle() == null ? null : incident.getVehicle().getId());
        map.put("vehicleReg", incident.getVehicle() == null ? null : incident.getVehicle().getRegistrationNumber());
        map.put("driverId", incident.getDriver() == null ? null : incident.getDriver().getId());
        map.put("driverName", incident.getDriver() == null ? null : incident.getDriver().getName());
        map.put("tripId", incident.getTrip() == null ? null : incident.getTrip().getId());
        map.put("branchName", incident.getBranch().getName());
        map.put("occurredAt", incident.getOccurredAt());
        map.put("location", incident.getLocation());
        map.put("incidentType", incident.getIncidentType());
        map.put("severity", incident.getSeverity());
        map.put("description", incident.getDescription());
        map.put("hasPhoto", incident.getEvidencePath() != null);
        map.put("investigationNotes", incident.getInvestigationNotes());
        map.put("assignedInvestigator", incident.getAssignedInvestigator());
        map.put("resolutionNotes", incident.getResolutionNotes());
        map.put("status", incident.getStatus().name());
        return map;
    }

    private Map<String, Object> documentMap(FleetDocument doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doc.getId());
        map.put("ownerType", doc.getOwnerType());
        map.put("ownerId", doc.getOwnerId());
        map.put("kind", doc.getKind());
        map.put("fileName", doc.getFileName());
        map.put("expiryDate", doc.getExpiryDate());
        map.put("status", expiryLabel(doc.getExpiryDate()));
        map.put("createdAt", doc.getCreatedAt());
        return map;
    }

    private String expiryLabel(LocalDate expiry) {
        if (expiry == null) {
            return "UNKNOWN";
        }
        if (expiry.isBefore(LocalDate.now(clock))) {
            return "EXPIRED";
        }
        if (!expiry.isAfter(LocalDate.now(clock).plusDays(14))) {
            return "EXPIRING_SOON";
        }
        return "VALID";
    }

    private <T> PageResponse<Map<String, Object>> pageOf(Page<T> page, java.util.function.Function<T, Map<String, Object>> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private String required(Map<String, Object> body, String key, String message) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ApiException(400, message);
        }
        return String.valueOf(value).trim();
    }

    private String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Long longVal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer intVal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Double doubleVal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private LocalDate date(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private LocalDateTime dateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() == 16) {
            return LocalDateTime.parse(text);
        }
        if (text.length() == 10) {
            return LocalDate.parse(text).atStartOfDay();
        }
        return LocalDateTime.parse(text);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, value);
    }

    private double nz(Double value) {
        return value == null ? 0 : value;
    }
}
