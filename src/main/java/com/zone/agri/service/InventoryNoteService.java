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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final com.zone.agri.common.WarehouseContext warehouseContext;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui lòng đăng nhập để thực hiện thao tác này");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Tài khoản không tồn tại"));
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

        // Save note (cascade will save details)
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    // ==========================================
    // 2. CHỐT PHIẾU XUẤT (CẬP NHẬT TỒN KHO)
    // ==========================================
    @Transactional
    public InventoryNoteResponse completeExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lệnh xuất ID: " + id));

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Lệnh xuất này đã hoàn thành trước đó.");
        }

        Branch sourceBranch = note.getBranch();
        Branch targetBranch = note.getPartnerBranch();
        boolean isInternal = targetBranch != null;
        boolean isReturn = note.getSupplier() != null;

        for (InventoryNoteDetail detail : note.getDetails()) {
            int remainingToDeduct = Objects.requireNonNullElse(detail.getQuantityReal(), 0);
            ProductVariant variant = detail.getProductVariant();

            // 1. Dùng findForUpdateFIFO (đã có @Lock) để tránh Race Condition ở kho xuất
            List<Inventory> availableBatches = inventoryRepository.findForUpdateFIFO(sourceBranch.getId(), variant.getId());

            for (Inventory batch : availableBatches) {
                if (remainingToDeduct <= 0) break;

                int deductAmount = Math.min(batch.getQuantity(), remainingToDeduct);
                int oldSourceQty = batch.getQuantity();
                int newSourceQty = oldSourceQty - deductAmount;

                batch.setQuantity(newSourceQty);
                inventoryRepository.save(batch);

                // Ghi log biến động kho (Xuất)
                TransactionType outType = isReturn ? TransactionType.RETURN : (isInternal ? TransactionType.TRANSFER_OUT : TransactionType.ADJUSTMENT);
                transactionRepository.save(InventoryTransaction.builder()
                        .type(outType)
                        .quantityChange(-deductAmount)
                        .newBalance(newSourceQty)
                        .referenceCode(note.getCode())
                        .reason("Xuất kho (Phiếu: " + note.getCode() + ")")
                        .createdAt(LocalDateTime.now())
                        .inventory(batch)
                        .inventoryNote(note)
                        .build());

                remainingToDeduct -= deductAmount;

                if (isInternal) {
                    // 2. Nhập vào kho đích (Dùng findExactBatchWithLock)
                    Inventory targetBatch = inventoryRepository.findExactBatchWithLock(targetBranch, variant, batch.getBatchNumber(), batch.getImportPrice())
                            .orElseGet(() -> {
                                Inventory newBatch = Inventory.builder()
                                    .branch(targetBranch)
                                    .productVariant(variant)
                                    .batchNumber(batch.getBatchNumber())
                                    .importPrice(batch.getImportPrice())
                                    .expiryDate(batch.getExpiryDate())
                                    .quantity(0)
                                    .build();
                                return inventoryRepository.save(newBatch);
                            });

                    int oldTargetQty = Objects.requireNonNullElse(targetBatch.getQuantity(), 0);
                    int newTargetQty = oldTargetQty + deductAmount;
                    targetBatch.setQuantity(newTargetQty);
                    inventoryRepository.save(targetBatch);

                    // Ghi log biến động kho (Nhập nội bộ)
                    transactionRepository.save(InventoryTransaction.builder()
                            .type(TransactionType.TRANSFER_IN)
                            .quantityChange(deductAmount)
                            .newBalance(newTargetQty)
                            .referenceCode(note.getCode())
                            .reason("Nhập nội bộ từ phiếu xuất: " + note.getCode())
                            .createdAt(LocalDateTime.now())
                            .inventory(targetBatch)
                            .inventoryNote(note)
                            .build());
                }
            }

            if (remainingToDeduct > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Sản phẩm " + variant.getSku() + " không đủ tồn kho thực tế để xuất.");
            }
        }

        note.setStatus(InventoryNoteStatus.COMPLETED);
        return mapToResponse(inventoryNoteRepository.save(note));
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
                    .quantityReal(detailReq.getQuantityReal()) // Số lượng thực tế kiểm thấy
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

            // Lock lô hàng chính xác theo (Branch, Variant, BatchNumber, ImportPrice)
            Optional<Inventory> batchOpt = inventoryRepository.findExactBatchWithLock(branch, variant, batchNum, importPrice);
            
            if (batchOpt.isEmpty()) {
                // Nếu chưa có lô này trong kho -> Tạo mới lô với số lượng thực tế
                Inventory newBatch = Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .batchNumber(batchNum)
                        .quantity(actualQty)
                        .importPrice(importPrice != null ? importPrice : BigDecimal.ZERO)
                        .lastReceiptDate(LocalDateTime.now())
                        .build();
                newBatch = inventoryRepository.save(newBatch);

                transactionRepository.save(InventoryTransaction.builder()
                        .type(TransactionType.ADJUSTMENT)
                        .quantityChange(actualQty)
                        .newBalance(actualQty)
                        .referenceCode(note.getCode())
                        .reason("Kiểm kho: Tạo mới lô hàng (Phiếu: " + note.getCode() + ")")
                        .createdAt(LocalDateTime.now())
                        .inventory(newBatch)
                        .inventoryNote(note)
                        .build());
            } else {
                Inventory batch = batchOpt.get();
                int systemQty = batch.getQuantity();
                int discrepancy = actualQty - systemQty;

                if (discrepancy != 0) {
                    batch.setQuantity(actualQty);
                    inventoryRepository.save(batch);

                    transactionRepository.save(InventoryTransaction.builder()
                            .type(TransactionType.ADJUSTMENT)
                            .quantityChange(discrepancy)
                            .newBalance(actualQty)
                            .referenceCode(note.getCode())
                            .reason("Kiểm kho: Điều chỉnh chênh lệch (Phiếu: " + note.getCode() + ")")
                            .createdAt(LocalDateTime.now())
                            .inventory(batch)
                            .inventoryNote(note)
                            .build());
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

        if ("RETURN".equals(request.getExportType()) && request.getSupplierId() != null) {
            note.setSupplier(supplierRepository.findById(request.getSupplierId()).orElse(null));
            note.setPartnerBranch(null);
        } else if ("INTERNAL".equals(request.getExportType()) && request.getTargetBranchId() != null) {
            note.setPartnerBranch(branchRepository.findById(request.getTargetBranchId()).orElse(null));
            note.setSupplier(null);
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
            Integer totalStock = inventoryRepository.sumQuantityByBranchAndVariant(note.getBranch().getId(), variant.getId());
            if (totalStock < reqDetail.getRequestedQuantity()) {
                throw new BadRequestException("Kho " + note.getBranch().getName() + " chỉ còn " + totalStock + " sản phẩm " + variant.getSku());
            }

            InventoryNoteDetail detail = InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantityRequested(reqDetail.getRequestedQuantity())
                    .quantityReal(reqDetail.getRequestedQuantity())
                    .quantity(reqDetail.getRequestedQuantity())
                    .price(reqDetail.getPrice() != null ? reqDetail.getPrice() : BigDecimal.ZERO)
                    .build();

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
        return getNotesByStatus(InventoryNoteStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllExportReceipts() {
        return getNotesByStatus(InventoryNoteStatus.COMPLETED);
    }

    private List<InventoryNoteResponse> getNotesByStatus(InventoryNoteStatus status) {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes = (warehouseId == null)
                ? inventoryNoteRepository.findAllByTypeAndStatusWithPartners(InventoryNoteType.EXPORT, status)
                : inventoryNoteRepository.findAllByTypeAndStatusAndBranchWithPartners(InventoryNoteType.EXPORT, status, warehouseId);

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
        
        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phiếu ĐÃ XUẤT không được phép xóa. Vui lòng tạo phiếu Nhập Trả Hàng.");
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

        String fullName = entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : "Hệ thống";

        return InventoryNoteResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .noteType(entity.getType() != null ? entity.getType().name() : "EXPORT")
                .exportType(entity.getPartnerBranch() != null ? "INTERNAL" : "RETURN")
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
                .quantityRequested(d.getQuantityRequested()) // Yêu cầu (Xuất kho)
                .quantityReal(d.getQuantityReal())     // Thực tế (Cả hai)
                .batchNumber(d.getBatchNumber())
                .price(Objects.requireNonNullElse(d.getPrice(), BigDecimal.ZERO))
                .imageUrl(variant != null ? variant.getImageUrl() : null)
                .note(d.getNote())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getExportCommandById(Long id) {
        return inventoryNoteRepository.findById(id)
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