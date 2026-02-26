package com.zone.agri.service;

import com.zone.agri.dto.transfer.TransferDetailResponse;
import com.zone.agri.dto.transfer.TransferRequest;
import com.zone.agri.dto.transfer.TransferItemRequest;
import com.zone.agri.dto.transfer.TransferResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private final InventoryTransferRepository transferRepo;
    private final BranchRepository branchRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;

    // ==========================================
    // BƯỚC 1: KHỞI TẠO PHIẾU (PENDING - CHƯA TRỪ KHO)
    // ==========================================
    @Transactional
    public InventoryTransfer createTransfer(TransferRequest req) {
        Branch fromBranch = branchRepo.findById(req.getFromBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho xuất"));
        Branch toBranch = branchRepo.findById(req.getToBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho nhận"));

        String newCode = String.format("PDC-%06d", transferRepo.countTotalTransfers() + 1);

        InventoryTransfer transfer = InventoryTransfer.builder()
                .transferCode(newCode)
                .status(InventoryTransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .fromBranch(fromBranch)
                .toBranch(toBranch)
                .transferType(req.getTransferType())
                .description(req.getDescription())
                .transporter(req.getTransporter())
                .vehicle(req.getVehicle())
                .dispatchOrder(req.getDispatchOrder())
                .referenceCode(req.getReferenceCode())
                .priority(req.getPriority())
                .transferDate(req.getTransferDate())
                .deadline(req.getDeadline())
                .build();

        List<InventoryTransferDetail> details = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (TransferItemRequest itemReq : req.getItems()) {
            // THAY ĐỔI: Tìm sản phẩm bằng SKU thay vì ID
            ProductVariant variant = variantRepo.findBySku(itemReq.getSku())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm với SKU " + itemReq.getSku() + " không tồn tại"));

            InventoryTransferDetail detail = InventoryTransferDetail.builder()
                    .inventoryTransfer(transfer)
                    .productVariant(variant) // Gán đúng variant vừa tìm được theo SKU
                    .quantity(itemReq.getQuantity())
                    .quantityRequested(itemReq.getQuantity())
                    .quantityReal(0)
                    .note(itemReq.getItemNote())
                    .build();

            details.add(detail);
            totalQty += itemReq.getQuantity();

            if (variant.getPrice() != null) {
                BigDecimal lineTotal = variant.getPrice().multiply(new BigDecimal(itemReq.getQuantity()));
                totalValue = totalValue.add(lineTotal);
            }
        }

        transfer.setDetails(details);
        transfer.setTotalQuantity(totalQty);
        transfer.setTotalValue(totalValue);

        return transferRepo.save(transfer);
    }

    // ==========================================
    // BƯỚC 2: DUYỆT XUẤT KHO (CHỈ ĐỔI TRẠNG THÁI)
    // ==========================================
    @Transactional
    public void approveAndShip(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xuất kho phiếu đang ở trạng thái Chờ Xuất!");
        }

        // CHỈ đổi trạng thái, KHÔNG trừ kho ở bước này nữa
        transfer.setStatus(InventoryTransferStatus.SHIPPING);
        transferRepo.save(transfer);
    }

    // ==========================================
    // BƯỚC 3: NHẬN HÀNG (TRỪ KHO XUẤT & CỘNG KHO NHẬP)
    // ==========================================
    @Transactional
    public void receiveTransfer(Long id, List<Map<String, Object>> receivedItems) {
        // 1. Lock bản ghi transfer để tránh tranh chấp dữ liệu (Pessimistic Lock nếu cần)
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException("Phiếu phải ở trạng thái Đang vận chuyển mới có thể nhận hàng!");
        }

        // 2. Chuyển list details thành Map để truy xuất cực nhanh và chính xác theo ID sản phẩm
        // Tránh việc dùng .stream().filter() lặp đi lặp lại trong vòng lặp for
        Map<Long, InventoryTransferDetail> detailMap = transfer.getDetails().stream()
                .collect(Collectors.toMap(
                        d -> d.getProductVariant().getId(),
                        d -> d,
                        (existing, replacement) -> existing // Tránh lỗi nếu có sản phẩm trùng trong list
                ));

        for (Map<String, Object> itemData : receivedItems) {
            Long variantId = ((Number) itemData.get("variantId")).longValue();
            Integer qtyReal = ((Number) itemData.get("quantityReal")).intValue();
            String note = itemData.get( "note") != null ? itemData.get("note").toString() : "";

            // 3. Lấy detail trực tiếp từ Map
            InventoryTransferDetail detail = detailMap.get(variantId);
            if (detail == null) {
                throw new RuntimeException("Sản phẩm ID " + variantId + " không tồn tại trong phiếu này!");
            }

            // Cập nhật số lượng thực nhận vào detail
            detail.setQuantityReal(qtyReal);
            detail.setNote(note);

            // 4. Cập nhật tồn kho
            if (qtyReal > 0) {
                Long fromBranchId = transfer.getFromBranch().getId();
                Long toBranchId = transfer.getToBranch().getId();

                // Thực hiện trừ kho nguồn
                updateInventoryQuantity(fromBranchId, variantId, -qtyReal);
                // Thực hiện cộng kho đích
                updateInventoryQuantity(toBranchId, variantId, qtyReal);
            }
        }

        // 5. Đổi trạng thái và lưu
        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);

        // Ép Hibernate đẩy dữ liệu xuống DB ngay lập tức để đảm bảo tính nhất quán
        transferRepo.flush();
    }

    // ==========================================
    // HÀM LẤY CHI TIẾT (ĐÃ SỬ DỤNG CONVERT DTO)
    // ==========================================
    public TransferDetailResponse getById(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu"));
        return convertToDetailResponse(transfer);
    }

    // ==========================================
    // HÀM LẤY DANH SÁCH (ĐÃ SỬ DỤNG CONVERT DTO)
    // ==========================================
    public Page<TransferResponse> getTransfers(String keyword, String statusStr, Pageable pageable) {
        InventoryTransferStatus status = null;
        if (statusStr != null && !statusStr.isEmpty() && !statusStr.equalsIgnoreCase("all")) {
            try {
                status = InventoryTransferStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception e) {
                // Ignore invalid status
            }
        }
        // Gọi query trong repository (nếu query đó trả về DTO thì không cần convert nữa)
        // Hiện tại query trong Repository của bạn đã tự tạo TransferResponse rồi.
        return transferRepo.searchTransfers(keyword, status, pageable);
    }

    // ==========================================
    // CÁC HÀM NGHIỆP VỤ PHỤ
    // ==========================================
    @Transactional
    public void cancelTransfer(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển với ID: " + id));

        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Chỉ có thể hủy phiếu đang ở trạng thái Chờ xuất hoặc Đang vận chuyển!");
        }

        // Không cần rollback tồn kho vì lúc duyệt xuất ta không trừ kho nữa
        transfer.setStatus(InventoryTransferStatus.CANCELLED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void changeDestination(Long id, Long newBranchId) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Không thể đổi chi nhánh cho phiếu đã chốt hoặc đã hủy!");
        }

        if (transfer.getFromBranch().getId().equals(newBranchId)) {
            throw new RuntimeException("Chi nhánh nhận không được trùng với chi nhánh xuất!");
        }

        Branch newBranch = branchRepo.findById(newBranchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh mới trên hệ thống"));

        transfer.setToBranch(newBranch);
        transferRepo.save(transfer);
    }

    @Transactional
    public void deleteTransfer(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển với ID: " + id));

        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xóa hoàn toàn phiếu đang ở trạng thái Chờ xuất (PENDING)!");
        }

        transferRepo.delete(transfer);
    }

    // ==========================================
    // HÀM BỔ TRỢ: CẬP NHẬT TỒN KHO AN TOÀN
    // ==========================================
    private void updateInventoryQuantity(Long branchId, Long variantId, Integer quantityChange) {
        Inventory inventory = inventoryRepo.findByBranchIdAndProductVariantId(branchId, variantId)
                .orElseGet(() -> {
                    Inventory newInv = new Inventory();
                    newInv.setBranch(branchRepo.findById(branchId).orElseThrow());
                    newInv.setProductVariant(variantRepo.findById(variantId).orElseThrow());
                    newInv.setQuantity(0);
                    return newInv;
                });

        int newQuantity = inventory.getQuantity() + quantityChange;

        if (newQuantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kho không đủ số lượng để trừ!");
        }

        inventory.setQuantity(newQuantity);
        inventoryRepo.save(inventory);
    }

    // ==========================================
    // HÀM CONVERT DTO (PRIVATE)
    // ==========================================
    private TransferResponse convertToResponse(InventoryTransfer t) {
        return new TransferResponse(
                t.getId(),
                t.getTransferCode(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getTransferDate(),
                t.getDeadline(),
                t.getFromBranch() != null ? t.getFromBranch().getName() : "N/A",
                t.getToBranch() != null ? t.getToBranch().getName() : "N/A",
                t.getTransporter(),
                t.getPriority(),
                t.getTotalQuantity(),
                t.getDetails() != null ? t.getDetails().size() : 0,
                t.getTotalValue()
        );
    }

    private TransferDetailResponse convertToDetailResponse(InventoryTransfer t) {
        return TransferDetailResponse.builder()
                .id(t.getId())
                .transferCode(t.getTransferCode())
                .transferType(t.getTransferType())
                .status(t.getStatus())
                .description(t.getDescription())
                .vehicle(t.getVehicle())
                .transporter(t.getTransporter())
                .dispatchOrder(t.getDispatchOrder())
                .referenceCode(t.getReferenceCode())
                .createdAt(t.getCreatedAt())
                .fromBranchName(t.getFromBranch() != null ? t.getFromBranch().getName() : "N/A")
                .toBranchName(t.getToBranch() != null ? t.getToBranch().getName() : "N/A")
                .totalQuantity(t.getTotalQuantity())
                .totalValue(t.getTotalValue())
                .items(t.getDetails().stream().map(d -> TransferDetailResponse.ItemDetail.builder()
                        .variantId(d.getProductVariant().getId())
                        .productName(d.getProductVariant().getProduct().getName())
                        .sku(d.getProductVariant().getSku())
                        .unit("Cái") // Hoặc lấy từ db
                        .quantityRequested(d.getQuantityRequested())
                        .quantityReal(d.getQuantityReal())
                        .note(d.getNote())
                        .build()).toList())
                .build();
    }
}