package com.painelagentesback;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class PainelAgentesBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(PainelAgentesBackApplication.class, args);
    }

}
