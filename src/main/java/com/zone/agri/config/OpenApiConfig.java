package com.zone.agri.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AgriShrimp Enterprise API",
                version = "1.0.0",
                description = "Hệ thống quản lý chuỗi cung ứng và vận hành nuôi tôm công nghệ cao.",
                contact = @Contact(name = "Zone Agri Support", email = "support@zone-agri.com", url = "https://zone-agri.com"),
                license = @License(name = "Enterprise License", url = "https://zone-agri.com/license")
        ),
        security = {@SecurityRequirement(name = "bearerAuth")}
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(@Value("${app.server.url:http://localhost:8080}") String serverUrl) {
        return new OpenAPI()
                .servers(List.of(
                        new Server().url(serverUrl).description("Default Server"),
                        new Server().url("https://api.dev.zone-agri.com").description("Development Server"),
                        new Server().url("https://api.stg.zone-agri.com").description("Staging Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập Token JWT vào đây (không cần từ khóa Bearer)")));
    }

    // --- PHÂN NHÓM API ---

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("1. Public APIs")
                .pathsToMatch("/api/auth/**", "/api/public/**", "/api/external/**")
                .build();
    }

    @Bean
    public GroupedOpenApi coreSystemApi() {
        return GroupedOpenApi.builder()
                .group("2. Core System")
                .pathsToMatch("/api/users/**", "/api/roles/**", "/api/branches/**", "/api/files/**")
                .build();
    }

    @Bean
    public GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group("3. Business Operations")
                .pathsToMatch("/api/products/**", "/api/categories/**", "/api/suppliers/**", "/api/customers/**", "/api/attributes/**")
                .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("0. All APIs (Global View)")
                .pathsToMatch("/**")
                .build();
    }
}
