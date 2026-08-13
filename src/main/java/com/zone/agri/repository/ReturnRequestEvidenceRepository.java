package com.zone.agri.repository;

import com.zone.agri.entity.ReturnRequestEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReturnRequestEvidenceRepository extends JpaRepository<ReturnRequestEvidence, Long> {
}
