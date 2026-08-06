package com.zone.agri.repository;

import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiDoctorDiagnosisHistoryRepository extends JpaRepository<AiDoctorDiagnosisHistory, Long> {

    /**
     * Lấy lịch sử của một user, mới nhất trước.
     * Dùng Pageable để giới hạn số lượng và hỗ trợ phân trang sau này.
     */
    Page<AiDoctorDiagnosisHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Lấy chi tiết một ca chẩn đoán — chỉ trả về nếu thuộc đúng user.
     * Kết hợp lookup + ownership check trong 1 query để tránh leak data.
     */
    Optional<AiDoctorDiagnosisHistory> findByIdAndUserId(Long id, Long userId);

    /** Chi lay cot createdAt de group theo ngay o tang Java, dung cho tinh nang "so kham". */
    @Query("SELECT h.createdAt FROM AiDoctorDiagnosisHistory h WHERE h.userId = :userId AND h.createdAt >= :since ORDER BY h.createdAt DESC")
    List<LocalDateTime> findCreatedAtByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    List<AiDoctorDiagnosisHistory> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long userId, LocalDateTime startOfDay, LocalDateTime endOfDay);
}
