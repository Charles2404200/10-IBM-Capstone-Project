package com.ibm.consulting.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConsultingSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultingSimApplication.class, args);
    }
}
