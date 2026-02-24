package com.zone.agri.repository;

import com.zone.agri.entity.StockRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRequestItemRepository extends JpaRepository<StockRequestItem, Long> {

    List<StockRequestItem> findByStockRequestId(Long stockRequestId);
}
