package com.enviro.assessment.junior.gumede;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication - marks this as the app's entry point and enables component scan, autoconfiguration and config properties for everything under this package
@SpringBootApplication
public class WithdrawalSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(WithdrawalSystemApplication.class, args);
    }
}
