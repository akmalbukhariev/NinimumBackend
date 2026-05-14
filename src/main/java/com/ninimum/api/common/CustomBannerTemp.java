package com.ninimum.api.common;

import org.springframework.boot.Banner;
import org.springframework.core.env.Environment;

import java.io.PrintStream;

public class CustomBannerTemp implements Banner {
    private String title;
    public CustomBannerTemp(String title){
        this.title = title;
    }
    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        out.println("====================================");
        out.println("      " + this.title + "     ");
        out.println("====================================");
    }
}
