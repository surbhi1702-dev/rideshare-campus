package com.rideshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RideShareApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideShareApplication.class, args);
    }
}
