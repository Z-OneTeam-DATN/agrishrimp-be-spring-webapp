package com.zone.agri.service;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderInventoryReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;

    public String buildSubOrderReferenceCode(SubOrder subOrder) {
        return subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
    }

    public String buildOrderReferenceCode(Order order) {
        return order.getCode();
    }

    @Transactional
    public void reserveInventory(Long branchId, Long productVariantId, int quantity, String referenceCode, String reason) {
        int reservedQuantity = reserveInventoryUpTo(branchId, productVariantId, quantity, referenceCode, reason);
        if (reservedQuantity < quantity) {
            throw new ConflictException("Ton kho da thay doi trong luc giu hang cho don hang", true);
        }
    }

    @Transactional
    public int reserveInventoryUpTo(
            Long branchId,
            Long productVariantId,
            int quantity,
            String referenceCode,
            String reason) {
        if (branchId == null || productVariantId == null || quantity <= 0 || referenceCode == null
                || referenceCode.isBlank()) {
            return 0;
        }

        int remainingToReserve = quantity;
        int reservedQuantity = 0;
        List<Inventory> batches = inventoryRepository.findForUpdateFIFO(branchId, productVariantId);

        for (Inventory batch : batches) {
            if (remainingToReserve <= 0) {
                break;
            }

            int currentQty = Objects.requireNonNullElse(batch.getQuantity(), 0);
            int currentReserved = Objects.requireNonNullElse(batch.getReservedQuantity(), 0);
            int available = Math.max(0, currentQty - currentReserved);
            if (available <= 0) {
                continue;
            }

            int reserveAmount = Math.min(available, remainingToReserve);
            int newReserved = currentReserved + reserveAmount;
            batch.setReservedQuantity(newReserved);
            inventoryRepository.save(batch);

            transactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.ORDER_RESERVE)
                    .quantityChange(reserveAmount)
                    .newBalance(newReserved)
                    .referenceCode(referenceCode)
                    .reason(reason)
                    .createdAt(LocalDateTime.now())
                    .inventory(batch)
                    .build());

            reservedQuantity += reserveAmount;
            remainingToReserve -= reserveAmount;
        }

        return reservedQuantity;
    }

    @Transactional
    public void releaseReservedInventory(String referenceCode, String reason) {
        for (InventoryTransaction reservation : findReservationTransactions(referenceCode)) {
            int reservedAmount = Math.abs(Objects.requireNonNullElse(reservation.getQuantityChange(), 0));
            if (reservedAmount <= 0 || reservation.getInventory() == null || reservation.getInventory().getId() == null) {
                continue;
            }

            Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getInventory().getId())
                    .orElseThrow(() -> new NotFoundException("Khong tim thay ton kho can giai phong"));

            int currentReserved = Objects.requireNonNullElse(inventory.getReservedQuantity(), 0);
            int releaseAmount = Math.min(reservedAmount, currentReserved);
            if (releaseAmount <= 0) {
                continue;
            }

            int newReserved = currentReserved - releaseAmount;
            inventory.setReservedQuantity(newReserved);
            inventoryRepository.save(inventory);

            transactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.ORDER_RELEASE)
                    .quantityChange(-releaseAmount)
                    .newBalance(newReserved)
                    .referenceCode(referenceCode)
                    .reason(reason)
                    .createdAt(LocalDateTime.now())
                    .inventory(inventory)
                    .build());
        }
    }

    @Transactional
    public void shipReservedInventory(String referenceCode, String reason) {
        for (InventoryTransaction reservation : findReservationTransactions(referenceCode)) {
            int reservedAmount = Math.abs(Objects.requireNonNullElse(reservation.getQuantityChange(), 0));
            if (reservedAmount <= 0 || reservation.getInventory() == null || reservation.getInventory().getId() == null) {
                continue;
            }

            Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getInventory().getId())
                    .orElseThrow(() -> new NotFoundException("Khong tim thay ton kho can xuat"));

            int currentQty = Objects.requireNonNullElse(inventory.getQuantity(), 0);
            int currentReserved = Objects.requireNonNullElse(inventory.getReservedQuantity(), 0);
            int shipAmount = Math.min(reservedAmount, currentReserved);
            if (shipAmount <= 0) {
                continue;
            }
            if (currentQty < shipAmount) {
                throw new ConflictException("Ton kho da thay doi trong luc xuat kho don hang", true);
            }

            int newQty = currentQty - shipAmount;
            int newReserved = currentReserved - shipAmount;
            inventory.setQuantity(newQty);
            inventory.setReservedQuantity(newReserved);
            inventoryRepository.save(inventory);

            transactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.SALE)
                    .quantityChange(-shipAmount)
                    .newBalance(newQty)
                    .referenceCode(referenceCode)
                    .reason(reason)
                    .createdAt(LocalDateTime.now())
                    .inventory(inventory)
                    .build());
        }
    }

    private List<InventoryTransaction> findReservationTransactions(String referenceCode) {
        return transactionRepository.findByReferenceCodeAndType(referenceCode, TransactionType.ORDER_RESERVE).stream()
                .sorted(Comparator.comparing(InventoryTransaction::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(InventoryTransaction::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }
}
