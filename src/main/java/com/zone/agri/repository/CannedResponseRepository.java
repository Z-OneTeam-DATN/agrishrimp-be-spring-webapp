package com.zone.agri.repository;

import com.zone.agri.entity.CannedResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CannedResponseRepository extends JpaRepository<CannedResponse, Long> {
    List<CannedResponse> findAllByOrderByShortcutAsc();
    List<CannedResponse> findByShortcutContainingIgnoreCase(String keyword);
}
