package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
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
    // 1. CÁC HÀM MỚI DÀNH CHO QUẢN LÝ THEO LÔ VÀ GIÁ (CỦA BẠN)
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

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.quantity > 0 " +
            "ORDER BY i.expiryDate ASC, i.lastReceiptDate ASC")
    List<Inventory> findAvailableBatchesForVariant(@Param("branchId") Long branchId, @Param("variantId") Long variantId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
    Integer sumQuantityByBranchAndVariant(@Param("branchId") Long branchId, @Param("variantId") Long variantId);

    Optional<Inventory> findByBranchAndProductVariantAndBatchNumberAndImportPrice(
            Branch branch, ProductVariant variant, String batchNumber, BigDecimal importPrice
    );

    // ==============================================================
    // 2. GIỮ NGUYÊN CÁC HÀM TÍNH TỔNG QUAN TRỌNG
    // ==============================================================
    @Query("SELECT COALESCE(SUM(i.quantity), 0L) FROM Inventory i WHERE i.productVariant.product.id = :productId")
    Long sumQuantityByProductId(@Param("productId") Long productId);

    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
    Long sumQuantityByProductVariantId(@Param("variantId") Long variantId);

    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
    Integer sumQuantityByVariantId(@Param("variantId") Long variantId);

    boolean existsByProductVariantProductId(Long productId);

    @Query("""
           SELECT i.productVariant.product.id, COALESCE(SUM(i.quantity), 0)
           FROM Inventory i
           WHERE i.productVariant.product.id IN :productIds
           GROUP BY i.productVariant.product.id
           """)
    List<Object[]> sumQuantityGroupByProductIds(@Param("productIds") List<Long> productIds);


    // ==============================================================
    // 3. ADAPTER: VIẾT LẠI CÁC HÀM CŨ ĐỂ KHÔNG LÀM LỖI CODE NGƯỜI KHÁC
    // ==============================================================

    // --- BƯỚC 3.1: Các raw query lấy danh sách tất cả các lô ---
    @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant")
    List<Inventory> rawFindByBranchAndProductVariant(@Param("branch") Branch branch, @Param("variant") ProductVariant variant);

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
    List<Inventory> rawFindByBranchIdAndProductVariantId(@Param("branchId") Long branchId, @Param("variantId") Long variantId);

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.sku = :sku")
    List<Inventory> rawFindByBranchIdAndProductVariantSku(@Param("branchId") Long branchId, @Param("sku") String sku);

    @Query("SELECT i FROM Inventory i WHERE i.productVariant.id IN :variantIds")
    List<Inventory> rawFindByProductVariantIdIn(@Param("variantIds") List<Long> variantIds);

    @Query("""
           SELECT i FROM Inventory i
           WHERE i.branch.id IN :branchIds
             AND i.productVariant.id IN :variantIds
           """)
    List<Inventory> rawFindInventoryMatrix(@Param("branchIds") List<Long> branchIds, @Param("variantIds") List<Long> variantIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
    List<Inventory> rawFindForUpdate(@Param("branchId") Long branchId, @Param("variantId") Long variantId);


    // --- BƯỚC 3.2: Khai báo lại các tên hàm cũ mà file khác đang gọi ---
    // (Bằng cách dùng "default", Spring Data sẽ không tự gen code nữa mà chạy code của chúng ta)

    default Optional<Inventory> findByBranchAndProductVariant(Branch branch, ProductVariant variant) {
        return aggregateInventoryList(rawFindByBranchAndProductVariant(branch, variant));
    }

    default Optional<Inventory> findByBranchIdAndProductVariantId(Long branchId, Long variantId) {
        return aggregateInventoryList(rawFindByBranchIdAndProductVariantId(branchId, variantId));
    }

    default Optional<Inventory> findByBranchIdAndProductVariantSku(Long branchId, String sku) {
        return aggregateInventoryList(rawFindByBranchIdAndProductVariantSku(branchId, sku));
    }

    default Optional<Inventory> findForUpdate(Long branchId, Long variantId) {
        return aggregateInventoryList(rawFindForUpdate(branchId, variantId));
    }

    default List<Inventory> findByProductVariantIdIn(List<Long> variantIds) {
        return aggregateInventoryListToArray(rawFindByProductVariantIdIn(variantIds));
    }

    default List<Inventory> findInventoryMatrix(List<Long> branchIds, List<Long> variantIds) {
        return aggregateInventoryListToArray(rawFindInventoryMatrix(branchIds, variantIds));
    }


    // --- HÀM BỔ TRỢ: Gom nhiều Lô hàng thành 1 Inventory "Đại diện" ---

    /**
     * Dành cho những logic cũ (như Check stock, Get quantity)
     * Nó sẽ gom 10 lô hàng lại thành 1 Object Inventory duy nhất với Quantity = Tổng 10 lô.
     */
    default Optional<Inventory> aggregateInventoryList(List<Inventory> list) {
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        // Lấy thông tin cơ bản từ lô hàng cũ nhất
        Inventory aggregated = new Inventory();
        aggregated.setId(list.get(0).getId());
        aggregated.setBranch(list.get(0).getBranch());
        aggregated.setProductVariant(list.get(0).getProductVariant());
        aggregated.setShelfLocation(list.get(0).getShelfLocation());

        // CỘNG DỒN SỐ LƯỢNG TỒN TẤT CẢ CÁC LÔ
        int totalQty = list.stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum();
        aggregated.setQuantity(totalQty);

        return Optional.of(aggregated);
    }

    /**
     * Dành cho các hàm cũ trả về List (vd: Giỏ hàng check tồn kho nhiều món 1 lúc)
     */
    default List<Inventory> aggregateInventoryListToArray(List<Inventory> rawList) {
        if (rawList == null || rawList.isEmpty()) return new ArrayList<>();

        // Gom nhóm theo (BranchId_VariantId) để đảm bảo không bị cộng nhầm sản phẩm khác nhau
        Map<String, List<Inventory>> grouped = rawList.stream()
                .collect(Collectors.groupingBy(i -> i.getBranch().getId() + "_" + i.getProductVariant().getId()));

        List<Inventory> result = new ArrayList<>();
        for (List<Inventory> group : grouped.values()) {
            aggregateInventoryList(group).ifPresent(result::add);
        }
        return result;
    }
}