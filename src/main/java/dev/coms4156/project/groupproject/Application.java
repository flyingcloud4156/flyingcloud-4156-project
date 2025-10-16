package dev.coms4156.project.groupproject;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entrypoint.
 * - Enables auto-configuration and component scanning.
 * - Scans MyBatis mappers.
 */
@SpringBootApplication
@MapperScan("dev.coms4156.project.groupproject.mapper")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
