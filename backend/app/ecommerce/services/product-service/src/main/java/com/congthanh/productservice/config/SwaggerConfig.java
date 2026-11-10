package com.congthanh.productservice.config;

import java.util.List;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
@OpenAPIDefinition(
        info = @io.swagger.v3.oas.annotations.info.Info(
                title = "Product Service API",
                description = "Product API documentation",
                version = "1.0"
        ),
        security = @SecurityRequirement(name = "oauth2_bearer"),
        servers = {
                @io.swagger.v3.oas.annotations.servers.Server(url = "${server.servlet.context-path:/}",
                        description = "Default Server URL")
        })
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "oauth2_bearer", type = SecuritySchemeType.OAUTH2,
        flows = @OAuthFlows(
                authorizationCode = @OAuthFlow(
                        authorizationUrl = "${springdoc.oauthflow.authorization-url}",
                        tokenUrl = "${springdoc.oauthflow.token-url}",
                        scopes = {@OAuthScope(name = "openid", description = "openid")
                        })))
public class SwaggerConfig {

    @Value("${congthanhapp.openapi.dev-url}")
    private String devUrl;

    @Value("${congthanhapp.openapi.prod-url}")
    private String prodUrl;

    @Bean
    public OpenAPI myOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl(devUrl);
        devServer.setDescription("Server URL in Development environment");

        Server prodServer = new Server();
        prodServer.setUrl(prodUrl);
        prodServer.setDescription("Server URL in Production environment");

        Contact contact = new Contact();
        contact.setEmail("pdcthanh112.dev@gmail.com");
        contact.setName("CongThanh");
        contact.setUrl("https://www.pdcthanhdev.com");

        License mitLicense = new License().name("MIT License").url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("CongThanhApp API")
                .version("1.0")
                .contact(contact)
                .description("This API exposes endpoints to manage CongThanhApp.").termsOfService("https://www.pdcthanh.com/terms")
                .license(mitLicense);

        return new OpenAPI().info(info).servers(List.of(devServer, prodServer)).components(
                new Components().addSecuritySchemes(
                        "api",
                        new SecurityScheme()
                                .scheme("bearer")
                                .type(SecurityScheme.Type.HTTP)
                                .bearerFormat("jwt") //if it is your case
                                .name("api")
                ).addSecuritySchemes("JavaInUseSecurityScheme", new SecurityScheme()
                        .name("JavaInUseSecurityScheme").type(SecurityScheme.Type.HTTP).scheme("basic"))
        );
    }
}
