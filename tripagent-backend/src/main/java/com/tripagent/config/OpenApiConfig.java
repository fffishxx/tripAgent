package com.tripagent.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tripAgentOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("TripAgent Backend API")
                        .description("TripAgent Java 业务后端：用户、偏好、行程、收藏、打车")
                        .version("v0.2.0")
                        .contact(new Contact().name("TripAgent")))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
