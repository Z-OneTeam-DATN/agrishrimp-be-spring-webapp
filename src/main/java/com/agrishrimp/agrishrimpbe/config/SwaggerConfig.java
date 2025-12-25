package com.agrishrimp.agrishrimpbe.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // 1. Info
                .info(new Info()
                        .title("AgriShrimp API Documentation")
                        .version("1.0")
                        .description("Tài liệu API cho dự án AgriShrimp (Team DATN)")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))

                // 2. Cấu hình Server
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Environment")
                ))

                // 3. Cấu hình 'Authorize'
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}