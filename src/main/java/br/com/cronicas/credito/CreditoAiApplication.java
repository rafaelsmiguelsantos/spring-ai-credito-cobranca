package br.com.cronicas.credito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CreditoAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditoAiApplication.class, args);
    }
}
