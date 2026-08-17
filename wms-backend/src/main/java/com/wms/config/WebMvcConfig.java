package com.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // API 统一加 /api/v1；com.wms.web 用于托管前端 SPA，不加前缀
        configurer.addPathPrefix("/api/v1", c -> {
            String pkg = c.getPackageName();
            return pkg.startsWith("com.wms") && !pkg.startsWith("com.wms.web");
        });
    }
}
