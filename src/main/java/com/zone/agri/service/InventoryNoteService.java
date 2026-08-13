package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.CheckNoteRequest;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.inventory.InventoryNoteDetailResponse;
import com.zone.agri.dto.response.inventory.InventoryNoteResponse;
import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryCheckScopeType;
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
import java.time.OffsetDateTime;
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

    private boolean canApproveAcrossBranches(String approvalAuthority) {
        return hasAuthority(approvalAuthority)
                && RoleUtils.hasAdminLikeAuthority(AuthUtils.getAuthorities());
    }

    private Long resolveExportListBranchId() {
        return canApproveAcrossBranches("EXPORT_APPROVE") ? null : warehouseContext.resolveWarehouseId();
    }

    private void assertExportReadOrApproveAccess(InventoryNote note) {
        if (!canApproveAcrossBranches("EXPORT_APPROVE")) {
            warehouseContext.assertAccess(note.getBranch().getId());
        }
    }

    private Long resolveCheckListBranchId() {
        return canApproveAcrossBranches("INVENTORY_CHECK_APPROVE") ? null : warehouseContext.resolveWarehouseId();
    }

    private void assertCheckReadOrApproveAccess(InventoryNote note) {
        if (!canApproveAcrossBranches("INVENTORY_CHECK_APPROVE")) {
            warehouseContext.assertAccess(note.getBranch().getId());
        }
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
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất ID: " + id));
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

        assertExportReadOrApproveAccess(note);
        inventoryCheckGuardService.assertStockMutationAllowed(
                note.getBranch().getId(),
                note.getDetails().stream().map(detail -> detail.getProductVariant().getId()).toList(),
                "xác nhận xuất kho"
        );

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
     * Tạo Phiếu xuất trả NCC trực tiếp từ một Phiếu nhập đã hoàn tất (COMPLETED).
     * <p>
     * Quy tắc:
     * - Chỉ hoạt động khi phiếu nhập ở trạng thái đã hoàn tất (COMPLETED) và có ít nhất 1 dòng hàng lỗi (quantityRejected > 0).
     * - Tự động điền NCC và danh sách hàng lỗi từ phiếu nhập.
     * - Đơn giá trả bị khóa bằng đúng đơn giá nhập của phiếu nhập (chống gian lận).
     * - Số lượng trả mặc định = số lượng lỗi trên phiếu nhập, có thể giảm xuống nhưng không tăng quá.
     */
    @Transactional
    public InventoryNoteResponse createReturnFromGR(Long grId) {
        InventoryNote gr = inventoryNoteRepository.findByIdWithDetails(grId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy Phiếu nhập ID: " + grId));

        if (gr.getType() != InventoryNoteType.IMPORT) {
            throw new BadRequestException("Chỉ có thể tạo phiếu xuất trả từ Phiếu nhập kho (IMPORT).");
        }
        if (gr.getStatus() != InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phiếu nhập phải ở trạng thái đã hoàn tất (COMPLETED) mới có thể tạo phiếu xuất trả.");
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
            // ĐƠN GIÁ KHÓA CỨNG = đơn giá nhập của phiếu nhập (không cho người dùng sửa)
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
                    .note("Lô hàng lỗi từ phiếu nhập " + gr.getCode())
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
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh ID: " + request.getBranchId()));
        assertCheckDraftDetailsPresent(request.getDetails());

        InventoryNote note = new InventoryNote();
        // Cập nhật tiền tố mã chứng từ thành PKK
        note.setCode(request.getCode() != null ? request.getCode() : "PKK-" + System.currentTimeMillis());
        note.setType(InventoryNoteType.CHECK);
        note.setStatus(InventoryNoteStatus.PENDING);
        note.setCreatedAt(LocalDateTime.now());
        note.setBranch(branch);
        note.setNote(request.getNote());
        
        // Cập nhật thông tin kiểm kho mới
        note.setCheckType(request.getType());
        note.setCheckScopeType(resolveScopeType(request));
        note.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : LocalDateTime.now());
        note.setCheckedBy(request.getCheckedBy());
        
        // Tự động gán người tạo từ Token (Luôn ưu tiên User thực tế đang login)
        note.setCreatedBy(getCurrentUser());

        note.setDetails(buildCheckDetails(note, branch, request.getDetails(), false));
        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.DRAFT);
        note.setTotalAmount(BigDecimal.ZERO);
        note.setPaymentAmount(BigDecimal.ZERO);
        note.setDebtAmount(BigDecimal.ZERO);

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse completeCheckCommand(Long id) {
        return approveCheckAdjustment(id);
    }

    @Transactional
    public InventoryNoteResponse startCheckCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetailsForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kiểm kê."));
        warehouseContext.assertAccess(note.getBranch().getId());
        Branch branch = branchRepository.findByIdForUpdate(note.getBranch().getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh."));

        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("Phiếu này không phải phiếu kiểm kê.");
        }
        if (canonicalStatus(note) != InventoryCheckWorkflowStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể bắt đầu phiếu kiểm kê đang ở trạng thái nháp.");
        }
        if (note.getDetails() == null || note.getDetails().isEmpty()) {
            throw new BadRequestException("Phiếu kiểm kê phải có ít nhất một sản phẩm trước khi bắt đầu.");
        }

        inventoryCheckGuardService.assertCheckCanStart(
                branch.getId(),
                note.getCheckScopeType(),
                extractVariantIds(note.getDetails()),
                note.getId()
        );

        for (InventoryNoteDetail detail : note.getDetails()) {
            detail.setQuantity(resolveSystemQuantity(branch, detail.getProductVariant(), detail.getBatchNumber(), detail.getPrice()));
            detail.setQuantityReal(null);
            detail.setQuantityRejected(null);
        }

        note.setCheckStartedAt(LocalDateTime.now());
        note.setCheckSubmittedAt(null);
        note.setCheckApprovedAt(null);
        note.setCheckApprovedBy(null);
        note.setCheckRecountReason(null);
        note.setCheckCancelReason(null);
        note.setCheckCancelledAt(null);
        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.COUNTING);
        note.setStatus(InventoryNoteStatus.PENDING);

        return mapToResponse(inventoryNoteRepository.save(note));
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
            throw new BadRequestException("Phiếu này không phải phiếu kiểm kê.");
        }

        InventoryCheckWorkflowStatus workflowStatus = canonicalStatus(note);
        if (workflowStatus == InventoryCheckWorkflowStatus.PENDING_APPROVAL
                || workflowStatus == InventoryCheckWorkflowStatus.COMPLETED
                || workflowStatus == InventoryCheckWorkflowStatus.CANCELLED
                || note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phiếu kiểm kê đã gửi duyệt hoặc hoàn tất, không thể chỉnh sửa.");
        }

        if (workflowStatus == InventoryCheckWorkflowStatus.DRAFT) {
            warehouseContext.assertAccess(request.getBranchId());
            assertCheckDraftDetailsPresent(request.getDetails());
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new NotFoundException("Khong tim thay chi nhanh ID: " + request.getBranchId()));
            note.setBranch(branch);
            note.setNote(request.getNote());
            note.setCheckType(request.getType());
            note.setCheckScopeType(resolveScopeType(request));
            note.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : LocalDateTime.now());
            note.setCheckedBy(request.getCheckedBy());
            note.setCreatedBy(getCurrentUser());
            note.getDetails().clear();
            inventoryNoteDetailRepository.flush();
            note.getDetails().addAll(buildCheckDetails(note, branch, request.getDetails(), false));
            note.setStatus(InventoryNoteStatus.PENDING);
            note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.DRAFT);
            note.setCheckStartedAt(null);
            note.setCheckSubmittedAt(null);
            note.setCheckApprovedAt(null);
            note.setCheckApprovedBy(null);
            note.setCheckRecountReason(null);
            note.setCheckCancelReason(null);
            note.setCheckCancelledAt(null);
        } else if (workflowStatus == InventoryCheckWorkflowStatus.COUNTING
                || workflowStatus == InventoryCheckWorkflowStatus.RECOUNT_REQUIRED) {
            applyCountingResults(note, request);
            note.setNote(request.getNote());
            note.setCheckedBy(request.getCheckedBy());
            note.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : note.getCheckDate());
        }

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllCheckNotes() {
        Long warehouseId = resolveCheckListBranchId();
        List<InventoryNote> notes = (warehouseId == null)
                ? inventoryNoteRepository.findAllByTypeWithPartners(InventoryNoteType.CHECK)
                : inventoryNoteRepository.findAllByTypeAndBranchWithPartners(InventoryNoteType.CHECK, warehouseId);

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Dành RIÊNG cho trang "Báo cáo kiểm kê hàng hóa" — cố tình KHÔNG dùng resolveCheckListBranchId()
    // (hàm đó gắn với quyền vận hành INVENTORY_CHECK_APPROVE, dùng chung cho cả màn hình thủ kho
    // quản lý phiếu kiểm kê thật). Trang báo cáo cần theo đúng mô hình phân quyền REPORT_INVENTORY_VIEW
    // + REPORT_INVENTORY_VIEW_ALL_BRANCHES giống 3 báo cáo tồn kho còn lại — tách hàm riêng để sửa mô
    // hình phân quyền của báo cáo mà không đụng đến luồng duyệt/xử lý kiểm kê thật của kho.
    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getCheckNotesForReport(Long branchId) {
        Long finalBranchId = com.zone.agri.common.AuthUtils.resolveRequestedOrUserBranch(
                branchId, "REPORT_INVENTORY_VIEW", "REPORT_INVENTORY_VIEW_ALL_BRANCHES");
        List<InventoryNote> notes = (finalBranchId == null)
                ? inventoryNoteRepository.findAllByTypeWithPartners(InventoryNoteType.CHECK)
                : inventoryNoteRepository.findAllByTypeAndBranchWithPartners(InventoryNoteType.CHECK, finalBranchId);

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getCheckCommandById(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho."));
        assertCheckReadOrApproveAccess(note);
        return mapToResponse(note);
    }

    private List<InventoryNoteResponse> getNotesByTypeAndStatus(InventoryNoteType type, InventoryNoteStatus status) {
        Long warehouseId = type == InventoryNoteType.CHECK
                ? resolveCheckListBranchId()
                : warehouseContext.resolveWarehouseId();
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
        
        String fullReason = String.format("Loại: %s | Ref: %s | Đ/c: %s | Lý do: %s",
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
                    detail.setExpiryDate(parseExpiryDate(reqDetail.getExpiryDate()));
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
        Long warehouseId = resolveExportListBranchId();
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

        InventoryCheckWorkflowStatus workflowStatus = canonicalStatus(note);
        if (workflowStatus == InventoryCheckWorkflowStatus.COMPLETED) {
            throw new BadRequestException("Chỉ cho phép xóa phiếu kiểm kê chưa được duyệt cân bằng.");
        }
        inventoryNoteRepository.delete(note);
    }

    @Transactional
    public InventoryNoteResponse submitCheckForApproval(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho."));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("Phiếu này không phải phiếu kiểm kê.");
        }
        InventoryCheckWorkflowStatus workflowStatus = canonicalStatus(note);
        if (workflowStatus != InventoryCheckWorkflowStatus.COUNTING
                && workflowStatus != InventoryCheckWorkflowStatus.RECOUNT_REQUIRED) {
            throw new BadRequestException("Phiếu kiểm kê phải ở trạng thái đang kiểm kê hoặc yêu cầu kiểm lại.");
        }
        if (note.getDetails() == null || note.getDetails().isEmpty()) {
            throw new BadRequestException("Phiếu kiểm kê chưa có dữ liệu snapshot.");
        }

        validateCheckSubmission(note);

        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.PENDING_APPROVAL);
        note.setCheckSubmittedAt(LocalDateTime.now());
        note.setStatus(InventoryNoteStatus.APPROVED);
        note.setCheckRecountReason(null);

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse requestCheckRecount(Long id, String reason) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kiểm kê."));
        assertCheckReadOrApproveAccess(note);

        if (canonicalStatus(note) != InventoryCheckWorkflowStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể yêu cầu kiểm lại ở trạng thái chờ duyệt.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Vui lòng nhập lý do kiểm lại.");
        }

        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.RECOUNT_REQUIRED);
        note.setCheckRecountReason(reason.trim());
        note.setStatus(InventoryNoteStatus.PENDING);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse cancelCheck(Long id, String reason) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kiểm kê."));
        warehouseContext.assertAccess(note.getBranch().getId());

        InventoryCheckWorkflowStatus workflowStatus = canonicalStatus(note);
        if (workflowStatus == InventoryCheckWorkflowStatus.COMPLETED) {
            throw new BadRequestException("Phiếu kiểm kê đã hoàn tất thì không thể hủy.");
        }
        if (workflowStatus == InventoryCheckWorkflowStatus.CANCELLED) {
            throw new BadRequestException("Phiếu kiểm kê này đã bị hủy.");
        }
        if (workflowStatus != InventoryCheckWorkflowStatus.DRAFT && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Vui lòng nhập lý do hủy phiếu kiểm kê.");
        }

        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.CANCELLED);
        note.setCheckCancelReason(reason != null ? reason.trim() : null);
        note.setCheckCancelledAt(LocalDateTime.now());
        note.setStatus(InventoryNoteStatus.CANCELLED);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse approveCheckAdjustment(Long id) {
        User approver = getCurrentUser();
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho ID: " + id));
        assertCheckReadOrApproveAccess(note);

        if (canonicalStatus(note) != InventoryCheckWorkflowStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Phiếu kiểm kê phải ở trạng thái chờ duyệt.");
        }

        Branch branch = note.getBranch();

        for (InventoryNoteDetail detail : note.getDetails()) {
            ProductVariant variant = detail.getProductVariant();
            String batchNum = detail.getBatchNumber();
            BigDecimal importPrice = detail.getPrice();
            int actualQty = Math.max(0, Objects.requireNonNullElse(detail.getQuantityReal(), 0)
                    - Objects.requireNonNullElse(detail.getQuantityRejected(), 0));
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
        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.COMPLETED);
        note.setCheckApprovedAt(LocalDateTime.now());
        note.setCheckApprovedBy(approver);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    private InventoryCheckScopeType resolveScopeType(CheckNoteRequest request) {
        if (request == null || request.getScopeType() == null || request.getScopeType().isBlank()) {
            return InventoryCheckScopeType.FULL_WAREHOUSE;
        }

        try {
            return InventoryCheckScopeType.valueOf(request.getScopeType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Phạm vi kiểm kê không hợp lệ.");
        }
    }

    private InventoryCheckWorkflowStatus canonicalStatus(InventoryNote note) {
        return note.getCheckWorkflowStatus() != null
                ? note.getCheckWorkflowStatus().toCanonical()
                : InventoryCheckWorkflowStatus.DRAFT;
    }

    private void assertCheckDraftDetailsPresent(List<CheckNoteRequest.CheckNoteDetailRequest> requestDetails) {
        if (requestDetails == null || requestDetails.isEmpty()) {
            throw new BadRequestException("Phiếu kiểm kê phải có ít nhất một sản phẩm.");
        }
    }

    private List<InventoryNoteDetail> buildCheckDetails(
            InventoryNote note,
            Branch branch,
            List<CheckNoteRequest.CheckNoteDetailRequest> requestDetails,
            boolean preserveCountResult
    ) {
        List<InventoryNoteDetail> details = new ArrayList<>();
        if (requestDetails == null) {
            return details;
        }

        for (CheckNoteRequest.CheckNoteDetailRequest detailReq : requestDetails) {
            ProductVariant variant = productVariantRepository.findById(detailReq.getProductVariantId())
                    .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại: " + detailReq.getProductVariantId()));

            Integer systemQty = detailReq.getSystemQuantity() != null
                    ? detailReq.getSystemQuantity()
                    : detailReq.getQuantity();
            if (systemQty == null && preserveCountResult) {
                systemQty = resolveSystemQuantity(branch, variant, detailReq.getBatchNumber(), detailReq.getImportPrice());
            }

            details.add(InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantity(systemQty)
                    .quantityReal(preserveCountResult ? detailReq.getQuantityReal() : null)
                    .quantityRejected(preserveCountResult ? detailReq.getQuantityRejected() : null)
                    .batchNumber(detailReq.getBatchNumber())
                    .expiryDate(parseExpiryDate(detailReq.getExpiryDate()))
                    .price(detailReq.getImportPrice())
                    .note(detailReq.getNote())
                    .build());
        }

        return details;
    }

    private Integer resolveSystemQuantity(Branch branch, ProductVariant variant, String batchNumber, BigDecimal importPrice) {
        return inventoryRepository.findExactBatch(branch, variant, batchNumber, importPrice)
                .map(Inventory::getQuantity)
                .orElse(0);
    }

    private LocalDateTime parseExpiryDate(String expiryDate) {
        if (expiryDate == null || expiryDate.isBlank()) {
            return null;
        }

        String normalized = expiryDate.trim();

        try {
            return LocalDate.parse(normalized).atStartOfDay();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (Exception ignored) {
        }

        if (normalized.length() >= 10) {
            try {
                return LocalDate.parse(normalized.substring(0, 10)).atStartOfDay();
            } catch (Exception ignored) {
            }
        }

        throw new BadRequestException("Hạn sử dụng không hợp lệ: " + expiryDate);
    }

    private Set<Long> extractVariantIds(List<InventoryNoteDetail> details) {
        if (details == null) {
            return Set.of();
        }

        return details.stream()
                .map(InventoryNoteDetail::getProductVariant)
                .filter(Objects::nonNull)
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void applyCountingResults(InventoryNote note, CheckNoteRequest request) {
        if (request.getDetails() == null) {
            throw new BadRequestException("Phiếu kiểm kê không có dữ liệu sản phẩm.");
        }

        Map<String, InventoryNoteDetail> existingByKey = note.getDetails().stream()
                .collect(Collectors.toMap(this::detailKey, detail -> detail, (left, right) -> left, LinkedHashMap::new));

        Map<String, CheckNoteRequest.CheckNoteDetailRequest> requestByKey = request.getDetails().stream()
                .collect(Collectors.toMap(this::detailKey, detail -> detail, (left, right) -> left, LinkedHashMap::new));

        if (!existingByKey.keySet().equals(requestByKey.keySet())) {
            throw new BadRequestException("Không thể thay đổi danh sách sản phẩm sau khi đã bắt đầu kiểm kê.");
        }

        for (Map.Entry<String, InventoryNoteDetail> entry : existingByKey.entrySet()) {
            InventoryNoteDetail detail = entry.getValue();
            CheckNoteRequest.CheckNoteDetailRequest requestDetail = requestByKey.get(entry.getKey());
            detail.setQuantityReal(requestDetail.getQuantityReal());
            detail.setQuantityRejected(requestDetail.getQuantityRejected());
            detail.setNote(requestDetail.getNote());
        }
    }

    private String detailKey(CheckNoteRequest.CheckNoteDetailRequest detail) {
        return detail.getProductVariantId() + "|" + Objects.toString(detail.getBatchNumber(), "") + "|"
                + normalizePriceKey(detail.getImportPrice());
    }

    private String detailKey(InventoryNoteDetail detail) {
        Long variantId = detail.getProductVariant() != null ? detail.getProductVariant().getId() : null;
        return variantId + "|" + Objects.toString(detail.getBatchNumber(), "") + "|"
                + normalizePriceKey(detail.getPrice());
    }

    private String normalizePriceKey(BigDecimal price) {
        if (price == null) {
            return "";
        }

        BigDecimal normalized = price.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }

        return normalized.toPlainString();
    }

    private void validateCheckSubmission(InventoryNote note) {
        for (InventoryNoteDetail detail : note.getDetails()) {
            Integer realQty = detail.getQuantityReal();
            Integer rejectedQty = Objects.requireNonNullElse(detail.getQuantityRejected(), 0);
            Integer snapshotQty = Objects.requireNonNullElse(detail.getQuantity(), 0);

            if (realQty == null) {
                throw new BadRequestException("Vui lòng nhập đủ số lượng thực tế trước khi gửi duyệt.");
            }
            if (realQty < 0) {
                throw new BadRequestException("Số lượng thực tế không được âm.");
            }
            if (rejectedQty < 0) {
                throw new BadRequestException("Số lượng hư hỏng không được âm.");
            }
            if (rejectedQty > realQty) {
                throw new BadRequestException("Số lượng hư hỏng không được lớn hơn số lượng thực tế.");
            }

            int diffQty = realQty - snapshotQty;
            if ((diffQty != 0 || rejectedQty > 0) && (detail.getNote() == null || detail.getNote().isBlank())) {
                throw new BadRequestException("Các dòng có chênh lệch hoặc hư hỏng phải nhập ghi chú hoặc nguyên nhân.");
            }
        }
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
                .scopeType(entity.getCheckScopeType() != null
                        ? entity.getCheckScopeType().name()
                        : InventoryCheckScopeType.FULL_WAREHOUSE.name())
                .checkDate(entity.getCheckDate())
                .checkedBy(entity.getCheckedBy())
                .checkWorkflowStatus(canonicalStatus(entity).name())
                .checkStartedAt(entity.getCheckStartedAt())
                .checkSubmittedAt(entity.getCheckSubmittedAt())
                .checkApprovedAt(entity.getCheckApprovedAt())
                .checkApprovedByName(entity.getCheckApprovedBy() != null ? entity.getCheckApprovedBy().getFullName() : null)
                .checkRecountReason(entity.getCheckRecountReason())
                .checkCancelReason(entity.getCheckCancelReason())
                .checkCancelledAt(entity.getCheckCancelledAt())
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
                .unit("Cái") // Giá trị mặc định đơn vị tính
                .quantity(d.getQuantity())           // Hệ thống (Kiểm kho)
                .systemQuantity(d.getQuantity())     // Bí danh cho FE hiển thị
                .quantityRequested(d.getQuantityRequested()) // Yêu cầu (Expected)
                .quantityReal(d.getQuantityReal())           // Thực tế (Actual)
                .quantityAccepted(d.getQuantityAccepted())   // Đạt
                .quantityRejected(d.getQuantityRejected())   // Lỗi
                .batchNumber(d.getBatchNumber())
                .expiryDate(d.getExpiryDate() != null ? d.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null)
                .price(Objects.requireNonNullElse(d.getPrice(), BigDecimal.ZERO))
                .imageUrl(variant != null ? variant.getImageUrl() : null)
                .note(d.getNote())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getExportCommandById(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất."));
        assertExportReadOrApproveAccess(note);
        return mapToResponse(note);
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getCheckCommandByCode(String code) {
        return inventoryNoteRepository.findByCodeWithDetails(code)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho với mã: " + code));
    }
}


