package com.ninimum.api;

import com.ninimum.api.common.CustomBannerTemp;
import com.ninimum.api.constants.Constant;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan(
        basePackages = {Constant.basePackageUser}
)
@EnableScheduling
@SpringBootApplication
public class NinimumApplication extends SpringBootServletInitializer {
    public NinimumApplication() {
    }

    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(com.ninimum.api.NinimumApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(com.ninimum.api.NinimumApplication.class);
        app.setBanner(new CustomBannerTemp("NinimumApplication has started..."));
        app.run(args);
    }
}