package dev.coms4156.project.groupproject.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger configuration class. */
@Configuration
public class SwaggerConfig {

  /**
   * OpenAPI configuration.
   *
   * @return configured {@link OpenAPI} instance.
   */
  @Bean
  public OpenAPI customOpenApi() {
    final String schemeName = "X-Auth-Token";
    return new OpenAPI()
        .info(new Info().title("Ledger APIs").version("1.0.0").description("APIs for 4156 project"))
        .components(new Components().addSecuritySchemes(
            schemeName,
            new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .name("X-Auth-Token")
                .in(SecurityScheme.In.HEADER)
                .description("Put accessToken here; get it from /api/auth/login")
        ))
        .addSecurityItem(new SecurityRequirement().addList(schemeName));
  }
}
