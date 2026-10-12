package io.github.quizup.console;

import io.github.quizup.console.infrastructure.config.ConsoleServicesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ConsoleServicesProperties.class)
public class ConsoleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleServiceApplication.class, args);
    }
}
