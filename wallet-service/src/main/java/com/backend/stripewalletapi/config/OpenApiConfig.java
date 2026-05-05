package com.backend.stripewalletapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Stripe Wallet API")
                        .description("""
                                Production-grade internal wallet system powered by Stripe.
                                
                                **Key features:**
                                - JWT Authentication
                                - Stripe Checkout top-up (webhook-verified)
                                - Wallet-to-wallet transfers
                                - Refund management
                                - Transaction history
                                - Idempotency key support
                                - Redis rate limiting (30 req/min)
                                - Full audit logging
                                
                                > ⚠️ Stripe test mode supported. Use test cards from [Stripe docs](https://stripe.com/docs/testing).
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Stripe Wallet API")
                                .url("https://github.com"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT token obtained from POST /api/v1/auth/login")));
    }
}
