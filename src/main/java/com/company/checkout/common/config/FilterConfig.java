package com.company.checkout.common.config;

import com.company.checkout.common.filter.ApiEventLoggingFilter;
import com.company.checkout.common.filter.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers servlet filters with an explicit, documented order (never rely on @Component scanning
 * order for filters).
 *
 * <pre>
 * HIGHEST_PRECEDENCE + 1   Micrometer ServerHttpObservationFilter  -> starts span, puts traceId/spanId in MDC
 * HIGHEST_PRECEDENCE + 10  CorrelationIdFilter                     -> correlationId/clientId in MDC
 * HIGHEST_PRECEDENCE + 20  ApiEventLoggingFilter                   -> request received / response sent events
 * -100                     Spring Security (when added)            -> 401/403 are still logged by the filters above
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
public class FilterConfig {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<ApiEventLoggingFilter> apiEventLoggingFilter() {
        FilterRegistrationBean<ApiEventLoggingFilter> registration = new FilterRegistrationBean<>(new ApiEventLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
