package com.zone.agri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AgriShrimpApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgriShrimpApplication.class, args);
  }

  @PostConstruct
  public void init() {
    // Thiết lập múi giờ mặc định cho toàn bộ ứng dụng là Asia/Ho_Chi_Minh
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
  }
}
