package com.example.rps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Rock Paper Scissors multiplayer game server.
 */
@SpringBootApplication
@EnableScheduling
public class RpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RpsApplication.class, args);
    }
}
