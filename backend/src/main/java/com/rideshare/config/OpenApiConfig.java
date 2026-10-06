package com.rideshare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI rideShareOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideShare Campus API")
                        .version("1.0.0")
                        .description("""
                                Smart Ride Matching System for institute students.
                                Authenticate with POST /api/auth/login, then click **Authorize** \
                                and paste the returned token. All errors use the ApiError schema \
                                with a machine readable `code`."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
