package com.example.demo.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.security.AccessGuard;
import com.example.demo.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AccessGuard access;

    public AuthController(AuthService authService, AccessGuard access) {
        this.authService = authService;
        this.access = access;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String identifier = body.get("username") == null ? body.get("email") : body.get("username");
        if (identifier == null) {
            identifier = body.get("identifier");
        }
        return authService.login(identifier, body.get("password"));
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        return authService.register(body.get("username"), body.get("email"), body.get("password"),
                body.get("fullName"), body.get("phone"));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return authService.me(access.user());
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> body) {
        return authService.changePassword(access.user(), body.get("currentPassword"), body.get("newPassword"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Signed out. Discard the token on the client."));
    }
}
