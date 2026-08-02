package com.zone.agri.repository;

import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiDiseaseKnowledgeRepository extends JpaRepository<AiDiseaseKnowledge, Long> {
    Optional<AiDiseaseKnowledge> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);

    // LEFT JOIN FETCH category: ket qua nay duoc AiKnowledgeService cache lai (approvedSnapshotRef,
    // TTL 5 phut) va tai su dung xuyen suot nhieu request/transaction khac nhau — neu de category
    // lazy-load binh thuong, proxy chua khoi tao se bi ket vao cache va nem
    // LazyInitializationException ("no session") ngay khi mot request SAU nay (transaction khac)
    // moi lan dau doc field cua no (vd hien thi ten danh muc trong goi y cau hoi).
    @Query("SELECT d FROM AiDiseaseKnowledge d LEFT JOIN FETCH d.category "
            + "WHERE d.status = :status AND d.enabled = true "
            + "ORDER BY d.priority DESC, d.nameVi ASC")
    List<AiDiseaseKnowledge> findAllByStatusAndEnabledTrueOrderByPriorityDescNameViAsc(@Param("status") AiKnowledgeStatus status);
}
