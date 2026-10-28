package com.localserviceconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LocalServiceConnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalServiceConnectApplication.class, args);
    }
}
