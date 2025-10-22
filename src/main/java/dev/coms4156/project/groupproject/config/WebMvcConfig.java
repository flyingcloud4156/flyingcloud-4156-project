package dev.coms4156.project.groupproject.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.coms4156.project.groupproject.utils.AccessLogInterceptor;
import dev.coms4156.project.groupproject.utils.AuthInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof MappingJackson2HttpMessageConverter) {
        MappingJackson2HttpMessageConverter jacksonConverter =
            (MappingJackson2HttpMessageConverter) converter;
        ObjectMapper objectMapper = jacksonConverter.getObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        break;
      }
    }
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
