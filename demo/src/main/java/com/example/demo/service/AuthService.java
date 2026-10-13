package com.example.demo.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.AppUser;
import com.example.demo.domain.Branch;
import com.example.demo.domain.Codes.Role;
import com.example.demo.domain.Driver;
import com.example.demo.repo.Repos;
import com.example.demo.security.JwtService;
import com.example.demo.web.ApiException;

@Service
public class AuthService {
    private final Repos repos;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(Repos repos, PasswordEncoder encoder, JwtService jwtService) {
        this.repos = repos;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public Map<String, Object> login(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(400, "Email/username and password are required.");
        }
        AppUser user = repos.users.findByUsernameIgnoreCase(identifier.trim())
                .or(() -> repos.users.findByEmailIgnoreCase(identifier.trim()))
                .orElseThrow(() -> new ApiException(401, "Invalid username or password."));
        if (!user.isActive()) {
            throw new ApiException(403, "This account has been deactivated.");
        }
        if (!encoder.matches(password, user.getPassword())) {
            throw new ApiException(401, "Invalid username or password.");
        }
        return authPayload(user);
    }

    @Transactional
    public Map<String, Object> register(String username, String email, String password, String fullName, String phone) {
        if (username == null || username.isBlank()) {
            throw new ApiException(400, "Username is required.");
        }
        if (email == null || email.isBlank()) {
            throw new ApiException(400, "Email is required.");
        }
        if (phone == null || phone.isBlank()) {
            throw new ApiException(400, "Phone number is required.");
        }
        if (password == null || password.length() < 6) {
            throw new ApiException(400, "Password must be at least 6 characters.");
        }
        if (repos.users.existsByUsernameIgnoreCase(username) || repos.users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(409, "Username or email is already in use.");
        }
        AppUser user = new AppUser();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setPassword(encoder.encode(password));
        user.setFullName(fullName == null || fullName.isBlank() ? username : fullName);
        user.setPhone(phone.trim());
        user.setRole(Role.DRIVER);
        repos.organizations.findAll().stream().findFirst().ifPresent(user::setOrganization);
        repos.branches.findAll().stream().findFirst().ifPresent(user::setBranch);
        repos.users.save(user);
        if (user.getBranch() != null) {
            Driver driver = new Driver();
            driver.setName(user.getFullName());
            driver.setPhone(user.getPhone());
            driver.setEmail(user.getEmail());
            driver.setLicenseNumber("TMP-" + user.getUsername().toUpperCase());
            driver.setBranch(user.getBranch());
            driver.setUserId(user.getId());
            repos.drivers.save(driver);
        }
        return authPayload(user);
    }

    public Map<String, Object> me(AppUser user) {
        return authPayload(user);
    }

    @Transactional
    public Map<String, Object> changePassword(AppUser user, String currentPassword, String newPassword) {
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPassword())) {
            throw new ApiException(400, "Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(400, "New password must be at least 8 characters.");
        }
        user.setPassword(encoder.encode(newPassword));
        repos.users.save(user);
        return Map.of("message", "Password changed successfully.");
    }

    private Map<String, Object> authPayload(AppUser user) {
        Branch branch = user.getBranch();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", jwtService.createToken(user));
        body.put("expiresInMs", jwtService.expirationMs());
        body.put("id", user.getId());
        body.put("username", user.getUsername());
        body.put("email", user.getEmail());
        body.put("fullName", user.getFullName());
        body.put("phone", user.getPhone());
        body.put("role", user.getRole().name());
        body.put("branchId", branch == null ? null : branch.getId());
        body.put("branchName", branch == null ? null : branch.getName());
        body.put("organizationId", user.getOrganization() == null ? null : user.getOrganization().getId());
        return body;
    }
}
