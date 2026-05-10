package com.cashtrack.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.cashtrack"})
@EntityScan(basePackages = {"com.cashtrack"})
@EnableJpaRepositories(basePackages = {"com.cashtrack"})
public class CashTrackApplication {
    public static void main(String[] args) {
        SpringApplication.run(CashTrackApplication.class, args);
    }
}
