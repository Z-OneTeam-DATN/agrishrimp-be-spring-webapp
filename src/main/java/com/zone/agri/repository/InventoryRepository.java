package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.dto.response.inventory.InventorySearchResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // ==============================================================
    // 1. CÁC HÀM MỚI DÀNH CHO QUẢN LÝ THEO LÔ VÀ GIÁ
    // ==============================================================
    @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant " +
            "AND (i.batchNumber = :batchNumber OR (i.batchNumber IS NULL AND :batchNumber IS NULL)) " +
            "AND (i.importPrice = :importPrice OR (i.importPrice IS NULL AND :importPrice IS NULL))")
    Optional<Inventory> findExactBatch(
            @Param("branch") Branch branch,
            @Param("variant") ProductVariant variant,
            @Param("batchNumber") String batchNumber,
            @Param("importPrice") BigDecimal importPrice
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant " +
            "AND (i.batchNumber = :batchNumber OR (i.batchNumber IS NULL AND :batchNumber IS NULL)) " +
            "AND (i.importPrice = :importPrice OR (i.importPrice IS NULL AND :importPrice IS NULL))")
    Optional<Inventory> findExactBatchWithLock(
            @Param("branch") Branch branch,
            @Param("variant") ProductVariant variant,
            @Param("batchNumber") String batchNumber,
            @Param("importPrice") BigDecimal importPrice
    );

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.quantity > 0 " +
            "ORDER BY i.expiryDate ASC, i.lastReceiptDate ASC")
    List<Inventory> findAvailableBatchesForVariant(@Param("branchId") Long branchId, @Param("variantId") Long variantId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
    Integer sumQuantityByBranchAndVariant(@Param("branchId") Long branchId, @Param("variantId") Long variantId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.batchNumber = :batchNumber")
    Integer sumQuantityByBranchAndVariantAndBatch(@Param("branchId") Long branchId, @Param("variantId") Long variantId, @Param("batchNumber") String batchNumber);

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.batchNumber = :batchNumber")
    List<Inventory> findExactBatchListByNumber(@Param("branchId") Long branchId, @Param("variantId") Long variantId, @Param("batchNumber") String batchNumber);

    Optional<Inventory> findByBranchAndProductVariantAndBatchNumberAndImportPrice(
            Branch branch, ProductVariant variant, String batchNumber, BigDecimal importPrice
    );

    // 👉 Tìm tất cả lô hàng của 1 Variant (dùng trong ProductService và InventoryTransferService)
    List<Inventory> findByProductVariantId(Long variantId);

    // ==============================
    // TỔNG TỒN KHO TOÀN HỆ THỐNG THEO PRODUCT
    // ==============================
    @Query("""
           SELECT COALESCE(SUM(i.quantity), 0L)
           FROM Inventory i
           WHERE i.productVariant.product.id = :productId
           """)
    Long sumQuantityByProductId(@Param("productId") Long productId);

    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
    Long sumQuantityByProductVariantId(@Param("variantId") Long variantId);

    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
    Integer sumQuantityByVariantId(@Param("variantId") Long variantId);

    boolean existsByProductVariantProductId(Long productId);

    // ==============================
    // BATCH: tổng tồn kho cho nhiều sản phẩm (dùng cho API public list)
    // ==============================
    @Query("""
           SELECT i.productVariant.product.id, COALESCE(SUM(i.quantity), 0)
           FROM Inventory i
           WHERE i.productVariant.product.id IN :productIds
           GROUP BY i.productVariant.product.id
           """)
    List<Object[]> sumQuantityGroupByProductIds(@Param("productIds") List<Long> productIds);


    @Query("""
           SELECT new com.zone.agri.dto.response.inventory.InventorySearchResponse(
               pv.id, p.name, pv.customSpecs, pv.sku, pv.barcode,
               i.batchNumber, i.quantity, i.importPrice, i.shelfLocation,
               i.expiryDate, pv.imageUrl
           )
           FROM Inventory i
           JOIN i.productVariant pv
           JOIN pv.product p
           WHERE pv.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
             AND (:branchId IS NULL OR i.branch.id = :branchId)
             AND (
                 :keyword IS NULL OR :keyword = ''
                 OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(pv.customSpecs) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(pv.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(pv.barcode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(i.batchNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
             )
           """)
    List<InventorySearchResponse> searchInventoryForCheck(@Param("keyword") String keyword, @Param("branchId") Long branchId);

    // ==============================================================
    // 2. ADAPTER: VIẾT LẠI CÁC HÀM CŨ ĐỂ KHÔNG LÀM LỖI CODE NGƯỜI KHÁC
    // ==============================================================

    // --- BƯỚC 2.1: Các raw query lấy danh sách tất cả các lô ---
    @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant")
    List<Inventory> rawFindByBranchAndProductVariant(@Param("branch") Branch branch, @Param("variant") ProductVariant variant);

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
    List<Inventory> rawFindByBranchIdAndProductVariantId(@Param("branchId") Long branchId, @Param("variantId") Long variantId);

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.sku = :sku")
    List<Inventory> rawFindByBranchIdAndProductVariantSku(@Param("branchId") Long branchId, @Param("sku") String sku);

    @Query("SELECT i FROM Inventory i WHERE i.productVariant.id IN :variantIds")
    List<Inventory> rawFindByProductVariantIdIn(@Param("variantIds") List<Long> variantIds);

    /**
     * Query gộp tồn kho nhiều chi nhánh + nhiều variant — 1 lần duy nhất.
     * ORDER BY i.id ASC để lấy lô hàng cũ xuất trước (FIFO).
     */
    @Query("""
           SELECT i FROM Inventory i
           WHERE i.branch.id IN :branchIds
             AND i.productVariant.id IN :variantIds
             AND i.quantity > 0
           ORDER BY i.id ASC
           """)
    List<Inventory> rawFindInventoryMatrix(@Param("branchIds") List<Long> branchIds, @Param("variantIds") List<Long> variantIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.quantity > 0 ORDER BY i.id ASC")
    List<Inventory> findForUpdateFIFO(
            @Param("branchId") Long branchId,
            @Param("variantId") Long variantId
    );

    // --- BƯỚC 2.2: Khai báo lại các tên hàm cũ mà file khác đang gọi ---

    default Optional<Inventory> findByBranchAndProductVariant(Branch branch, ProductVariant variant) {
        return aggregateInventoryList(rawFindByBranchAndProductVariant(branch, variant));
    }

    default Optional<Inventory> findByBranchIdAndProductVariantId(Long branchId, Long variantId) {
        return aggregateInventoryList(rawFindByBranchIdAndProductVariantId(branchId, variantId));
    }

    default Optional<Inventory> findByBranchIdAndProductVariantSku(Long branchId, String sku) {
        return aggregateInventoryList(rawFindByBranchIdAndProductVariantSku(branchId, sku));
    }

    default List<Inventory> findByProductVariantIdIn(List<Long> variantIds) {
        return aggregateInventoryListToArray(rawFindByProductVariantIdIn(variantIds));
    }

    default List<Inventory> findInventoryMatrix(List<Long> branchIds, List<Long> variantIds) {
        return aggregateInventoryListToArray(rawFindInventoryMatrix(branchIds, variantIds));
    }


    // --- HÀM BỔ TRỢ: Gom nhiều Lô hàng thành 1 Inventory "Đại diện" ---

    /**
     * Dành cho những logic cũ (như Check stock, Get quantity).
     * Gom N lô hàng thành 1 Inventory với Quantity = Tổng N lô.
     */
    default Optional<Inventory> aggregateInventoryList(List<Inventory> list) {
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        Inventory aggregated = new Inventory();
        aggregated.setId(list.get(0).getId());
        aggregated.setBranch(list.get(0).getBranch());
        aggregated.setProductVariant(list.get(0).getProductVariant());
        aggregated.setShelfLocation(list.get(0).getShelfLocation());

        int totalQty = list.stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum();
        aggregated.setQuantity(totalQty);

        return Optional.of(aggregated);
    }

    /**
     * Dành cho các hàm cũ trả về List (vd: Giỏ hàng check tồn kho nhiều món 1 lúc).
     */
    default List<Inventory> aggregateInventoryListToArray(List<Inventory> rawList) {
        if (rawList == null || rawList.isEmpty()) return new ArrayList<>();

        Map<String, List<Inventory>> grouped = rawList.stream()
                .collect(Collectors.groupingBy(i -> i.getBranch().getId() + "_" + i.getProductVariant().getId()));

        List<Inventory> result = new ArrayList<>();
        for (List<Inventory> group : grouped.values()) {
            aggregateInventoryList(group).ifPresent(result::add);
        }
        return result;
    }
}
