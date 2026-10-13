package com.pjmbusnel.smartbikes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartbikesApplication {

    public static void main(String[] args) {
        System.out.println("Server starting...");
        SpringApplication.run(SmartbikesApplication.class, args);
    }
}
