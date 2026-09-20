package com.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API 前缀与静态资源：PDA 更新包优先读 jar 同级 {@code ./pda-update/}，其次 classpath。
 */
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 部署示例：
        //   /opt/wms/wms-backend.jar
        //   /opt/wms/pda-update/wms-pda.apk
        // 启动：cd /opt/wms && java -jar wms-backend.jar
        // download-url 仍用 /pda-update/wms-pda.apk
        registry.addResourceHandler("/pda-update/**")
                .addResourceLocations(
                        "file:./pda-update/",
                        "classpath:/static/pda-update/")
                .setCachePeriod(0);
    }
}
