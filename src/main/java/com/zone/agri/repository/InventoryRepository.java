package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.enums.VariantStatus;
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

        @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant " +
                        "AND (i.batchNumber = :batchNumber OR (i.batchNumber IS NULL AND :batchNumber IS NULL)) " +
                        "AND (i.importPrice = :importPrice OR (i.importPrice IS NULL AND :importPrice IS NULL))")
        Optional<Inventory> findExactBatch(
                        @Param("branch") Branch branch,
                        @Param("variant") ProductVariant variant,
                        @Param("batchNumber") String batchNumber,
                        @Param("importPrice") BigDecimal importPrice);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant " +
                        "AND (i.batchNumber = :batchNumber OR (i.batchNumber IS NULL AND :batchNumber IS NULL)) " +
                        "AND (i.importPrice = :importPrice OR (i.importPrice IS NULL AND :importPrice IS NULL))")
        Optional<Inventory> findExactBatchWithLock(
                        @Param("branch") Branch branch,
                        @Param("variant") ProductVariant variant,
                        @Param("batchNumber") String batchNumber,
                        @Param("importPrice") BigDecimal importPrice);

        @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.quantity > 0 "
                        +
                        "ORDER BY i.expiryDate ASC, i.lastReceiptDate ASC")
        List<Inventory> findAvailableBatchesForVariant(@Param("branchId") Long branchId,
                        @Param("variantId") Long variantId);

        @Query("SELECT CAST(COALESCE(SUM(i.quantity), 0) AS Long) FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
        Long sumQuantityByBranchAndVariant(@Param("branchId") Long branchId, @Param("variantId") Long variantId);

        @Query("SELECT CAST(COALESCE(SUM(i.defectiveQuantity), 0) AS Long) FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
        Long sumDefectiveQuantityByBranchAndVariant(@Param("branchId") Long branchId,
                        @Param("variantId") Long variantId);

        @Query("SELECT CAST(COALESCE(SUM(i.defectiveQuantity), 0) AS Long) FROM Inventory i " +
                        "WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId " +
                        "AND (TRIM(LOWER(i.batchNumber)) = TRIM(LOWER(:batchNumber)) OR (i.batchNumber IS NULL AND :batchNumber IS NULL))")
        Long sumDefectiveQuantityByBranchAndVariantAndBatch(@Param("branchId") Long branchId,
                        @Param("variantId") Long variantId, @Param("batchNumber") String batchNumber);

        @Query("SELECT COALESCE(SUM(i.quantity), 0L) FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.batchNumber = :batchNumber")
        Long sumQuantityByBranchAndVariantAndBatch(@Param("branchId") Long branchId, @Param("variantId") Long variantId,
                        @Param("batchNumber") String batchNumber);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.batchNumber = :batchNumber")
        List<Inventory> findExactBatchListByNumber(@Param("branchId") Long branchId, @Param("variantId") Long variantId,
                        @Param("batchNumber") String batchNumber);

        Optional<Inventory> findByBranchAndProductVariantAndBatchNumberAndImportPrice(
                        Branch branch, ProductVariant variant, String batchNumber, BigDecimal importPrice);

        List<Inventory> findByProductVariantId(Long variantId);

        @Query("SELECT i FROM Inventory i " +
                        "JOIN FETCH i.branch b " +
                        "WHERE i.productVariant.id = :variantId")
        List<Inventory> findByProductVariantIdWithBranch(@Param("variantId") Long variantId);

        @Query("""
                        SELECT COALESCE(SUM(i.quantity), 0L)
                        FROM Inventory i
                        WHERE i.productVariant.product.id = :productId
                        """)
        Long sumQuantityByProductId(@Param("productId") Long productId);

        @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
        Long sumQuantityByProductVariantId(@Param("variantId") Long variantId);

        @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
        Long sumQuantityByVariantId(@Param("variantId") Long variantId);

        boolean existsByProductVariantProductId(Long productId);

        @Query("""
                        SELECT i.productVariant.product.id, COALESCE(SUM(i.quantity), 0L)
                        FROM Inventory i
                        WHERE i.productVariant.product.id IN :productIds
                        GROUP BY i.productVariant.product.id
                        """)
        List<Object[]> sumQuantityGroupByProductIds(@Param("productIds") List<Long> productIds);

        @Query("""
                        SELECT i.productVariant.product.id, COALESCE(SUM(i.quantity), 0L)
                        FROM Inventory i
                        WHERE i.productVariant.product.id IN :productIds
                        AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE
                        AND i.productVariant.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
                        GROUP BY i.productVariant.product.id
                        """)
        List<Object[]> sumActiveBranchQuantityGroupByProductIds(@Param("productIds") List<Long> productIds);

        @Query("""
                        SELECT i.productVariant.product.id, MIN(i.importPrice)
                        FROM Inventory i
                        WHERE i.productVariant.product.id IN :productIds AND i.quantity > 0
                        GROUP BY i.productVariant.product.id
                        """)
        List<Object[]> findMinImportPriceGroupByProductIds(@Param("productIds") List<Long> productIds);

        @Query("SELECT new com.zone.agri.dto.response.inventory.InventorySearchResponse(" +
                        "pv.id, p.name, pv.customSpecs, pv.sku, pv.barcode, " +
                        "i.batchNumber, i.quantity, i.defectiveQuantity, 0, '', i.importPrice, i.shelfLocation, " +
                        "i.expiryDate, pv.imageUrl, 'Cái') " +
                        "FROM Inventory i " +
                        "JOIN i.productVariant pv " +
                        "JOIN pv.product p " +
                        "WHERE pv.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE " +
                        "AND (:branchId IS NULL OR i.branch.id = :branchId) " +
                        "AND (:keyword IS NULL OR :keyword = '' " +
                        "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR LOWER(pv.customSpecs) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR LOWER(pv.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR LOWER(pv.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR LOWER(i.batchNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        List<InventorySearchResponse> searchInventoryForCheck(@Param("keyword") String keyword,
                        @Param("branchId") Long branchId);

        @Query("SELECT COUNT(DISTINCT i.productVariant.product.id) FROM Inventory i " +
                        "WHERE (:branchId IS NULL OR i.branch.id = :branchId)")
        long countDistinctProducts(@Param("branchId") Long branchId);

        @Query("SELECT COUNT(DISTINCT i.productVariant.product.id) FROM Inventory i " +
                        "WHERE (:branchId IS NULL OR i.branch.id = :branchId) " +
                        "GROUP BY i.productVariant.product.id " +
                        "HAVING SUM(i.quantity) > 0 AND SUM(i.quantity) <= :threshold")
        List<Long> getLowStockProductIds(@Param("threshold") int threshold, @Param("branchId") Long branchId);

        @Query("SELECT COUNT(DISTINCT p.id) FROM Product p " +
                        "WHERE p.id NOT IN (SELECT DISTINCT i.productVariant.product.id FROM Inventory i " +
                        "WHERE (:branchId IS NULL OR i.branch.id = :branchId) AND i.quantity > 0)")
        long countOutOfStockProducts(@Param("branchId") Long branchId);

        @Query("SELECT i FROM Inventory i WHERE i.defectiveQuantity > :threshold")
        List<Inventory> findAllByDefectiveQuantityGreaterThan(@Param("threshold") int threshold);

        @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.defectiveQuantity > :threshold")
        List<Inventory> findAllByBranchIdAndDefectiveQuantityGreaterThan(@Param("branchId") Long branchId,
                        @Param("threshold") int threshold);

        @Query("SELECT COALESCE(SUM(i.quantity * i.importPrice), 0) FROM Inventory i " +
                        "WHERE (:branchId IS NULL OR i.branch.id = :branchId)")
        BigDecimal sumTotalValue(@Param("branchId") Long branchId);

        @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant")
        List<Inventory> rawFindByBranchAndProductVariant(@Param("branch") Branch branch,
                        @Param("variant") ProductVariant variant);

        @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
        List<Inventory> rawFindByBranchIdAndProductVariantId(@Param("branchId") Long branchId,
                        @Param("variantId") Long variantId);

        @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.sku = :sku")
        List<Inventory> rawFindByBranchIdAndProductVariantSku(@Param("branchId") Long branchId,
                        @Param("sku") String sku);

        @Query("SELECT i FROM Inventory i WHERE i.productVariant.id IN :variantIds")
        List<Inventory> rawFindByProductVariantIdIn(@Param("variantIds") List<Long> variantIds);

        @Query("SELECT i FROM Inventory i JOIN FETCH i.branch b WHERE i.productVariant.id IN :variantIds")
        List<Inventory> findByProductVariantIdInWithBranch(@Param("variantIds") List<Long> variantIds);

        @Query("""
                        SELECT i FROM Inventory i
                        WHERE i.branch.id IN :branchIds
                          AND i.productVariant.id IN :variantIds
                          AND i.quantity > 0
                        ORDER BY i.id ASC
                        """)
        List<Inventory> rawFindInventoryMatrix(@Param("branchIds") List<Long> branchIds,
                        @Param("variantIds") List<Long> variantIds);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.quantity > 0 ORDER BY i.id ASC")
        List<Inventory> findForUpdateFIFO(
                        @Param("branchId") Long branchId,
                        @Param("variantId") Long variantId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.defectiveQuantity > 0 ORDER BY i.id ASC")
        List<Inventory> findForUpdateDefectiveFIFO(
                        @Param("branchId") Long branchId,
                        @Param("variantId") Long variantId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT i FROM Inventory i WHERE i.id = :id")
        Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

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

        default List<Inventory> aggregateInventoryListToArray(List<Inventory> rawList) {
                if (rawList == null || rawList.isEmpty())
                        return new ArrayList<>();

                Map<String, List<Inventory>> grouped = rawList.stream()
                                .collect(Collectors.groupingBy(
                                                i -> i.getBranch().getId() + "_" + i.getProductVariant().getId()));

                List<Inventory> result = new ArrayList<>();
                for (List<Inventory> group : grouped.values()) {
                        aggregateInventoryList(group).ifPresent(result::add);
                }
                return result;
        }

        interface StockSummaryProjection {
                Long getVariantId();

                String getSku();

                String getProductName();

                String getCategoryName();

                Long getBranchQuantity();

                BigDecimal getBranchValue();

                Long getSystemQuantity();

                BigDecimal getSystemValue();
        }

        @Query("""
                        SELECT pv.id AS variantId,
                               pv.sku AS sku,
                               p.name AS productName,
                               c.name AS categoryName,
                               COALESCE(SUM(CASE WHEN (:branchId IS NULL OR i.branch.id = :branchId) THEN i.quantity ELSE 0 END), 0) AS branchQuantity,
                               COALESCE(SUM(CASE WHEN (:branchId IS NULL OR i.branch.id = :branchId) THEN i.quantity * COALESCE(i.importPrice, 0) ELSE 0 END), 0) AS branchValue,
                               COALESCE(SUM(i.quantity), 0) AS systemQuantity,
                               COALESCE(SUM(i.quantity * COALESCE(i.importPrice, 0)), 0) AS systemValue
                        FROM Inventory i
                        JOIN i.productVariant pv
                        JOIN pv.product p
                        LEFT JOIN p.category c
                        GROUP BY pv.id, pv.sku, p.name, c.name
                        """)
        List<StockSummaryProjection> findStockSummary(@Param("branchId") Long branchId);

        interface VariantStockProjection {
                Long getVariantId();

                String getSku();

                String getProductName();

                Long getQuantity();

                BigDecimal getValue();
        }

        @Query("""
                        SELECT pv.id AS variantId,
                               pv.sku AS sku,
                               p.name AS productName,
                               COALESCE(SUM(i.quantity), 0) AS quantity,
                               COALESCE(SUM(i.quantity * COALESCE(i.importPrice, 0)), 0) AS value
                        FROM Inventory i
                        JOIN i.productVariant pv
                        JOIN pv.product p
                        WHERE (:branchId IS NULL OR i.branch.id = :branchId)
                        GROUP BY pv.id, pv.sku, p.name
                        """)
        List<VariantStockProjection> findCurrentStockByBranch(@Param("branchId") Long branchId);
}

