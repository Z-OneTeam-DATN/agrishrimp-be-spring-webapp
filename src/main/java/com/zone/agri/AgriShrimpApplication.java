package com.zone.agri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
public class AgriShrimpApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgriShrimpApplication.class, args);
  }
}
