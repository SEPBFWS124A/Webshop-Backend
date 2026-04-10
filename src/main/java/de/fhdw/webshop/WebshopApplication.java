package de.fhdw.webshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebshopApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(WebshopApplication.class, arguments);
    }
}
