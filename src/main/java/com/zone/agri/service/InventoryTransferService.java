package com.zone.agri.service;

import com.zone.agri.dto.transfer.TransferRequest;
import com.zone.agri.dto.transfer.TransferItemRequest;
import com.zone.agri.dto.transfer.TransferResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        // 1. Map dữ liệu từ Request vào Entity
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
        BigDecimal totalValue = BigDecimal.ZERO; // Khởi tạo tổng tiền = 0

        // 2. Xử lý chi tiết mặt hàng
        for (TransferItemRequest itemReq : req.getItems()) {
            ProductVariant variant = variantRepo.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

            InventoryTransferDetail detail = InventoryTransferDetail.builder()
                    .inventoryTransfer(transfer)
                    .productVariant(variant)
                    .quantity(itemReq.getQuantity())
                    .quantityRequested(itemReq.getQuantity())
                    .quantityReal(0) // Mới tạo thì thực nhận = 0
                    .note(itemReq.getItemNote())
                    .build();

            details.add(detail);
            totalQty += itemReq.getQuantity();

            // Tính tổng giá trị phiếu điều chuyển (Lấy giá của Variant * Số lượng)
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
    // BƯỚC 2: DUYỆT & XUẤT KHO (SHIPPING)
    // ==========================================
    @Transactional
    public void approveAndShip(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xuất kho phiếu đang ở trạng thái Chờ Xuất!");
        }

        Long fromBranchId = transfer.getFromBranch().getId();

        // 1. Quét qua từng sản phẩm để TRỪ KHO XUẤT
        for (InventoryTransferDetail detail : transfer.getDetails()) {
            Long variantId = detail.getProductVariant().getId();
            Integer qtyToDeduct = detail.getQuantityRequested();

            // Lấy tồn kho hiện tại của sản phẩm ở Kho Xuất
            Inventory fromInventory = inventoryRepo.findByBranchIdAndProductVariantId(fromBranchId, variantId)
                    .orElseThrow(() -> new RuntimeException("Sản phẩm chưa có trong kho xuất!"));

            // Kiểm tra xem kho còn đủ hàng để xuất không
            if (fromInventory.getQuantity() < qtyToDeduct) {
                throw new RuntimeException("Lỗi: Số lượng tồn kho của [" + detail.getProductVariant().getSku() + "] không đủ để xuất!");
            }

            // Trừ số lượng và lưu lại
            fromInventory.setQuantity(fromInventory.getQuantity() - qtyToDeduct);
            inventoryRepo.save(fromInventory);
        }

        transfer.setStatus(InventoryTransferStatus.SHIPPING);
        transferRepo.save(transfer);
    }


    public InventoryTransfer getById(Long id) {
        return transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu"));
    }

    // 1. NGHIỆP VỤ: HỦY PHIẾU CHUYỂN
    @Transactional
    public void cancelTransfer(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển với ID: " + id));

        // Không cho phép hủy phiếu đã hoàn thành hoặc đã bị hủy trước đó
        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Chỉ có thể hủy phiếu đang ở trạng thái Chờ xuất hoặc Đang vận chuyển!");
        }

        // Nếu phiếu ĐANG VẬN CHUYỂN (tức là lúc trước bấm Duyệt đã bị trừ kho xuất rồi)
        // -> Bây giờ hủy thì phải TRẢ LẠI (Cộng vào) kho xuất
        if (transfer.getStatus() == InventoryTransferStatus.SHIPPING) {
            for (InventoryTransferDetail detail : transfer.getDetails()) {
                updateInventoryQuantity(
                        transfer.getFromBranch().getId(),
                        detail.getProductVariant().getId(),
                        detail.getQuantityRequested() // Cộng lại đúng số lượng đã yêu cầu/đã trừ
                );
            }
        }

        transfer.setStatus(InventoryTransferStatus.CANCELLED);
        transferRepo.save(transfer);
    }

    // 2. NGHIỆP VỤ: THAY ĐỔI CHI NHÁNH NHẬN (GIỮA ĐƯỜNG)
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

        // Lấy chi nhánh mới và cập nhật
        Branch newBranch = branchRepo.findById(newBranchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh mới trên hệ thống"));

        transfer.setToBranch(newBranch);
        transferRepo.save(transfer);
    }

    // 3. NGHIỆP VỤ: KIỂM ĐẾM & NHẬN HÀNG
    @Transactional
    public void receiveTransfer(Long id, List<Map<String, Object>> receivedItems) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException("Phiếu phải ở trạng thái Đang vận chuyển mới có thể nhận hàng!");
        }

        // Duyệt qua danh sách kiểm đếm gửi từ React lên
        for (Map<String, Object> itemData : receivedItems) {
            // Ép kiểu an toàn (tránh lỗi ClassCastException giữa Integer và Long khi Jackson parse JSON)
            Long variantId = ((Number) itemData.get("variantId")).longValue();
            Integer qtyReal = ((Number) itemData.get("quantityReal")).intValue();
            String note = itemData.get("note") != null ? itemData.get("note").toString() : "";

            // Tìm chi tiết sản phẩm tương ứng trong phiếu
            InventoryTransferDetail detail = transfer.getDetails().stream()
                    .filter(d -> d.getProductVariant().getId().equals(variantId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không thuộc phiếu điều chuyển này"));

            // Cập nhật số liệu THỰC NHẬN vào phiếu
            detail.setQuantityReal(qtyReal);
            detail.setNote(note);

            // CỘNG tồn kho cho Chi nhánh nhận (Kho đích) bằng số lượng thực tế nhận được
            if (qtyReal > 0) {
                updateInventoryQuantity(transfer.getToBranch().getId(), variantId, qtyReal);
            }
        }

        // Đổi trạng thái phiếu thành ĐÃ HOÀN THÀNH
        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);
    }

    // ==========================================
    // LẤY DANH SÁCH CHO TABLE
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
        return transferRepo.searchTransfers(keyword, status, pageable);
    }

    // 4. HÀM BỔ TRỢ: CỘNG/TRỪ TỒN KHO AN TOÀN
    private void updateInventoryQuantity(Long branchId, Long variantId, Integer quantityChange) {
        // Dùng cái hàm FindBy.. mà Huy đã viết sẵn trong InventoryRepository
        Inventory inventory = inventoryRepo.findByBranchIdAndProductVariantId(branchId, variantId)
                .orElseGet(() -> {
                    // Nếu chi nhánh này CHƯA TỪNG có sản phẩm này -> Tạo dòng tồn kho mới = 0
                    Inventory newInv = new Inventory();
                    newInv.setBranch(branchRepo.findById(branchId).orElseThrow());
                    newInv.setProductVariant(variantRepo.findById(variantId).orElseThrow());
                    newInv.setQuantity(0);
                    // Có thể set thêm các trường khác nếu entity Inventory yêu cầu (như min_stock, shelf_location...)
                    return newInv;
                });

        // Cập nhật số lượng mới
        inventory.setQuantity(inventory.getQuantity() + quantityChange);
        inventoryRepo.save(inventory);
    }
}