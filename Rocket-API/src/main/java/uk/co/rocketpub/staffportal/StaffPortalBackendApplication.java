package uk.co.rocketpub.staffportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StaffPortalBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(StaffPortalBackendApplication.class, args);
    }
}
