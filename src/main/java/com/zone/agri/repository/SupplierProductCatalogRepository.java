package com.zone.agri.repository;

import com.zone.agri.entity.SupplierProductCatalog;
import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierProductCatalogRepository extends JpaRepository<SupplierProductCatalog, Long> {

    @Query("SELECT spc FROM SupplierProductCatalog spc " +
            "JOIN FETCH spc.productVariant pv " +
            "JOIN FETCH pv.product p " +
            "JOIN FETCH spc.supplier s " +
            "WHERE s.id = :supplierId " +
            "ORDER BY pv.sku ASC")
    List<SupplierProductCatalog> findAllBySupplierId(@Param("supplierId") Long supplierId);

    @Query("SELECT spc FROM SupplierProductCatalog spc " +
            "WHERE spc.supplier.id = :supplierId AND spc.productVariant.id = :productVariantId")
    Optional<SupplierProductCatalog> findBySupplierIdAndProductVariantId(@Param("supplierId") Long supplierId,
            @Param("productVariantId") Long productVariantId);

    @Query("""
            SELECT spc
            FROM SupplierProductCatalog spc
            WHERE spc.supplier.id = :supplierId
              AND spc.productVariant.id = :productVariantId
              AND spc.status = com.zone.agri.entity.enums.SupplierProductCatalogStatus.AVAILABLE
            """)
    Optional<SupplierProductCatalog> findAvailableBySupplierIdAndProductVariantId(
            @Param("supplierId") Long supplierId,
            @Param("productVariantId") Long productVariantId);

    @Query("""
            SELECT CASE WHEN COUNT(spc) > 0 THEN true ELSE false END
            FROM SupplierProductCatalog spc
            WHERE spc.supplier.id = :supplierId
              AND spc.productVariant.id = :productVariantId
              AND spc.status = com.zone.agri.entity.enums.SupplierProductCatalogStatus.AVAILABLE
            """)
    boolean existsAvailableBySupplierIdAndProductVariantId(@Param("supplierId") Long supplierId,
            @Param("productVariantId") Long productVariantId);

    @Query("""
            SELECT spc
            FROM SupplierProductCatalog spc
            JOIN FETCH spc.supplier s
            JOIN FETCH spc.productVariant pv
            WHERE pv.id IN :productVariantIds
              AND spc.status = :status
              AND s.status = com.zone.agri.entity.enums.SupplierStatus.ACTIVE
            ORDER BY s.id ASC, pv.id ASC
            """)
    List<SupplierProductCatalog> findByProductVariantIdInAndStatus(
            @Param("productVariantIds") List<Long> productVariantIds,
            @Param("status") SupplierProductCatalogStatus status);

    @Modifying
    @Query("DELETE FROM SupplierProductCatalog spc WHERE spc.supplier.id = :supplierId AND spc.productVariant.id NOT IN :productVariantIds")
    void deleteBySupplierIdAndProductVariantIdNotIn(@Param("supplierId") Long supplierId,
            @Param("productVariantIds") List<Long> productVariantIds);

    @Modifying
    @Query("DELETE FROM SupplierProductCatalog spc WHERE spc.supplier.id = :supplierId")
    void deleteBySupplierId(@Param("supplierId") Long supplierId);

    boolean existsBySupplierId(Long supplierId);
}
