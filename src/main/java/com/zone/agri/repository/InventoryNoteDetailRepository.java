package com.zone.agri.repository;

import com.zone.agri.entity.InventoryNoteDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryNoteDetailRepository 
        extends JpaRepository<InventoryNoteDetail, Long> {

    // Kiểm tra sản phẩm đã từng xuất/nhập chưa
    boolean existsByProductVariantProductId(Long productId);

    // Lấy tất cả chi tiết theo ID phiếu
    List<InventoryNoteDetail> findByInventoryNoteId(Long inventoryNoteId);

    // Xóa toàn bộ chi tiết theo ID phiếu
    void deleteByInventoryNoteId(Long inventoryNoteId);
}