package com.zone.agri.repository;

import com.zone.agri.entity.SupplierProductCatalog;
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
            "JOIN FETCH spc.product p " +
            "JOIN FETCH spc.supplier s " +
            "WHERE s.id = :supplierId " +
            "ORDER BY p.name ASC")
    List<SupplierProductCatalog> findAllBySupplierId(@Param("supplierId") Long supplierId);

    @Query("SELECT spc FROM SupplierProductCatalog spc " +
            "WHERE spc.supplier.id = :supplierId AND spc.product.id = :productId")
    Optional<SupplierProductCatalog> findBySupplierIdAndProductId(@Param("supplierId") Long supplierId,
            @Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM SupplierProductCatalog spc WHERE spc.supplier.id = :supplierId AND spc.product.id NOT IN :productIds")
    void deleteBySupplierIdAndProductIdNotIn(@Param("supplierId") Long supplierId,
            @Param("productIds") List<Long> productIds);

    @Modifying
    @Query("DELETE FROM SupplierProductCatalog spc WHERE spc.supplier.id = :supplierId")
    void deleteBySupplierId(@Param("supplierId") Long supplierId);
}
