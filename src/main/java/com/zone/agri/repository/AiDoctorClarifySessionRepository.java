package com.zone.agri.repository;

import com.zone.agri.entity.AiDoctorClarifySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiDoctorClarifySessionRepository extends JpaRepository<AiDoctorClarifySession, Long> {

    /**
     * Tra cứu KHÔNG phân biệt userId — diagnosis_id đã là unique constraint (một phiên chỉ có
     * đúng một chủ). Ownership được kiểm tra riêng ở AiDoctorClarifyService (so sánh userId của
     * caller với session.getUserId()) thay vì tách 2 query, để một request bị đổi danh tính giữa
     * chừng (vd token hết hạn khiến private → public) trả lỗi rõ ràng thay vì crash do trùng
     * unique constraint khi cố insert phiên mới cho cùng diagnosisId.
     */
    Optional<AiDoctorClarifySession> findByDiagnosisId(String diagnosisId);
}
