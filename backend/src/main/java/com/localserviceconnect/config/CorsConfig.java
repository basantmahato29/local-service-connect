package com.localserviceconnect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/provider/home").setViewName("forward:/provider/home.html");
        registry.addViewController("/provider/dashboard").setViewName("forward:/provider-dashboard.html");
        registry.addViewController("/provider/profile").setViewName("forward:/provider/profile.html");
        registry.addViewController("/provider/account").setViewName("forward:/provider/profile.html");
        registry.addViewController("/admin/dashboard").setViewName("forward:/admin/dashboard.html");
        registry.addViewController("/admin/analytics").setViewName("forward:/admin/dashboard.html");
        registry.addViewController("/admin/providers").setViewName("forward:/admin/providers.html");
        registry.addViewController("/admin/bookings").setViewName("forward:/admin/bookings.html");
        registry.addViewController("/admin/customers").setViewName("forward:/admin/customers.html");
        registry.addViewController("/admin/services").setViewName("forward:/admin/services.html");
        registry.addViewController("/admin/reviews").setViewName("forward:/admin/reviews.html");
        registry.addViewController("/admin/notifications").setViewName("forward:/admin/notifications.html");
        registry.addViewController("/admin/activity-logs").setViewName("forward:/admin/activity-logs.html");
    }
}

