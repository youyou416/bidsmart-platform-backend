package com.bidsmart.platform.config;

import com.bidsmart.platform.common.TraceFilter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Clock;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class CommonConfiguration {
    @Bean public Clock utcClock() { return Clock.systemUTC(); }

    @Bean public FilterRegistrationBean<TraceFilter> traceRegistration() {
        FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>(new TraceFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean public org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer jsonIds() {
        return (Jackson2ObjectMapperBuilder builder) -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.modulesToInstall(module);
            builder.timeZone("UTC");
        };
    }

    @Bean public WebServerFactoryCustomizer<TomcatServletWebServerFactory> sameSiteCookies() {
        return factory -> factory.addContextCustomizers(context -> {
            Rfc6265CookieProcessor processor = new Rfc6265CookieProcessor();
            processor.setSameSiteCookies("lax");
            context.setCookieProcessor(processor);
        });
    }
}
