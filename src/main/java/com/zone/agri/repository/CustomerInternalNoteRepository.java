package com.zone.agri.repository;

import com.zone.agri.entity.CustomerInternalNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerInternalNoteRepository extends JpaRepository<CustomerInternalNote, Long> {
    List<CustomerInternalNote> findByCustomerUserIdOrderByCreatedAtDesc(Long customerUserId);
}
