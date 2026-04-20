package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.CheckNoteRequest;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.inventory.InventoryNoteDetailResponse;
import com.zone.agri.dto.response.inventory.InventoryNoteResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất ID: " + id));
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt lệnh xuất đang chờ xử lý.");
        }
        note.setStatus(InventoryNoteStatus.APPROVED);
        note = inventoryNoteRepository.save(note);

        boolean isReturn = note.getSupplier() != null ||
                (note.getReason() != null && (note.getReason().contains("RETURN") || note.getReason().contains("Tráº£ NCC")));
        if (isReturn) {
            return completeExportCommand(id);
        }

        return mapToResponse(note);
    }

    // ==========================================
    // 2. CHỐT PHIẾU XUẤT (CẬP NHẬT TỒN KHO - CÓ KIỂM ĐẾM)
    // ==========================================
    @Transactional
    public InventoryNoteResponse completeExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất ID: " + id));

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Lệnh xuất này đã hoàn thành trước đó.");
        }

        if (note.getStatus() != InventoryNoteStatus.APPROVED && note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Phiếu phải ở trạng thái Đã duyệt hoặc Chờ duyệt mới có thể hoàn thành xuất kho.");
        }

        Branch sourceBranch = note.getBranch();
        
        // Kiểm tra logic isReturn dựa trên cả supplier và exportType trong reason/note
        boolean isReturn = note.getSupplier() != null || 
                          (note.getReason() != null && (note.getReason().contains("RETURN") || note.getReason().contains("Trả NCC")));

        for (InventoryNoteDetail detail : note.getDetails()) {
            int remainingToDeduct = Objects.requireNonNullElse(detail.getQuantityRequested(), 0);
            if (remainingToDeduct <= 0) continue;

            ProductVariant variant = detail.getProductVariant();
            String targetBatch = detail.getBatchNumber();
            if (targetBatch == null || targetBatch.isBlank()) {
                targetBatch = "DEFAULT";
            }

            // 1. Ưu tiên trừ theo lô đã chỉ định (nếu có)
            List<Inventory> exactBatches = inventoryRepository.findExactBatchListByNumber(sourceBranch.getId(), variant.getId(), targetBatch);
            for (Inventory batch : exactBatches) {
                if (remainingToDeduct <= 0) break;
                remainingToDeduct = deductFromBatch(batch, remainingToDeduct, isReturn, note);
            }

            // 2. Nếu vẫn còn thiếu, trừ theo FIFO (với các lô khác)
            if (remainingToDeduct > 0) {
                // Đối với phiếu TRẢ, tìm trong kho lỗi trước
                if (isReturn) {
                    List<Inventory> defectiveBatches = inventoryRepository.findForUpdateDefectiveFIFO(sourceBranch.getId(), variant.getId());
                    for (Inventory batch : defectiveBatches) {
                        if (remainingToDeduct <= 0) break;
                        remainingToDeduct = deductFromBatch(batch, remainingToDeduct, isReturn, note);
                    }
                }
                
                // Nếu vẫn còn thiếu (hoặc không phải phiếu trả), tìm tiếp trong kho chính
                if (remainingToDeduct > 0) {
                    List<Inventory> normalBatches = inventoryRepository.findForUpdateFIFO(sourceBranch.getId(), variant.getId());
                    for (Inventory batch : normalBatches) {
                        if (remainingToDeduct <= 0) break;
                        remainingToDeduct = deductFromBatch(batch, remainingToDeduct, isReturn, note);
                    }
                }
            }

            if (remainingToDeduct > 0) {
                // Thêm log chẩn đoán tổng quát
                Long normalStock = inventoryRepository.sumQuantityByBranchAndVariant(sourceBranch.getId(), variant.getId());
                Long defectiveStock = inventoryRepository.sumDefectiveQuantityByBranchAndVariant(sourceBranch.getId(), variant.getId());
                long totalAvailable = (normalStock != null ? normalStock : 0) + (defectiveStock != null ? defectiveStock : 0);
                
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Sản phẩm %s không đủ tồn để xuất. Yêu cầu: %d, Tổng tồn hiện có (Lỗi + Thường): %d. Còn thiếu: %d", 
                        variant.getSku(), detail.getQuantityRequested(), totalAvailable, remainingToDeduct));
            }

            detail.setQuantityReal(detail.getQuantityRequested());
        }

        if (isReturn) {
            BigDecimal totalReturnAmount = note.getDetails().stream()
                    .map(detail -> Objects.requireNonNullElse(detail.getPrice(), BigDecimal.ZERO)
                            .multiply(BigDecimal.valueOf(Objects.requireNonNullElse(detail.getQuantityRequested(), 0))))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            note.setTotalAmount(totalReturnAmount);
            note.setPaymentAmount(BigDecimal.ZERO);
            note.setDebtAmount(totalReturnAmount.negate());
        } else {
            note.setPaymentAmount(BigDecimal.ZERO);
            note.setDebtAmount(BigDecimal.ZERO);
        }

        note.setStatus(InventoryNoteStatus.COMPLETED);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    private int deductFromBatch(Inventory batch, int amount, boolean isReturn, InventoryNote note) {
        int originalToDeduct = amount;
        if (isReturn) {
            // 1. Trừ kho lỗi trước
            int availableDefective = Objects.requireNonNullElse(batch.getDefectiveQuantity(), 0);
            int deductDefective = Math.min(availableDefective, amount);
            if (deductDefective > 0) {
                batch.setDefectiveQuantity(availableDefective - deductDefective);
                amount -= deductDefective;
            }

            // 2. Nếu vẫn còn thiếu và là phiếu trả, cho phép trừ tiếp vào kho chính
            // (Hỗ trợ trường hợp hàng lỗi nhưng lúc nhập bị lưu nhầm vào kho chính)
            if (amount > 0) {
                int availableNormal = Objects.requireNonNullElse(batch.getQuantity(), 0);
                int deductNormal = Math.min(availableNormal, amount);
                if (deductNormal > 0) {
                    batch.setQuantity(availableNormal - deductNormal);
                    amount -= deductNormal;
                }
            }

            if (amount < originalToDeduct) {
                inventoryRepository.save(batch);
                saveTransaction(batch, note, TransactionType.RETURN, -(originalToDeduct - amount), "Xuất trả NCC: " + note.getCode());
            }
        } else {
            // Xuất kho bình thường (chỉ trừ kho chính)
            int availableNormal = Objects.requireNonNullElse(batch.getQuantity(), 0);
            int deductNormal = Math.min(availableNormal, amount);
            if (deductNormal > 0) {
                batch.setQuantity(availableNormal - deductNormal);
                inventoryRepository.save(batch);
                saveTransaction(batch, note, TransactionType.ADJUSTMENT, -deductNormal, "Xuất kho: " + note.getCode());
                amount -= deductNormal;
            }
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
            Long defectiveStock = inventoryRepository.sumDefectiveQuantityByBranchAndVariant(
                    gr.getBranch().getId(), d.getProductVariant().getId());
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
        note.setTotalAmount(BigDecimal.ZERO);
        note.setPaymentAmount(BigDecimal.ZERO);
        note.setDebtAmount(BigDecimal.ZERO);

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse completeCheckCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho ID: " + id));

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Lệnh kiểm kho này đã hoàn thành.");
        }

        Branch branch = note.getBranch();

        for (InventoryNoteDetail detail : note.getDetails()) {
            ProductVariant variant = detail.getProductVariant();
            String batchNum = detail.getBatchNumber();
            BigDecimal importPrice = detail.getPrice();
            int actualQty = Objects.requireNonNullElse(detail.getQuantityReal(), 0);
            int actualDefectiveQty = Objects.requireNonNullElse(detail.getQuantityRejected(), 0);

            // Lock lô hàng chính xác theo (Branch, Variant, BatchNumber, ImportPrice)
            Optional<Inventory> batchOpt = inventoryRepository.findExactBatchWithLock(branch, variant, batchNum, importPrice);
            
            if (batchOpt.isEmpty()) {
                // Nếu chưa có lô này trong kho -> Tạo mới lô với số lượng thực tế
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
                    backorderService.fulfillBackordersOnStockReceive(
                            branch.getId(),
                            variant.getId(),
                            actualQty);
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
                        backorderService.fulfillBackordersOnStockReceive(
                                branch.getId(),
                                variant.getId(),
                                discrepancyNormal);
                    }
                }
            }
        }

        note.setStatus(InventoryNoteStatus.COMPLETED);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllCheckCommands() {
        return getNotesByTypeAndStatus(InventoryNoteType.CHECK, InventoryNoteStatus.PENDING);
    }

    @Transactional
    public InventoryNoteResponse updateCheckCommand(Long id, CheckNoteRequest request) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh kiểm kho."));

        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể chỉnh sửa lệnh kiểm kho đang chờ xử lý.");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh ID: " + request.getBranchId()));

        note.setBranch(branch);
        note.setNote(request.getNote());
        note.setCheckType(request.getType());
        note.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : LocalDateTime.now());
        note.setCheckedBy(request.getCheckedBy());

        // Cập nhật người sửa (Luôn dùng User từ Token)
        note.setCreatedBy(getCurrentUser());

        // Clear and update details
        note.getDetails().clear();
        inventoryNoteDetailRepository.flush();

        List<InventoryNoteDetail> details = new ArrayList<>();
        for (CheckNoteRequest.CheckNoteDetailRequest detailReq : request.getDetails()) {
            ProductVariant variant = productVariantRepository.findById(detailReq.getProductVariantId())
                    .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại: " + detailReq.getProductVariantId()));

            // Ưu tiên dùng systemQuantity từ FE gửi lên, fallback query DB
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
                note.setSupplier(supplierRepository.findById(request.getSupplierId()).orElse(null));
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
