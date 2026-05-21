package com.jupin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.jupin")
public class JupinApplication {
    public static void main(String[] args) {
        SpringApplication.run(JupinApplication.class, args);
    }
}
