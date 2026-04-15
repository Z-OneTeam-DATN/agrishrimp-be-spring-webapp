package com.zone.agri.repository;

import com.zone.agri.entity.InventoryTransferDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransferDetailRepository extends JpaRepository<InventoryTransferDetail, Long> {

    List<InventoryTransferDetail> findByInventoryTransferId(Long transferId);

    /**
     * Tìm dòng chi tiết theo phiếu và sản phẩm.
     * Dùng khi merge thêm hàng vào phiếu đang PENDING.
     */
    Optional<InventoryTransferDetail> findByInventoryTransferIdAndProductVariantId(Long transferId, Long variantId);
}
