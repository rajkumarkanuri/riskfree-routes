package com.riskfreeroutes.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RiskFreeRoutesApplication — The Entry Point of Our Spring Boot Backend
 *
 * WHY THIS EXISTS:
 * Every Java program needs a 'main()' method — the starting point.
 * Spring Boot is no different. This class IS that starting point.
 *
 * When you run 'mvn spring-boot:run' or press Run in IntelliJ,
 * Java calls main(), which calls SpringApplication.run().
 *
 * SpringApplication.run() then:
 * 1. Reads application.properties
 * 2. Starts the embedded Tomcat web server on port 8080
 * 3. Connects to PostgreSQL using our datasource config
 * 4. Scans ALL classes in this package for @Component, @Service,
 *    @Repository, @RestController annotations and registers them
 * 5. Sets up Spring Security (JWT filter, etc.)
 *
 * @SpringBootApplication is a "convenience" annotation that combines:
 *   @Configuration       → This class can define Spring beans
 *   @EnableAutoConfiguration → Let Spring Boot auto-configure everything
 *   @ComponentScan       → Scan this package and all sub-packages for Spring components
 */
@SpringBootApplication
public class RiskFreeRoutesApplication {

    /**
     * The main method — Java's universal entry point.
     * Spring Boot takes over from here.
     *
     * @param args Command-line arguments (we don't use any, but Spring Boot might).
     */
    public static void main(String[] args) {
        // SpringApplication.run() bootstraps the entire Spring Boot application.
        // First argument: the class annotated with @SpringBootApplication (this class).
        // Second argument: the command-line args passed to main().
        SpringApplication.run(RiskFreeRoutesApplication.class, args);

        // At this point, the server is running and ready to accept HTTP requests
        // from our Android app at: http://localhost:8080/api/...
        System.out.println("✅ Risk Free Routes Backend is running on http://localhost:8080");
        System.out.println("📍 API Base: http://localhost:8080/api");
    }
}
