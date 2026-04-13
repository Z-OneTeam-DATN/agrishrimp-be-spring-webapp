package com.zone.agri.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zone.agri.entity.CustomerStatusLog;

public interface CustomerStatusLogRepository extends JpaRepository<CustomerStatusLog, Long> {
    List<CustomerStatusLog> findByCustomerUserIdOrderByCreatedAtDesc(Long customerUserId);
}
