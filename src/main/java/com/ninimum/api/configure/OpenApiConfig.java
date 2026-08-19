package com.ninimum.api.configure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ninimum API")
                        .version("v1")
                )

                // Default security for normal Ninimum APIs
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("bearerAuth")
                )

                .components(
                        new Components()

                                // Admin/user JWT
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .name("bearerAuth")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )

                                // Payme callback authentication
                                .addSecuritySchemes(
                                        "paymeBasicAuth",
                                        new SecurityScheme()
                                                .name("paymeBasicAuth")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("basic")
                                )
                );
    }
}