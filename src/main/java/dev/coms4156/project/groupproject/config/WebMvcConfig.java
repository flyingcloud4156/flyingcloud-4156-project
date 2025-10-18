package dev.coms4156.project.groupproject.config;

import dev.coms4156.project.groupproject.utils.AccessLogInterceptor;
import dev.coms4156.project.groupproject.utils.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers authentication and access log interceptors for /api/** except auth & docs. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;
  private final AccessLogInterceptor accessLogInterceptor;

  @Autowired
  public WebMvcConfig(AuthInterceptor authInterceptor, AccessLogInterceptor accessLogInterceptor) {
    this.authInterceptor = authInterceptor;
    this.accessLogInterceptor = accessLogInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(accessLogInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns(
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**");

    registry
        .addInterceptor(authInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**");
  }
}
