package com.example.demo.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionConfigurationValidator {
    public ProductionConfigurationValidator(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.upload-dir}") String uploadDir) {
        if (jwtSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Production JWT_SECRET must contain at least 32 bytes.");
        }
        if (!datasourceUrl.startsWith("jdbc:postgresql://") || datasourceUsername.isBlank() || datasourcePassword.isBlank()) {
            throw new IllegalStateException("Production PostgreSQL URL, username, and password are required.");
        }
        if (uploadDir.isBlank()) {
            throw new IllegalStateException("Production UPLOAD_DIR must be configured.");
        }
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> !isProductionOrigin(origin))) {
            throw new IllegalStateException("Production CORS origins must be explicit HTTPS origins without wildcards or localhost.");
        }
    }

    private boolean isProductionOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && !uri.getHost().equalsIgnoreCase("localhost")
                    && !uri.getHost().equals("127.0.0.1")
                    && uri.getRawPath().isEmpty() && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}