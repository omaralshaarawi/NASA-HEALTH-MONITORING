package com.nasa.spacehealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpaceHealthApplication {

    public static void main(String[] args) {
        DotEnv.load();
        SpringApplication.run(SpaceHealthApplication.class, args);
    }
}
