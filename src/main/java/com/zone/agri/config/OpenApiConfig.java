package com.zone.agri.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(@Value("${open.api.title:AgriShrimp API}") String title,
                           @Value("${open.api.version:1.0.0}") String version) {
        return new OpenAPI()
                // 1. Cấu hình thông tin chung
                .info(new Info().title(title)
                        .version(version)
                        .description("Tài liệu API cho dự án AgriShrimp - Thương mại điện tử")
                        .license(new License().name("API License").url("http://domain.vn/license")))
                // 2. Cấu hình Server (để test trên Docker hay Local đều đúng link)
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Server Local"),
                        new Server().url("http://localhost:8001").description("Server Docker")
                ))
                // 3. Cấu hình Bảo mật (Nút ổ khóa để nhập JWT)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    // --- PHẦN NÀY ĐỂ CHIA NHÓM API CHO DỄ QUẢN LÝ ---

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("1. Authentication") // Tên nhóm trên Menu
                .pathsToMatch("/api/auth/**") // Chỉ hiện các API bắt đầu bằng /api/auth
                .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("2. User Management")
                .pathsToMatch("/api/users/**", "/api/roles/**")
                .build();
    }

    @Bean
    public GroupedOpenApi branchApi() {
        return GroupedOpenApi.builder()
                .group("3. Branch Management")
                .pathsToMatch("/api/branches/**")
                .build();
    }
}