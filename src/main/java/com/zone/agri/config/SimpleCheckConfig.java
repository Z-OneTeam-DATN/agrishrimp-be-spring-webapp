package com.zone.agri.config;

import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class SimpleCheckConfig {
    @PostConstruct
    public void init() {
        log.info(">>>>>> SPRING HAS LOADED NEW CONFIGURATION SUCCESSFULLY! <<<<<<");
    }
}
