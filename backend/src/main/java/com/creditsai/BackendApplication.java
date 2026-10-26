package com.creditsai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the creditsAI REST API, which registers clients and their credits.
 */
@SpringBootApplication
public class BackendApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments passed to Spring
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
