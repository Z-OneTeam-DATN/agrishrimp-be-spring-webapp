package com.zone.agri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCompletionScheduler {

    private final OrderService orderService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCompleteDeliveredOrders() {
        log.info("Running scheduled delivery completion job");
        orderService.autoCompleteDeliveredOrders();
    }
}
