package com.nexabank.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI descriptor (Swagger UI at {@code /swagger-ui.html}).
 *
 * <p>Documents authentication (stateless Bearer JWT), the customer/account/
 * transaction/beneficiary/transfer/fraud surfaces and the standard
 * 401/403/404/409/422/429 responses. The UI never bypasses security — every
 * protected operation requires a JWT and the backend still enforces RBAC.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI nexaBankApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NexaBank API")
                        .version("0.1.0")
                        .description("Educational enterprise-style digital banking platform: "
                                + "customers, accounts, transactions, transfers, beneficiaries "
                                + "and a deterministic fraud risk engine. "
                                + "Not affiliated with any real bank.")
                        .contact(new Contact()
                                .name("NexaBank")
                                .url("https://github.com/anomalyco/opencode")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .name(BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Stateless access token from POST /api/v1/auth/login. "
                                        + "Send as: Authorization: Bearer <accessToken>")));
    }
}
