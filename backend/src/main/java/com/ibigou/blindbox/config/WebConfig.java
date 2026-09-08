package com.ibigou.blindbox.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册平台后台鉴权拦截器（登录接口除外）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final CustomerAuthInterceptor customerAuthInterceptor;
    private final MerchantAuthInterceptor merchantAuthInterceptor;

    @org.springframework.beans.factory.annotation.Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        // 上传文件（商家收款码）静态访问
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login.html").setViewName("forward:/h5/customer/login.html");
        registry.addViewController("/index.html").setViewName("forward:/h5/customer/index.html");
        registry.addViewController("/draw.html").setViewName("forward:/h5/customer/draw.html");
        registry.addViewController("/main.html").setViewName("forward:/h5/customer/main.html");
        registry.addViewController("/pay.html").setViewName("forward:/h5/customer/pay.html");
        registry.addViewController("/wallet.html").setViewName("forward:/h5/customer/wallet.html");
        registry.addViewController("/ibigou.html").setViewName("forward:/h5/customer/ibigou.html");
        registry.addViewController("/").setViewName("forward:/h5/customer/login.html");
        registry.addViewController("/h5").setViewName("forward:/h5/customer/login.html");
        registry.addViewController("/h5/").setViewName("forward:/h5/customer/login.html");
        registry.addViewController("/h5/customer").setViewName("forward:/h5/customer/login.html");
        registry.addViewController("/h5/customer/").setViewName("forward:/h5/customer/login.html");
        registry.addViewController("/h5/merchant").setViewName("forward:/h5/merchant/login.html");
        registry.addViewController("/h5/merchant/").setViewName("forward:/h5/merchant/login.html");
        registry.addViewController("/h5/admin").setViewName("forward:/h5/admin/login.html");
        registry.addViewController("/h5/admin/").setViewName("forward:/h5/admin/login.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
        registry.addInterceptor(customerAuthInterceptor)
                .addPathPatterns("/api/customer/**")
                .excludePathPatterns("/api/customer/auth/send-code", "/api/customer/auth/login", "/api/customer/ibigou/goods", "/api/customer/ibigou/announce-prize", "/api/customer/recent-wins/**");
        registry.addInterceptor(merchantAuthInterceptor)
                .addPathPatterns("/api/merchant/**")
                .excludePathPatterns("/api/merchant/auth/login");
    }
}
