package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.CheckNoteRequest;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.inventory.InventoryNoteDetailResponse;
import com.zone.agri.dto.response.inventory.InventoryNoteResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryCheckWorkflowStatus;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryNoteService {
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryNoteDetailRepository inventoryNoteDetailRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final BackorderService backorderService;
    private final InventoryCheckGuardService inventoryCheckGuardService;
    private final com.zone.agri.common.WarehouseContext warehouseContext;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui lòng đăng nhập để thực hiện thao tác này");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Tài khoản không tồn tại"));
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities() != null
                && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private boolean isWarehouseBranch(Branch branch) {
        return branch != null
                && branch.getBranchType() != null
                && "WAREHOUSE".equalsIgnoreCase(branch.getBranchType());
    }

    // ==========================================
    // 1. TẠO LỆNH XUẤT (TRẠNG THÁI PENDING - CHƯA TRỪ KHO)
    // ==========================================
    @Transactional
    public InventoryNoteResponse createExportCommand(ExportNoteRequest request) {
        assertReturnExportRequest(request);
        inventoryCheckGuardService.assertNoOpenCheckForBranch(request.getBranchId(), "tạo lệnh xuất kho");
        InventoryNote note = new InventoryNote();
        note.setCode(request.getCode() != null ? request.getCode() : "LXK-" + System.currentTimeMillis());
        note.setType(InventoryNoteType.EXPORT);
        note.setStatus(InventoryNoteStatus.PENDING);
        note.setCreatedAt(LocalDateTime.now());
        note.setCreatedBy(getCurrentUser()); // Tự động gán người tạo từ Token

        updateNoteMetadata(note, request);
        
        // Process details and calculate total amount
        BigDecimal totalAmount = processNoteDetails(note, request.getDetails());
        note.setTotalAmount(totalAmount);
        note.setDebtAmount(BigDecimal.ZERO);
        note.setPaymentAmount(BigDecimal.ZERO);

        InventoryNote savedNote = inventoryNoteRepository.save(note);

        if (hasAuthority("EXPORT_APPROVE")) {
            return approveExportCommand(savedNote.getId());
        }

        return mapToResponse(savedNote);
    }

    @Transactional
    public InventoryNoteResponse approveExportCommand(Long id) {
        // freeze branch inventory when a stock count is open
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất ID: " + id));
        inventoryCheckGuardService.assertNoOpenCheckForBranch(note.getBranch().getId(), "duyệt lệnh xuất kho");
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt lệnh xuất đang chờ xử lý.");
        }
        note.setStatus(InventoryNoteStatus.APPROVED);
        note = inventoryNoteRepository.save(note);

        assertReturnExportNote(note);
        return completeExportCommand(id);
    }

    // ==========================================
    // 2. CHỐT PHIẾU XUẤT (CẬP NHẬT TỒN KHO - CÓ KIỂM ĐẾM)
    // ==========================================
    @Transactional
    public InventoryNoteResponse completeExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay lenh xuat ID: " + id));

        warehouseContext.assertAccess(note.getBranch().getId());
        inventoryCheckGuardService.assertNoOpenCheckForBranch(note.getBranch().getId(), "chot phieu xuat tra NCC");

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Lenh xuat tra nay da hoan thanh truoc do.");
        }

        if (note.getStatus() != InventoryNoteStatus.APPROVED && note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Phieu phai o trang thai Da duyet hoac Cho duyet moi co the hoan thanh xuat tra NCC.");
        }

        assertReturnExportNote(note);
        Branch sourceBranch = note.getBranch();

        for (InventoryNoteDetail detail : note.getDetails()) {
            int remainingToDeduct = Objects.requireNonNullElse(detail.getQuantityRequested(), 0);
            if (remainingToDeduct <= 0) continue;

            ProductVariant variant = detail.getProductVariant();
            String targetBatch = detail.getBatchNumber();
            if (targetBatch == null || targetBatch.isBlank()) {
                throw new BadRequestException("Xuat tra NCC bat buoc chi dinh dung so lo hang loi.");
            }

            List<Inventory> exactBatches = inventoryRepository.findExactBatchListByNumber(
                    sourceBranch.getId(),
                    variant.getId(),
                    targetBatch);
            for (Inventory batch : exactBatches) {
                if (remainingToDeduct <= 0) break;
                remainingToDeduct = deductDefectiveFromBatch(batch, remainingToDeduct, note);
            }

            if (remainingToDeduct > 0) {
                Long defectiveStock = inventoryRepository.sumDefectiveQuantityByBranchAndVariantAndBatch(
                        sourceBranch.getId(),
                        variant.getId(),
                        targetBatch);
                long available = defectiveStock != null ? defectiveStock : 0;

                throw new BadRequestException(String.format(
                        "San pham %s lo %s khong du hang loi de tra NCC. Yeu cau: %d, hang loi hien co: %d, con thieu: %d.",
                        variant.getSku(),
                        targetBatch,
                        detail.getQuantityRequested(),
                        available,
                        remainingToDeduct));
            }

            detail.setQuantityReal(detail.getQuantityRequested());
        }

        BigDecimal totalReturnAmount = note.getDetails().stream()
                .map(detail -> Objects.requireNonNullElse(detail.getPrice(), BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(Objects.requireNonNullElse(detail.getQuantityRequested(), 0))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        note.setTotalAmount(totalReturnAmount);
        note.setPaymentAmount(BigDecimal.ZERO);
        note.setDebtAmount(totalReturnAmount.negate());
        note.setStatus(InventoryNoteStatus.COMPLETED);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    private int deductDefectiveFromBatch(Inventory batch, int amount, InventoryNote note) {
        int availableDefective = Objects.requireNonNullElse(batch.getDefectiveQuantity(), 0);
        int deductDefective = Math.min(availableDefective, amount);
        if (deductDefective > 0) {
            batch.setDefectiveQuantity(availableDefective - deductDefective);
            inventoryRepository.save(batch);
            saveTransaction(batch, note, TransactionType.RETURN, -deductDefective, "Xuat tra NCC: " + note.getCode());
            amount -= deductDefective;
        }
        return amount;
    }
    private void saveTransaction(Inventory batch, InventoryNote note, TransactionType type, int change, String reason) {
        int q = Objects.requireNonNullElse(batch.getQuantity(), 0);
        int dq = Objects.requireNonNullElse(batch.getDefectiveQuantity(), 0);
        
        transactionRepository.save(InventoryTransaction.builder()
                .type(type)
                .quantityChange(change)
                .newBalance(q + dq)
                .referenceCode(note.getCode())
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .inventory(batch)
                .inventoryNote(note)
                .build());
    }

    // ==========================================
    // 2b. TẠO PHIẾU XUẤT TRẢ NCC TỪ PHIẾU NHẬP (Quy trình 3 – Hướng 1)
    // ==========================================

    /**
     * Tạo Phiếu xuất trả NCC trực tiếp từ một Phiếu nhập đã COMPLETED.
     * <p>
     * Quy tắc:
     * - Chỉ hoạt động khi GR ở trạng thái COMPLETED và có ít nhất 1 dòng hàng lỗi (quantityRejected > 0).
     * - Tự động điền NCC và danh sách hàng lỗi từ GR.
     * - Đơn giá trả bị khóa bằng đúng đơn giá nhập của GR (chống gian lận).
     * - Số lượng trả mặc định = số lượng lỗi trên GR, có thể giảm xuống nhưng không tăng quá.
     */
    @Transactional
    public InventoryNoteResponse createReturnFromGR(Long grId) {
        InventoryNote gr = inventoryNoteRepository.findByIdWithDetails(grId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy Phiếu nhập ID: " + grId));

        if (gr.getType() != InventoryNoteType.IMPORT) {
            throw new BadRequestException("Chỉ có thể tạo phiếu xuất trả từ Phiếu nhập kho (IMPORT).");
        }
        if (gr.getStatus() != InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phiếu nhập phải ở trạng thái COMPLETED mới có thể tạo phiếu xuất trả.");
        }
        if (gr.getSupplier() == null) {
            throw new BadRequestException("Phiếu nhập không có thông tin nhà cung cấp.");
        }

        // Lấy các dòng có hàng lỗi
        List<InventoryNoteDetail> defectiveDetails = gr.getDetails().stream()
                .filter(d -> d.getQuantityRejected() != null && d.getQuantityRejected() > 0)
                .collect(java.util.stream.Collectors.toList());

        if (defectiveDetails.isEmpty()) {
            throw new BadRequestException("Phiếu nhập không có hàng lỗi nào để tạo phiếu xuất trả.");
        }

        // Kiểm tra tồn kho lỗi thực tế (đề phòng đã xuất trả trước đó)
        for (InventoryNoteDetail d : defectiveDetails) {
            if (d.getBatchNumber() == null || d.getBatchNumber().isBlank()) {
                d.setQuantityRejected(0);
                continue;
            }
            Long defectiveStock = inventoryRepository.sumDefectiveQuantityByBranchAndVariantAndBatch(
                    gr.getBranch().getId(), d.getProductVariant().getId(), d.getBatchNumber());
            if (defectiveStock == null || defectiveStock < d.getQuantityRejected()) {
                // Không throw, chỉ cảnh báo bằng cách điều chỉnh số lượng còn lại
                d.setQuantityRejected(defectiveStock != null ? defectiveStock.intValue() : 0);
            }
        }

        // Lọc lại sau khi điều chỉnh
        defectiveDetails = defectiveDetails.stream()
                .filter(d -> d.getQuantityRejected() != null && d.getQuantityRejected() > 0)
                .collect(java.util.stream.Collectors.toList());

        if (defectiveDetails.isEmpty()) {
            throw new BadRequestException("Tồn kho lỗi của NCC này đã hết hoặc đã xuất trả hết trước đó.");
        }

        // Tạo phiếu xuất trả
        String returnCode = "PXT-" + System.currentTimeMillis();
        InventoryNote returnNote = new InventoryNote();
        returnNote.setCode(returnCode);
        returnNote.setType(InventoryNoteType.EXPORT);
        returnNote.setStatus(InventoryNoteStatus.PENDING);
        returnNote.setCreatedAt(LocalDateTime.now());
        returnNote.setCreatedBy(getCurrentUser());
        returnNote.setBranch(gr.getBranch());
        returnNote.setSupplier(gr.getSupplier());
        returnNote.setPartnerBranch(null);
        // Đánh dấu là phiếu RETURN để logic xuất kho biết dùng kho lỗi
        returnNote.setReason("RETURN | Tạo từ Phiếu nhập: " + gr.getCode() + " | NCC: " + gr.getSupplier().getName());
        returnNote.setNote("Xuất trả hàng lỗi từ phiếu nhập " + gr.getCode());

        BigDecimal totalReturn = BigDecimal.ZERO;
        List<InventoryNoteDetail> returnDetails = new ArrayList<>();

        for (InventoryNoteDetail grDetail : defectiveDetails) {
            // ĐƠN GIÁ KHÓA CỨNG = đơn giá nhập của GR (không cho người dùng sửa)
            BigDecimal lockedPrice = Objects.requireNonNullElse(grDetail.getPrice(), BigDecimal.ZERO);
            int returnQty = grDetail.getQuantityRejected();

            InventoryNoteDetail returnDetail = InventoryNoteDetail.builder()
                    .inventoryNote(returnNote)
                    .productVariant(grDetail.getProductVariant())
                    .quantityRequested(returnQty)
                    .quantityReal(returnQty)
                    .quantity(returnQty)
                    .price(lockedPrice)
                    .batchNumber(grDetail.getBatchNumber())
                    .expiryDate(grDetail.getExpiryDate())
                    .note("Lô hàng lỗi từ GR " + gr.getCode())
                    .build();

            returnDetails.add(returnDetail);
            totalReturn = totalReturn.add(lockedPrice.multiply(BigDecimal.valueOf(returnQty)));
        }

        returnNote.setDetails(returnDetails);
        returnNote.setTotalAmount(totalReturn);
        // Phiếu xuất trả tạo ra một khoản ghi nhận âm (credit) với NCC
        // debtAmount âm = giảm nợ NCC
        returnNote.setDebtAmount(totalReturn.negate());
        returnNote.setPaymentAmount(BigDecimal.ZERO);

        return mapToResponse(inventoryNoteRepository.save(returnNote));
    }

    // ==========================================
    // 3. KIỂM KHO (INVENTORY CHECK)
    // ==========================================

    @Transactional
    public InventoryNoteResponse createCheckCommand(CheckNoteRequest request) {
        // branch access and freeze validation are applied before snapshot starts
        warehouseContext.assertAccess(request.getBranchId());
        inventoryCheckGuardService.assertNoOpenCheckForBranch(request.getBranchId(), "khởi tạo phiếu kiểm kê");
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh ID: " + request.getBranchId()));

        InventoryNote note = new InventoryNote();
        // Cập nhật prefix mã chứng từ thành PKK
        note.setCode(request.getCode() != null ? request.getCode() : "PKK-" + System.currentTimeMillis());
        note.setType(InventoryNoteType.CHECK);
        note.setStatus(InventoryNoteStatus.PENDING);
        note.setCreatedAt(LocalDateTime.now());
        note.setBranch(branch);
        note.setNote(request.getNote());
        
        // Cập nhật thông tin kiểm kho mới
        note.setCheckType(request.getType());
        note.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : LocalDateTime.now());
        note.setCheckedBy(request.getCheckedBy());
        
        // Tự động gán người tạo từ Token (Luôn ưu tiên User thực tế đang login)
        note.setCreatedBy(getCurrentUser());

        List<InventoryNoteDetail> details = new ArrayList<>();
        for (CheckNoteRequest.CheckNoteDetailRequest detailReq : request.getDetails()) {
            ProductVariant variant = productVariantRepository.findById(detailReq.getProductVariantId())
                    .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại: " + detailReq.getProductVariantId()));

            // Nếu FE gửi systemQuantity (số lượng thấy trên màn hình lúc đó) thì dùng luôn, 
            // ngược lại mới query DB để lấy tồn hiện tại của lô.
            Integer systemQty;
            if (detailReq.getSystemQuantity() != null) {
                systemQty = detailReq.getSystemQuantity();
            } else if (detailReq.getQuantity() != null) {
                systemQty = detailReq.getQuantity();
            } else {
                Optional<Inventory> existingBatch = inventoryRepository.findExactBatch(branch, variant, detailReq.getBatchNumber(), detailReq.getImportPrice());
                systemQty = existingBatch.map(Inventory::getQuantity).orElse(0);
            }

            details.add(InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantity(systemQty) // Số lượng hệ thống (snapshot)
                    .quantityReal(detailReq.getQuantityReal()) // Số lượng thực tế kiểm thấy (tổng)
                    .quantityRejected(detailReq.getQuantityRejected()) // Số lượng hàng lỗi
                    .batchNumber(detailReq.getBatchNumber())
                    .price(detailReq.getImportPrice())
                    .note(detailReq.getNote())
                    .build());
        }
        note.setDetails(details);
        note.setCheckWorkflowStatus(resolveCheckWorkflowStatus(request.getDetails(), false));
        note.setTotalAmount(BigDecimal.ZERO);
        note.setPaymentAmount(BigDecimal.ZERO);
        note.setDebtAmount(BigDecimal.ZERO);

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse completeCheckCommand(Long id) {
        return approveCheckAdjustment(id);
    }

    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllCheckCommands() {
        return getNotesByTypeAndStatus(InventoryNoteType.CHECK, InventoryNoteStatus.PENDING);
    }

    @Transactional
    public InventoryNoteResponse updateCheckCommand(Long id, CheckNoteRequest request) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay lenh kiem kho."));
        warehouseContext.assertAccess(note.getBranch().getId());
        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("Phieu nay khong phai phieu kiem ke.");
        }
        if (note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.WAITING_FOR_ADJUSTMENT_APPROVAL
                || note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.COUNTING_COMPLETED
                || note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phieu kiem ke da gui duyet hoac hoan tat, khong the chinh sua.");
        }
        if (!Objects.equals(note.getBranch().getId(), request.getBranchId())) {
            throw new BadRequestException("Khong the thay doi chi nhanh sau khi da tao phieu kiem ke.");
        }
        Branch branch = note.getBranch();
        note.setBranch(branch);
        note.setNote(request.getNote());
        note.setCheckType(request.getType());
        note.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : LocalDateTime.now());
        note.setCheckedBy(request.getCheckedBy());
        note.setCreatedBy(getCurrentUser());
        note.getDetails().clear();
        inventoryNoteDetailRepository.flush();
        List<InventoryNoteDetail> details = new ArrayList<>();
        for (CheckNoteRequest.CheckNoteDetailRequest detailReq : request.getDetails()) {
            ProductVariant variant = productVariantRepository.findById(detailReq.getProductVariantId())
                    .orElseThrow(() -> new NotFoundException("S???n ph???m kh??ng t???n t???i: " + detailReq.getProductVariantId()));
            Integer systemQty;
            if (detailReq.getSystemQuantity() != null) {
                systemQty = detailReq.getSystemQuantity();
            } else if (detailReq.getQuantity() != null) {
                systemQty = detailReq.getQuantity();
            } else {
                Optional<Inventory> existingBatch = inventoryRepository.findExactBatch(branch, variant, detailReq.getBatchNumber(), detailReq.getImportPrice());
                systemQty = existingBatch.map(Inventory::getQuantity).orElse(0);
            }
            details.add(InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantity(systemQty)
                    .quantityReal(detailReq.getQuantityReal())
                    .quantityRejected(detailReq.getQuantityRejected())
                    .batchNumber(detailReq.getBatchNumber())
                    .price(detailReq.getImportPrice())
                    .note(detailReq.getNote())
                    .build());
        }
        note.getDetails().addAll(details);
        note.setStatus(InventoryNoteStatus.PENDING);
        note.setCheckWorkflowStatus(resolveCheckWorkflowStatus(request.getDetails(), false));
        note.setCheckSubmittedAt(null);
        note.setCheckApprovedAt(null);
        note.setCheckApprovedBy(null);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllCheckNotes() {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes = (warehouseId == null)
                ? inventoryNoteRepository.findAllByTypeWithPartners(InventoryNoteType.CHECK)
                : inventoryNoteRepository.findAllByTypeAndBranchWithPartners(InventoryNoteType.CHECK, warehouseId);

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getCheckCommandById(Long id) {
        return inventoryNoteRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho."));
    }

    private List<InventoryNoteResponse> getNotesByTypeAndStatus(InventoryNoteType type, InventoryNoteStatus status) {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes = (warehouseId == null)
                ? inventoryNoteRepository.findAllByTypeAndStatusWithPartners(type, status)
                : inventoryNoteRepository.findAllByTypeAndStatusAndBranchWithPartners(type, status, warehouseId);

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ==========================================
    // CẬP NHẬT LỆNH XUẤT (CHỈ ÁP DỤNG KHI STATUS = PENDING)
    // ==========================================
    @Transactional
    public InventoryNoteResponse updateExportCommand(Long id, ExportNoteRequest request) {
        assertReturnExportRequest(request);
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất."));

        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể chỉnh sửa lệnh xuất đang chờ xử lý.");
        }

        updateNoteMetadata(note, request);

        // Clear and update details
        note.getDetails().clear();
        inventoryNoteDetailRepository.flush();

        BigDecimal totalAmount = processNoteDetails(note, request.getDetails());
        note.setTotalAmount(totalAmount);

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    // --- Helpers for processing ---

    private void assertReturnExportRequest(ExportNoteRequest request) {
        if (!"RETURN".equalsIgnoreCase(Objects.requireNonNullElse(request.getExportType(), ""))) {
            throw new BadRequestException("Module xuat kho hien chi ho tro xuat tra nha cung cap (RETURN).");
        }
        if (request.getSupplierId() == null) {
            throw new BadRequestException("Xuat tra NCC bat buoc chon nha cung cap.");
        }
        if (request.getTargetBranchId() != null) {
            throw new BadRequestException("Xuat tra NCC khong su dung kho nhan noi bo.");
        }
        if (request.getDetails() == null || request.getDetails().isEmpty()) {
            throw new BadRequestException("Lenh xuat tra phai co it nhat mot dong hang loi.");
        }
        for (ExportNoteRequest.ExportNoteDetailRequest detail : request.getDetails()) {
            if (detail.getBatchNumber() == null || detail.getBatchNumber().isBlank()) {
                throw new BadRequestException("Moi dong xuat tra NCC bat buoc co so lo hang loi.");
            }
        }
    }

    private void assertReturnExportNote(InventoryNote note) {
        if (note.getType() != InventoryNoteType.EXPORT || note.getSupplier() == null) {
            throw new BadRequestException("Module xuat kho hien chi cho phep xuat tra nha cung cap.");
        }
        if (note.getDetails() == null || note.getDetails().isEmpty()) {
            throw new BadRequestException("Phieu xuat tra NCC chua co hang loi.");
        }
    }

    private void updateNoteMetadata(InventoryNote note, ExportNoteRequest request) {
        note.setDeliverer(request.getSpecificReceiver());
        note.setNote(request.getNote());
        note.setShippingAddress(request.getShippingAddress()); // Lưu địa chỉ tách biệt
        
        String fullReason = String.format("Loại: %s | Ref: %s | Đ/c: %s | Lydo: %s",
                request.getExportType(), request.getReferenceCode(), request.getShippingAddress(), request.getNote());
        note.setReason(fullReason);

        if (request.getExpectedDate() != null) {
            note.setEntryDate(request.getExpectedDate().atStartOfDay());
        }

        Branch sourceBranch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kho xuất"));
        note.setBranch(sourceBranch);

        if (request.getCreatedById() != null) {
            userRepository.findById(request.getCreatedById()).ifPresent(note::setCreatedBy);
        }

        // XỬ LÝ ĐỐI TÁC: NHÀ CUNG CẤP HOẶC CHI NHÁNH NHẬN
        if (request.getSupplierId() != null || "RETURN".equals(request.getExportType())) {
            if (!isWarehouseBranch(sourceBranch)) {
                throw new BadRequestException(
                        "Chỉ các chi nhánh loại kho mới được phép thực hiện nghiệp vụ xuất trả nhà cung cấp.");
            }
            
            if (request.getSupplierId() != null) {
                Supplier supplier = supplierRepository.findById(request.getSupplierId())
                        .orElseThrow(() -> new NotFoundException("Khong tim thay nha cung cap ID: " + request.getSupplierId()));
                note.setSupplier(supplier);
            } else if (request.getDetails() != null && !request.getDetails().isEmpty()) {
                // TỰ ĐỘNG TRUY VẾT NCC TỪ LÔ HÀNG ĐẦU TIÊN NẾU FE KHÔNG GỬI SUPPLIER_ID
                String firstBatch = request.getDetails().get(0).getBatchNumber();
                String firstSku = productVariantRepository.findById(request.getDetails().get(0).getProductVariantId())
                        .map(ProductVariant::getSku).orElse(null);
                
                if (firstBatch != null && firstSku != null) {
                    List<InventoryNoteDetail> importDetails = inventoryNoteDetailRepository.findOriginalImportDetailBySkuAndBatch(firstSku, firstBatch);
                    if (!importDetails.isEmpty()) {
                        note.setSupplier(importDetails.get(0).getInventoryNote().getSupplier());
                    }
                }
            }
            
            note.setPartnerBranch(null);
        } else if (request.getTargetBranchId() != null) {
            note.setPartnerBranch(branchRepository.findById(request.getTargetBranchId()).orElse(null));
            note.setSupplier(null);
        } else {
            // Nếu không có cả 2 thì xóa trắng đối tác (ví dụ xuất hủy)
            note.setSupplier(null);
            note.setPartnerBranch(null);
        }
    }

    private BigDecimal processNoteDetails(InventoryNote note, List<ExportNoteRequest.ExportNoteDetailRequest> detailRequests) {
        if (detailRequests == null) return BigDecimal.ZERO;
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<InventoryNoteDetail> details = new ArrayList<>();

        for (ExportNoteRequest.ExportNoteDetailRequest reqDetail : detailRequests) {
            ProductVariant variant = productVariantRepository.findById(reqDetail.getProductVariantId())
                    .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại ID: " + reqDetail.getProductVariantId()));

            // Check stock availability
            boolean isReturn = note.getSupplier() != null || 
                              (note.getReason() != null && (note.getReason().contains("RETURN") || note.getReason().contains("Trả NCC")));
            
            int checkStock;
            String errorPool;
            String batchNum = reqDetail.getBatchNumber();
            List<InventoryNoteDetail> originalImportDetails = Collections.emptyList();
            
            if (isReturn) {
                if (batchNum == null || batchNum.isBlank()) {
                    throw new BadRequestException("Xuất trả nhà cung cấp bắt buộc chọn đúng lô hàng lỗi.");
                }

                if (note.getSupplier() == null) {
                    throw new BadRequestException("Phiếu xuất trả nhà cung cấp thiếu thông tin nhà cung cấp.");
                }

                originalImportDetails = inventoryNoteDetailRepository.findOriginalImportDetail(
                        note.getSupplier().getId(),
                        variant.getSku(),
                        batchNum
                );

                boolean matchesOriginalWarehouse = originalImportDetails.stream()
                        .anyMatch(d -> d.getInventoryNote() != null
                                && d.getInventoryNote().getBranch() != null
                                && Objects.equals(d.getInventoryNote().getBranch().getId(), note.getBranch().getId()));

                if (!matchesOriginalWarehouse) {
                    throw new BadRequestException(String.format(
                            "Lô %s của sản phẩm %s không thuộc đúng nhà cung cấp hoặc đúng kho nhập hiện tại.",
                            batchNum,
                            variant.getSku()
                    ));
                }

                Long defectiveStockLong;
                if (batchNum != null && !batchNum.isBlank()) {
                    defectiveStockLong = inventoryRepository.sumDefectiveQuantityByBranchAndVariantAndBatch(note.getBranch().getId(), variant.getId(), batchNum);
                    errorPool = "kho lỗi (lô " + batchNum + ")";
                } else {
                    defectiveStockLong = inventoryRepository.sumDefectiveQuantityByBranchAndVariant(note.getBranch().getId(), variant.getId());
                    errorPool = "kho lỗi (tổng)";
                }
                checkStock = defectiveStockLong != null ? defectiveStockLong.intValue() : 0;
            } else {
                Long normalStockLong = inventoryRepository.sumQuantityByBranchAndVariant(note.getBranch().getId(), variant.getId());
                checkStock = normalStockLong != null ? normalStockLong.intValue() : 0;
                errorPool = "kho chính";
            }
            
            if (checkStock < reqDetail.getRequestedQuantity()) {
                // Lấy chi tiết tất cả các dòng có hàng lỗi của biến thể này tại chi nhánh
                List<Inventory> allDefectiveInBranch = inventoryRepository.findAllByBranchIdAndDefectiveQuantityGreaterThan(note.getBranch().getId(), 0);
                String availableBatches = allDefectiveInBranch.stream()
                        .filter(i -> i.getProductVariant().getId().equals(variant.getId()))
                        .map(i -> (i.getBatchNumber() == null ? "TRỐNG" : i.getBatchNumber()) + ":" + i.getDefectiveQuantity())
                        .collect(Collectors.joining(", "));
                
                if (availableBatches.isEmpty()) availableBatches = "Không có lô nào có hàng lỗi";

                throw new BadRequestException(String.format("Sản phẩm %s: Lô %s chỉ còn %d lỗi. Danh sách lô đang có hàng lỗi thực tế trong DB: [%s]. (Yêu cầu: %d)",
                        variant.getSku(), (batchNum == null ? "TRỐNG" : batchNum), checkStock, availableBatches, reqDetail.getRequestedQuantity()));
            }

            BigDecimal lockedPrice = reqDetail.getPrice() != null ? reqDetail.getPrice() : BigDecimal.ZERO;
            if (isReturn && !originalImportDetails.isEmpty()) {
                lockedPrice = Objects.requireNonNullElse(originalImportDetails.get(0).getPrice(), BigDecimal.ZERO);
            }

            InventoryNoteDetail detail = InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantityRequested(reqDetail.getRequestedQuantity())
                    .quantityReal(reqDetail.getRequestedQuantity())
                    .quantity(reqDetail.getPlannedQuantity()) // Lưu số lượng yêu cầu ban đầu vào trường quantity của detail
                    .quantityRejected(reqDetail.getDefectiveQuantity()) // Lưu số lượng lỗi hiện có
                    .batchNumber(reqDetail.getBatchNumber())
                    .price(lockedPrice)
                    .note(reqDetail.getNote())
                    .build();

            // Xử lý hạn dùng nếu có gửi lên
            if (reqDetail.getExpiryDate() != null && !reqDetail.getExpiryDate().isBlank()) {
                try {
                    detail.setExpiryDate(LocalDate.parse(reqDetail.getExpiryDate()).atStartOfDay());
                } catch (Exception e) { /* Bỏ qua lỗi format ngày */ }
            }

            details.add(detail);
            
            if (detail.getPrice() != null && detail.getQuantityRequested() != null) {
                totalAmount = totalAmount.add(detail.getPrice().multiply(new BigDecimal(detail.getQuantityRequested())));
            }
        }
        
        if (note.getDetails() == null) {
            note.setDetails(details);
        } else {
            note.getDetails().addAll(details);
        }
        
        return totalAmount;
    }

    // ==========================================
    // 3. LẤY DANH SÁCH
    // ==========================================
    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllExportCommands() {
        return getNotesByStatuses(InventoryNoteType.EXPORT, Arrays.asList(
                InventoryNoteStatus.PENDING,
                InventoryNoteStatus.APPROVED,
                InventoryNoteStatus.COMPLETED,
                InventoryNoteStatus.REJECTED,
                InventoryNoteStatus.CANCELLED
        ));
    }

    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllExportReceipts() {
        return getNotesByStatuses(InventoryNoteType.EXPORT, Collections.singletonList(InventoryNoteStatus.COMPLETED));
    }

    private List<InventoryNoteResponse> getNotesByStatuses(InventoryNoteType type, Collection<InventoryNoteStatus> statuses) {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes = (warehouseId == null)
                ? inventoryNoteRepository.findAllByTypeAndStatusInWithPartners(type, statuses)
                : inventoryNoteRepository.findAllByTypeAndStatusInAndBranchWithPartners(type, statuses, warehouseId);

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ==========================================
    // 4. XÓA PHIẾU
    // ==========================================
    @Transactional
    public void deleteExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu xuất."));
        
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chỉ được phép xóa phiếu ở trạng thái CHỜ DUYỆT. Các phiếu đã duyệt hoặc đã xuất kho không thể xóa.");
        }
        inventoryNoteRepository.delete(note);
    }

    @Transactional
    public void deleteCheckNote(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kiểm kho."));

        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("Đây không phải là phiếu kiểm kho.");
        }

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phiếu kiểm kho đã hoàn thành không được phép xóa.");
        }
        inventoryNoteRepository.delete(note);
    }

    @Transactional
    public InventoryNoteResponse submitCheckForApproval(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y lá»‡nh kiá»ƒm kho."));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("Phiếu này không phải phiếu kiểm kê.");
        }
        if (note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.COUNTING_COMPLETED) {
            throw new BadRequestException("Phiếu kiểm kê đã hoàn tất.");
        }
        if (note.getDetails() == null || note.getDetails().isEmpty()) {
            throw new BadRequestException("Phiếu kiểm kê chưa có dữ liệu snapshot.");
        }

        boolean hasActualCount = note.getDetails().stream().allMatch(detail -> detail.getQuantityReal() != null);
        if (!hasActualCount) {
            throw new BadRequestException("Vui lòng nhập đủ số lượng thực tế trước khi gửi duyệt.");
        }

        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.WAITING_FOR_ADJUSTMENT_APPROVAL);
        note.setCheckSubmittedAt(LocalDateTime.now());
        note.setStatus(InventoryNoteStatus.APPROVED);

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse approveCheckAdjustment(Long id) {
        User approver = getCurrentUser();
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y lá»‡nh kiá»ƒm kho ID: " + id));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.COUNTING_COMPLETED) {
            throw new BadRequestException("Lệnh kiểm kho này đã hoàn thành.");
        }
        if (note.getCheckWorkflowStatus() != InventoryCheckWorkflowStatus.WAITING_FOR_ADJUSTMENT_APPROVAL) {
            throw new BadRequestException("Phiếu kiểm kê phải ở trạng thái chờ duyệt cân bằng.");
        }

        Branch branch = note.getBranch();

        for (InventoryNoteDetail detail : note.getDetails()) {
            ProductVariant variant = detail.getProductVariant();
            String batchNum = detail.getBatchNumber();
            BigDecimal importPrice = detail.getPrice();
            int actualQty = Objects.requireNonNullElse(detail.getQuantityReal(), 0);
            int actualDefectiveQty = Objects.requireNonNullElse(detail.getQuantityRejected(), 0);

            Optional<Inventory> batchOpt = inventoryRepository.findExactBatchWithLock(branch, variant, batchNum, importPrice);

            if (batchOpt.isEmpty()) {
                Inventory newBatch = Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .batchNumber(batchNum)
                        .quantity(actualQty)
                        .defectiveQuantity(actualDefectiveQty)
                        .importPrice(importPrice != null ? importPrice : BigDecimal.ZERO)
                        .lastReceiptDate(LocalDateTime.now())
                        .build();
                newBatch = inventoryRepository.save(newBatch);

                transactionRepository.save(InventoryTransaction.builder()
                        .type(TransactionType.ADJUSTMENT)
                        .quantityChange(actualQty + actualDefectiveQty)
                        .newBalance(actualQty + actualDefectiveQty)
                        .referenceCode(note.getCode())
                        .reason("Kiểm kho: Tạo mới lô hàng (Phiếu: " + note.getCode() + ")")
                        .createdAt(LocalDateTime.now())
                        .inventory(newBatch)
                        .inventoryNote(note)
                        .build());

                if (actualQty > 0) {
                    backorderService.fulfillBackordersOnStockReceive(branch.getId(), variant.getId(), actualQty);
                }
            } else {
                Inventory batch = batchOpt.get();
                int systemQty = Objects.requireNonNullElse(batch.getQuantity(), 0);
                int systemDefectiveQty = Objects.requireNonNullElse(batch.getDefectiveQuantity(), 0);

                int discrepancyNormal = actualQty - systemQty;
                int discrepancyDefective = actualDefectiveQty - systemDefectiveQty;

                if (discrepancyNormal != 0 || discrepancyDefective != 0) {
                    batch.setQuantity(actualQty);
                    batch.setDefectiveQuantity(actualDefectiveQty);
                    inventoryRepository.save(batch);

                    transactionRepository.save(InventoryTransaction.builder()
                            .type(TransactionType.ADJUSTMENT)
                            .quantityChange(discrepancyNormal + discrepancyDefective)
                            .newBalance(actualQty + actualDefectiveQty)
                            .referenceCode(note.getCode())
                            .reason("Kiểm kho: Điều chỉnh chênh lệch (Phiếu: " + note.getCode() + ")")
                            .createdAt(LocalDateTime.now())
                            .inventory(batch)
                            .inventoryNote(note)
                            .build());

                    if (discrepancyNormal > 0) {
                        backorderService.fulfillBackordersOnStockReceive(branch.getId(), variant.getId(), discrepancyNormal);
                    }
                }
            }
        }

        note.setStatus(InventoryNoteStatus.COMPLETED);
        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.COUNTING_COMPLETED);
        note.setCheckApprovedAt(LocalDateTime.now());
        note.setCheckApprovedBy(approver);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    private InventoryCheckWorkflowStatus resolveCheckWorkflowStatus(
            List<CheckNoteRequest.CheckNoteDetailRequest> details,
            boolean submittedForApproval
    ) {
        if (submittedForApproval) {
            return InventoryCheckWorkflowStatus.WAITING_FOR_ADJUSTMENT_APPROVAL;
        }
        boolean hasActualCount = details != null
                && details.stream().anyMatch(detail -> detail.getQuantityReal() != null);
        return hasActualCount
                ? InventoryCheckWorkflowStatus.COUNTING_IN_PROGRESS
                : InventoryCheckWorkflowStatus.COUNTING_INIT;
    }

    // ==========================================
    // MAPPER
    // ==========================================
    private InventoryNoteResponse mapToResponse(InventoryNote entity) {
        if (entity == null) return null;

        String partnerName = "N/A";
        if (entity.getPartnerBranch() != null) {
            partnerName = "[Nội bộ] " + entity.getPartnerBranch().getName();
        } else if (entity.getSupplier() != null) {
            partnerName = "[Trả NCC] " + entity.getSupplier().getName();
        } else if (entity.getDeliverer() != null && !entity.getDeliverer().isEmpty()) {
            partnerName = entity.getDeliverer();
        }

        String fullName = (entity.getCreatedBy() != null) ? entity.getCreatedBy().getFullName() : "Hệ thống";

        return InventoryNoteResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .noteType(entity.getType() != null ? entity.getType().name() : "EXPORT")
                .exportType(entity.getPartnerBranch() != null ? "INTERNAL" : 
                           entity.getSupplier() != null ? "RETURN" : "EXPORT")
                .status(entity.getStatus() != null ? entity.getStatus().name() : "PENDING")
                .reason(entity.getReason())
                .note(entity.getNote())
                .deliverer(entity.getDeliverer())
                .totalAmount(Objects.requireNonNullElse(entity.getTotalAmount(), BigDecimal.ZERO))
                .paymentAmount(Objects.requireNonNullElse(entity.getPaymentAmount(), BigDecimal.ZERO))
                .debtAmount(Objects.requireNonNullElse(entity.getDebtAmount(), BigDecimal.ZERO))
                // Các trường thông tin kiểm kho mới
                .type(entity.getCheckType())
                .checkDate(entity.getCheckDate())
                .checkedBy(entity.getCheckedBy())
                .checkWorkflowStatus(entity.getCheckWorkflowStatus() != null ? entity.getCheckWorkflowStatus().name() : null)
                .checkSubmittedAt(entity.getCheckSubmittedAt())
                .checkApprovedAt(entity.getCheckApprovedAt())
                .checkApprovedByName(entity.getCheckApprovedBy() != null ? entity.getCheckApprovedBy().getFullName() : null)
                .createdAt(entity.getCreatedAt())
                .entryDate(entity.getEntryDate() != null ? entity.getEntryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                .branchId(entity.getBranch() != null ? entity.getBranch().getId() : null)
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : "N/A")
                .partnerBranchId(entity.getPartnerBranch() != null ? entity.getPartnerBranch().getId() : null)
                .partnerBranchName(entity.getPartnerBranch() != null ? entity.getPartnerBranch().getName() : null)
                .supplierId(entity.getSupplier() != null ? entity.getSupplier().getId() : null)
                .supplierName(entity.getSupplier() != null ? entity.getSupplier().getName() : null)
                .supplierCode(entity.getSupplier() != null ? entity.getSupplier().getCode() : null)
                .displayPartnerName(partnerName)
                .creatorName(fullName)
                .createdByName(fullName)
                .shippingAddress(entity.getShippingAddress()) // Bổ sung địa chỉ
                .details(entity.getDetails() != null ? entity.getDetails().stream().map(this::mapDetailToResponse).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private InventoryNoteDetailResponse mapDetailToResponse(InventoryNoteDetail d) {
        ProductVariant variant = d.getProductVariant();
        return InventoryNoteDetailResponse.builder()
                .id(d.getId())
                .productVariantId(variant != null ? variant.getId() : null)
                .sku(variant != null ? variant.getSku() : "N/A")
                .productName(variant != null && variant.getProduct() != null ? variant.getProduct().getName() : "N/A")
                .name(variant != null ? variant.getCustomSpecs() : "N/A")
                .unit("Cái") // Fallback hardcoded unit
                .quantity(d.getQuantity())           // Hệ thống (Kiểm kho)
                .systemQuantity(d.getQuantity())     // Alias cho FE hiển thị
                .quantityRequested(d.getQuantityRequested()) // Yêu cầu (Expected)
                .quantityReal(d.getQuantityReal())           // Thực tế (Actual)
                .quantityAccepted(d.getQuantityAccepted())   // Đạt
                .quantityRejected(d.getQuantityRejected())   // Lỗi
                .batchNumber(d.getBatchNumber())
                .price(Objects.requireNonNullElse(d.getPrice(), BigDecimal.ZERO))
                .imageUrl(variant != null ? variant.getImageUrl() : null)
                .note(d.getNote())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getExportCommandById(Long id) {
        return inventoryNoteRepository.findByIdWithDetails(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất."));
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getCheckCommandByCode(String code) {
        return inventoryNoteRepository.findByCodeWithDetails(code)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho với mã: " + code));
    }
}


