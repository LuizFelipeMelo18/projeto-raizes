package br.com.raizes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RaizesApplication {
    public static void main(String[] args) {
        SpringApplication.run(RaizesApplication.class, args);
    }
}
