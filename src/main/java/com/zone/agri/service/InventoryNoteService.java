package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.CheckNoteRequest;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.inventory.InventoryNoteDetailResponse;
import com.zone.agri.dto.response.inventory.InventoryNoteResponse;
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
            throw new SignInRequiredException("Vui lÄ‚Â²ng Ă„â€˜Ă„Æ’ng nhĂ¡ÂºÂ­p Ă„â€˜Ă¡Â»Æ’ thĂ¡Â»Â±c hiĂ¡Â»â€¡n thao tÄ‚Â¡c nÄ‚Â y");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("TÄ‚Â i khoĂ¡ÂºÂ£n khÄ‚Â´ng tĂ¡Â»â€œn tĂ¡ÂºÂ¡i"));
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
    // 1. TĂ¡ÂºÂ O LĂ¡Â»â€ NH XUĂ¡ÂºÂ¤T (TRĂ¡ÂºÂ NG THÄ‚ÂI PENDING - CHĂ†Â¯A TRĂ¡Â»Âª KHO)
    // ==========================================
    @Transactional
    public InventoryNoteResponse createExportCommand(ExportNoteRequest request) {
        assertReturnExportRequest(request);
        InventoryNote note = new InventoryNote();
        note.setCode(request.getCode() != null ? request.getCode() : "LXK-" + System.currentTimeMillis());
        note.setType(InventoryNoteType.EXPORT);
        note.setStatus(InventoryNoteStatus.PENDING);
        note.setCreatedAt(LocalDateTime.now());
        note.setCreatedBy(getCurrentUser()); // TĂ¡Â»Â± Ă„â€˜Ă¡Â»â„¢ng gÄ‚Â¡n ngĂ†Â°Ă¡Â»Âi tĂ¡ÂºÂ¡o tĂ¡Â»Â« Token

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
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y lĂ¡Â»â€¡nh xuĂ¡ÂºÂ¥t ID: " + id));
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("ChĂ¡Â»â€° cÄ‚Â³ thĂ¡Â»Æ’ duyĂ¡Â»â€¡t lĂ¡Â»â€¡nh xuĂ¡ÂºÂ¥t Ă„â€˜ang chĂ¡Â»Â xĂ¡Â»Â­ lÄ‚Â½.");
        }
        note.setStatus(InventoryNoteStatus.APPROVED);
        note = inventoryNoteRepository.save(note);

        assertReturnExportNote(note);
        return completeExportCommand(id);
    }

    // ==========================================
    // 2. CHĂ¡Â»ÂT PHIĂ¡ÂºÂ¾U XUĂ¡ÂºÂ¤T (CĂ¡ÂºÂ¬P NHĂ¡ÂºÂ¬T TĂ¡Â»â€™N KHO - CÄ‚â€œ KIĂ¡Â»â€M Ă„ÂĂ¡ÂºÂ¾M)
    // ==========================================
    @Transactional
    public InventoryNoteResponse completeExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay lenh xuat ID: " + id));

        warehouseContext.assertAccess(note.getBranch().getId());
        inventoryCheckGuardService.assertStockMutationAllowed(
                note.getBranch().getId(),
                note.getDetails().stream().map(detail -> detail.getProductVariant().getId()).toList(),
                "xÄ‚Â¡c nhĂ¡ÂºÂ­n xuĂ¡ÂºÂ¥t kho"
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
    // 2b. TĂ¡ÂºÂ O PHIĂ¡ÂºÂ¾U XUĂ¡ÂºÂ¤T TRĂ¡ÂºÂ¢ NCC TĂ¡Â»Âª PHIĂ¡ÂºÂ¾U NHĂ¡ÂºÂ¬P (Quy trÄ‚Â¬nh 3 Ă¢â‚¬â€œ HĂ†Â°Ă¡Â»â€ºng 1)
    // ==========================================

    /**
     * TĂ¡ÂºÂ¡o PhiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ NCC trĂ¡Â»Â±c tiĂ¡ÂºÂ¿p tĂ¡Â»Â« mĂ¡Â»â„¢t PhiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p Ă„â€˜Ä‚Â£ COMPLETED.
     * <p>
     * Quy tĂ¡ÂºÂ¯c:
     * - ChĂ¡Â»â€° hoĂ¡ÂºÂ¡t Ă„â€˜Ă¡Â»â„¢ng khi GR Ă¡Â»Å¸ trĂ¡ÂºÂ¡ng thÄ‚Â¡i COMPLETED vÄ‚Â  cÄ‚Â³ Ä‚Â­t nhĂ¡ÂºÂ¥t 1 dÄ‚Â²ng hÄ‚Â ng lĂ¡Â»â€”i (quantityRejected > 0).
     * - TĂ¡Â»Â± Ă„â€˜Ă¡Â»â„¢ng Ă„â€˜iĂ¡Â»Ân NCC vÄ‚Â  danh sÄ‚Â¡ch hÄ‚Â ng lĂ¡Â»â€”i tĂ¡Â»Â« GR.
     * - Ă„ÂĂ†Â¡n giÄ‚Â¡ trĂ¡ÂºÂ£ bĂ¡Â»â€¹ khÄ‚Â³a bĂ¡ÂºÂ±ng Ă„â€˜Ä‚Âºng Ă„â€˜Ă†Â¡n giÄ‚Â¡ nhĂ¡ÂºÂ­p cĂ¡Â»Â§a GR (chĂ¡Â»â€˜ng gian lĂ¡ÂºÂ­n).
     * - SĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng trĂ¡ÂºÂ£ mĂ¡ÂºÂ·c Ă„â€˜Ă¡Â»â€¹nh = sĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng lĂ¡Â»â€”i trÄ‚Âªn GR, cÄ‚Â³ thĂ¡Â»Æ’ giĂ¡ÂºÂ£m xuĂ¡Â»â€˜ng nhĂ†Â°ng khÄ‚Â´ng tĂ„Æ’ng quÄ‚Â¡.
     */
    @Transactional
    public InventoryNoteResponse createReturnFromGR(Long grId) {
        InventoryNote gr = inventoryNoteRepository.findByIdWithDetails(grId)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y PhiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p ID: " + grId));

        if (gr.getType() != InventoryNoteType.IMPORT) {
            throw new BadRequestException("ChĂ¡Â»â€° cÄ‚Â³ thĂ¡Â»Æ’ tĂ¡ÂºÂ¡o phiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ tĂ¡Â»Â« PhiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p kho (IMPORT).");
        }
        if (gr.getStatus() != InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p phĂ¡ÂºÂ£i Ă¡Â»Å¸ trĂ¡ÂºÂ¡ng thÄ‚Â¡i COMPLETED mĂ¡Â»â€ºi cÄ‚Â³ thĂ¡Â»Æ’ tĂ¡ÂºÂ¡o phiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£.");
        }
        if (gr.getSupplier() == null) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p khÄ‚Â´ng cÄ‚Â³ thÄ‚Â´ng tin nhÄ‚Â  cung cĂ¡ÂºÂ¥p.");
        }

        // LĂ¡ÂºÂ¥y cÄ‚Â¡c dÄ‚Â²ng cÄ‚Â³ hÄ‚Â ng lĂ¡Â»â€”i
        List<InventoryNoteDetail> defectiveDetails = gr.getDetails().stream()
                .filter(d -> d.getQuantityRejected() != null && d.getQuantityRejected() > 0)
                .collect(java.util.stream.Collectors.toList());

        if (defectiveDetails.isEmpty()) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p khÄ‚Â´ng cÄ‚Â³ hÄ‚Â ng lĂ¡Â»â€”i nÄ‚Â o Ă„â€˜Ă¡Â»Æ’ tĂ¡ÂºÂ¡o phiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£.");
        }

        // KiĂ¡Â»Æ’m tra tĂ¡Â»â€œn kho lĂ¡Â»â€”i thĂ¡Â»Â±c tĂ¡ÂºÂ¿ (Ă„â€˜Ă¡Â»Â phÄ‚Â²ng Ă„â€˜Ä‚Â£ xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ trĂ†Â°Ă¡Â»â€ºc Ă„â€˜Ä‚Â³)
        for (InventoryNoteDetail d : defectiveDetails) {
            if (d.getBatchNumber() == null || d.getBatchNumber().isBlank()) {
                d.setQuantityRejected(0);
                continue;
            }
            Long defectiveStock = inventoryRepository.sumDefectiveQuantityByBranchAndVariantAndBatch(
                    gr.getBranch().getId(), d.getProductVariant().getId(), d.getBatchNumber());
            if (defectiveStock == null || defectiveStock < d.getQuantityRejected()) {
                // KhÄ‚Â´ng throw, chĂ¡Â»â€° cĂ¡ÂºÂ£nh bÄ‚Â¡o bĂ¡ÂºÂ±ng cÄ‚Â¡ch Ă„â€˜iĂ¡Â»Âu chĂ¡Â»â€°nh sĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng cÄ‚Â²n lĂ¡ÂºÂ¡i
                d.setQuantityRejected(defectiveStock != null ? defectiveStock.intValue() : 0);
            }
        }

        // LĂ¡Â»Âc lĂ¡ÂºÂ¡i sau khi Ă„â€˜iĂ¡Â»Âu chĂ¡Â»â€°nh
        defectiveDetails = defectiveDetails.stream()
                .filter(d -> d.getQuantityRejected() != null && d.getQuantityRejected() > 0)
                .collect(java.util.stream.Collectors.toList());

        if (defectiveDetails.isEmpty()) {
            throw new BadRequestException("TĂ¡Â»â€œn kho lĂ¡Â»â€”i cĂ¡Â»Â§a NCC nÄ‚Â y Ă„â€˜Ä‚Â£ hĂ¡ÂºÂ¿t hoĂ¡ÂºÂ·c Ă„â€˜Ä‚Â£ xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ hĂ¡ÂºÂ¿t trĂ†Â°Ă¡Â»â€ºc Ă„â€˜Ä‚Â³.");
        }

        // TĂ¡ÂºÂ¡o phiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£
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
        // Ă„ÂÄ‚Â¡nh dĂ¡ÂºÂ¥u lÄ‚Â  phiĂ¡ÂºÂ¿u RETURN Ă„â€˜Ă¡Â»Æ’ logic xuĂ¡ÂºÂ¥t kho biĂ¡ÂºÂ¿t dÄ‚Â¹ng kho lĂ¡Â»â€”i
        returnNote.setReason("RETURN | TĂ¡ÂºÂ¡o tĂ¡Â»Â« PhiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p: " + gr.getCode() + " | NCC: " + gr.getSupplier().getName());
        returnNote.setNote("XuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ hÄ‚Â ng lĂ¡Â»â€”i tĂ¡Â»Â« phiĂ¡ÂºÂ¿u nhĂ¡ÂºÂ­p " + gr.getCode());

        BigDecimal totalReturn = BigDecimal.ZERO;
        List<InventoryNoteDetail> returnDetails = new ArrayList<>();

        for (InventoryNoteDetail grDetail : defectiveDetails) {
            // Ă„ÂĂ†Â N GIÄ‚Â KHÄ‚â€œA CĂ¡Â»Â¨NG = Ă„â€˜Ă†Â¡n giÄ‚Â¡ nhĂ¡ÂºÂ­p cĂ¡Â»Â§a GR (khÄ‚Â´ng cho ngĂ†Â°Ă¡Â»Âi dÄ‚Â¹ng sĂ¡Â»Â­a)
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
                    .note("LÄ‚Â´ hÄ‚Â ng lĂ¡Â»â€”i tĂ¡Â»Â« GR " + gr.getCode())
                    .build();

            returnDetails.add(returnDetail);
            totalReturn = totalReturn.add(lockedPrice.multiply(BigDecimal.valueOf(returnQty)));
        }

        returnNote.setDetails(returnDetails);
        returnNote.setTotalAmount(totalReturn);
        // PhiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ tĂ¡ÂºÂ¡o ra mĂ¡Â»â„¢t khoĂ¡ÂºÂ£n ghi nhĂ¡ÂºÂ­n Ä‚Â¢m (credit) vĂ¡Â»â€ºi NCC
        // debtAmount Ä‚Â¢m = giĂ¡ÂºÂ£m nĂ¡Â»Â£ NCC
        returnNote.setDebtAmount(totalReturn.negate());
        returnNote.setPaymentAmount(BigDecimal.ZERO);

        return mapToResponse(inventoryNoteRepository.save(returnNote));
    }

    // ==========================================
    // 3. KIĂ¡Â»â€M KHO (INVENTORY CHECK)
    // ==========================================

    @Transactional
    public InventoryNoteResponse createCheckCommand(CheckNoteRequest request) {
        // branch access and freeze validation are applied before snapshot starts
        warehouseContext.assertAccess(request.getBranchId());
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y chi nhÄ‚Â¡nh ID: " + request.getBranchId()));
        assertCheckDraftDetailsPresent(request.getDetails());

        InventoryNote note = new InventoryNote();
        // CĂ¡ÂºÂ­p nhĂ¡ÂºÂ­t prefix mÄ‚Â£ chĂ¡Â»Â©ng tĂ¡Â»Â« thÄ‚Â nh PKK
        note.setCode(request.getCode() != null ? request.getCode() : "PKK-" + System.currentTimeMillis());
        note.setType(InventoryNoteType.CHECK);
        note.setStatus(InventoryNoteStatus.PENDING);
        note.setCreatedAt(LocalDateTime.now());
        note.setBranch(branch);
        note.setNote(request.getNote());
        
        // CĂ¡ÂºÂ­p nhĂ¡ÂºÂ­t thÄ‚Â´ng tin kiĂ¡Â»Æ’m kho mĂ¡Â»â€ºi
        note.setCheckType(request.getType());
        note.setCheckScopeType(resolveScopeType(request));
        note.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : LocalDateTime.now());
        note.setCheckedBy(request.getCheckedBy());
        
        // TĂ¡Â»Â± Ă„â€˜Ă¡Â»â„¢ng gÄ‚Â¡n ngĂ†Â°Ă¡Â»Âi tĂ¡ÂºÂ¡o tĂ¡Â»Â« Token (LuÄ‚Â´n Ă†Â°u tiÄ‚Âªn User thĂ¡Â»Â±c tĂ¡ÂºÂ¿ Ă„â€˜ang login)
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
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª."));
        warehouseContext.assertAccess(note.getBranch().getId());
        Branch branch = branchRepository.findByIdForUpdate(note.getBranch().getId())
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y chi nhÄ‚Â¡nh."));

        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u nÄ‚Â y khÄ‚Â´ng phĂ¡ÂºÂ£i phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª.");
        }
        if (canonicalStatus(note) != InventoryCheckWorkflowStatus.DRAFT) {
            throw new BadRequestException("ChĂ¡Â»â€° cÄ‚Â³ thĂ¡Â»Æ’ bĂ¡ÂºÂ¯t Ă„â€˜Ă¡ÂºÂ§u phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª Ă„â€˜ang Ă¡Â»Å¸ trĂ¡ÂºÂ¡ng thÄ‚Â¡i nhÄ‚Â¡p.");
        }
        if (note.getDetails() == null || note.getDetails().isEmpty()) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª phĂ¡ÂºÂ£i cÄ‚Â³ Ä‚Â­t nhĂ¡ÂºÂ¥t mĂ¡Â»â„¢t sĂ¡ÂºÂ£n phĂ¡ÂºÂ©m trĂ†Â°Ă¡Â»â€ºc khi bĂ¡ÂºÂ¯t Ă„â€˜Ă¡ÂºÂ§u.");
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
        return inventoryNoteRepository.findByIdWithDetails(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y lĂ¡Â»â€¡nh kiĂ¡Â»Æ’m kho."));
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
    // CĂ¡ÂºÂ¬P NHĂ¡ÂºÂ¬T LĂ¡Â»â€ NH XUĂ¡ÂºÂ¤T (CHĂ¡Â»Ë† Ä‚ÂP DĂ¡Â»Â¤NG KHI STATUS = PENDING)
    // ==========================================
    @Transactional
    public InventoryNoteResponse updateExportCommand(Long id, ExportNoteRequest request) {
        assertReturnExportRequest(request);
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y lĂ¡Â»â€¡nh xuĂ¡ÂºÂ¥t."));

        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("ChĂ¡Â»â€° cÄ‚Â³ thĂ¡Â»Æ’ chĂ¡Â»â€°nh sĂ¡Â»Â­a lĂ¡Â»â€¡nh xuĂ¡ÂºÂ¥t Ă„â€˜ang chĂ¡Â»Â xĂ¡Â»Â­ lÄ‚Â½.");
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
        note.setShippingAddress(request.getShippingAddress()); // LĂ†Â°u Ă„â€˜Ă¡Â»â€¹a chĂ¡Â»â€° tÄ‚Â¡ch biĂ¡Â»â€¡t
        
        String fullReason = String.format("LoĂ¡ÂºÂ¡i: %s | Ref: %s | Ă„Â/c: %s | Lydo: %s",
                request.getExportType(), request.getReferenceCode(), request.getShippingAddress(), request.getNote());
        note.setReason(fullReason);

        if (request.getExpectedDate() != null) {
            note.setEntryDate(request.getExpectedDate().atStartOfDay());
        }

        Branch sourceBranch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y kho xuĂ¡ÂºÂ¥t"));
        note.setBranch(sourceBranch);

        if (request.getCreatedById() != null) {
            userRepository.findById(request.getCreatedById()).ifPresent(note::setCreatedBy);
        }

        // XĂ¡Â»Â¬ LÄ‚Â Ă„ÂĂ¡Â»ÂI TÄ‚ÂC: NHÄ‚â‚¬ CUNG CĂ¡ÂºÂ¤P HOĂ¡ÂºÂ¶C CHI NHÄ‚ÂNH NHĂ¡ÂºÂ¬N
        if (request.getSupplierId() != null || "RETURN".equals(request.getExportType())) {
            if (!isWarehouseBranch(sourceBranch)) {
                throw new BadRequestException(
                        "ChĂ¡Â»â€° cÄ‚Â¡c chi nhÄ‚Â¡nh loĂ¡ÂºÂ¡i kho mĂ¡Â»â€ºi Ă„â€˜Ă†Â°Ă¡Â»Â£c phÄ‚Â©p thĂ¡Â»Â±c hiĂ¡Â»â€¡n nghiĂ¡Â»â€¡p vĂ¡Â»Â¥ xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ nhÄ‚Â  cung cĂ¡ÂºÂ¥p.");
            }
            
            if (request.getSupplierId() != null) {
                Supplier supplier = supplierRepository.findById(request.getSupplierId())
                        .orElseThrow(() -> new NotFoundException("Khong tim thay nha cung cap ID: " + request.getSupplierId()));
                note.setSupplier(supplier);
            } else if (request.getDetails() != null && !request.getDetails().isEmpty()) {
                // TĂ¡Â»Â° Ă„ÂĂ¡Â»ËœNG TRUY VĂ¡ÂºÂ¾T NCC TĂ¡Â»Âª LÄ‚â€ HÄ‚â‚¬NG Ă„ÂĂ¡ÂºÂ¦U TIÄ‚ÂN NĂ¡ÂºÂ¾U FE KHÄ‚â€NG GĂ¡Â»Â¬I SUPPLIER_ID
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
            // NĂ¡ÂºÂ¿u khÄ‚Â´ng cÄ‚Â³ cĂ¡ÂºÂ£ 2 thÄ‚Â¬ xÄ‚Â³a trĂ¡ÂºÂ¯ng Ă„â€˜Ă¡Â»â€˜i tÄ‚Â¡c (vÄ‚Â­ dĂ¡Â»Â¥ xuĂ¡ÂºÂ¥t hĂ¡Â»Â§y)
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
                    .orElseThrow(() -> new NotFoundException("SĂ¡ÂºÂ£n phĂ¡ÂºÂ©m khÄ‚Â´ng tĂ¡Â»â€œn tĂ¡ÂºÂ¡i ID: " + reqDetail.getProductVariantId()));

            // Check stock availability
            boolean isReturn = note.getSupplier() != null || 
                              (note.getReason() != null && (note.getReason().contains("RETURN") || note.getReason().contains("TrĂ¡ÂºÂ£ NCC")));
            
            int checkStock;
            String errorPool;
            String batchNum = reqDetail.getBatchNumber();
            List<InventoryNoteDetail> originalImportDetails = Collections.emptyList();
            
            if (isReturn) {
                if (batchNum == null || batchNum.isBlank()) {
                    throw new BadRequestException("XuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ nhÄ‚Â  cung cĂ¡ÂºÂ¥p bĂ¡ÂºÂ¯t buĂ¡Â»â„¢c chĂ¡Â»Ân Ă„â€˜Ä‚Âºng lÄ‚Â´ hÄ‚Â ng lĂ¡Â»â€”i.");
                }

                if (note.getSupplier() == null) {
                    throw new BadRequestException("PhiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t trĂ¡ÂºÂ£ nhÄ‚Â  cung cĂ¡ÂºÂ¥p thiĂ¡ÂºÂ¿u thÄ‚Â´ng tin nhÄ‚Â  cung cĂ¡ÂºÂ¥p.");
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
                            "LÄ‚Â´ %s cĂ¡Â»Â§a sĂ¡ÂºÂ£n phĂ¡ÂºÂ©m %s khÄ‚Â´ng thuĂ¡Â»â„¢c Ă„â€˜Ä‚Âºng nhÄ‚Â  cung cĂ¡ÂºÂ¥p hoĂ¡ÂºÂ·c Ă„â€˜Ä‚Âºng kho nhĂ¡ÂºÂ­p hiĂ¡Â»â€¡n tĂ¡ÂºÂ¡i.",
                            batchNum,
                            variant.getSku()
                    ));
                }

                Long defectiveStockLong;
                if (batchNum != null && !batchNum.isBlank()) {
                    defectiveStockLong = inventoryRepository.sumDefectiveQuantityByBranchAndVariantAndBatch(note.getBranch().getId(), variant.getId(), batchNum);
                    errorPool = "kho lĂ¡Â»â€”i (lÄ‚Â´ " + batchNum + ")";
                } else {
                    defectiveStockLong = inventoryRepository.sumDefectiveQuantityByBranchAndVariant(note.getBranch().getId(), variant.getId());
                    errorPool = "kho lĂ¡Â»â€”i (tĂ¡Â»â€¢ng)";
                }
                checkStock = defectiveStockLong != null ? defectiveStockLong.intValue() : 0;
            } else {
                Long normalStockLong = inventoryRepository.sumQuantityByBranchAndVariant(note.getBranch().getId(), variant.getId());
                checkStock = normalStockLong != null ? normalStockLong.intValue() : 0;
                errorPool = "kho chÄ‚Â­nh";
            }
            
            if (checkStock < reqDetail.getRequestedQuantity()) {
                // LĂ¡ÂºÂ¥y chi tiĂ¡ÂºÂ¿t tĂ¡ÂºÂ¥t cĂ¡ÂºÂ£ cÄ‚Â¡c dÄ‚Â²ng cÄ‚Â³ hÄ‚Â ng lĂ¡Â»â€”i cĂ¡Â»Â§a biĂ¡ÂºÂ¿n thĂ¡Â»Æ’ nÄ‚Â y tĂ¡ÂºÂ¡i chi nhÄ‚Â¡nh
                List<Inventory> allDefectiveInBranch = inventoryRepository.findAllByBranchIdAndDefectiveQuantityGreaterThan(note.getBranch().getId(), 0);
                String availableBatches = allDefectiveInBranch.stream()
                        .filter(i -> i.getProductVariant().getId().equals(variant.getId()))
                        .map(i -> (i.getBatchNumber() == null ? "TRĂ¡Â»ÂNG" : i.getBatchNumber()) + ":" + i.getDefectiveQuantity())
                        .collect(Collectors.joining(", "));
                
                if (availableBatches.isEmpty()) availableBatches = "KhÄ‚Â´ng cÄ‚Â³ lÄ‚Â´ nÄ‚Â o cÄ‚Â³ hÄ‚Â ng lĂ¡Â»â€”i";

                throw new BadRequestException(String.format("SĂ¡ÂºÂ£n phĂ¡ÂºÂ©m %s: LÄ‚Â´ %s chĂ¡Â»â€° cÄ‚Â²n %d lĂ¡Â»â€”i. Danh sÄ‚Â¡ch lÄ‚Â´ Ă„â€˜ang cÄ‚Â³ hÄ‚Â ng lĂ¡Â»â€”i thĂ¡Â»Â±c tĂ¡ÂºÂ¿ trong DB: [%s]. (YÄ‚Âªu cĂ¡ÂºÂ§u: %d)",
                        variant.getSku(), (batchNum == null ? "TRĂ¡Â»ÂNG" : batchNum), checkStock, availableBatches, reqDetail.getRequestedQuantity()));
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
                    .quantity(reqDetail.getPlannedQuantity()) // LĂ†Â°u sĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng yÄ‚Âªu cĂ¡ÂºÂ§u ban Ă„â€˜Ă¡ÂºÂ§u vÄ‚Â o trĂ†Â°Ă¡Â»Âng quantity cĂ¡Â»Â§a detail
                    .quantityRejected(reqDetail.getDefectiveQuantity()) // LĂ†Â°u sĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng lĂ¡Â»â€”i hiĂ¡Â»â€¡n cÄ‚Â³
                    .batchNumber(reqDetail.getBatchNumber())
                    .price(lockedPrice)
                    .note(reqDetail.getNote())
                    .build();

            // XĂ¡Â»Â­ lÄ‚Â½ hĂ¡ÂºÂ¡n dÄ‚Â¹ng nĂ¡ÂºÂ¿u cÄ‚Â³ gĂ¡Â»Â­i lÄ‚Âªn
            if (reqDetail.getExpiryDate() != null && !reqDetail.getExpiryDate().isBlank()) {
                try {
                    detail.setExpiryDate(parseExpiryDate(reqDetail.getExpiryDate()));
                } catch (Exception e) { /* BĂ¡Â»Â qua lĂ¡Â»â€”i format ngÄ‚Â y */ }
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
    // 3. LĂ¡ÂºÂ¤Y DANH SÄ‚ÂCH
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
    // 4. XÄ‚â€œA PHIĂ¡ÂºÂ¾U
    // ==========================================
    @Transactional
    public void deleteExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y phiĂ¡ÂºÂ¿u xuĂ¡ÂºÂ¥t."));
        
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("ChĂ¡Â»â€° Ă„â€˜Ă†Â°Ă¡Â»Â£c phÄ‚Â©p xÄ‚Â³a phiĂ¡ÂºÂ¿u Ă¡Â»Å¸ trĂ¡ÂºÂ¡ng thÄ‚Â¡i CHĂ¡Â»Å“ DUYĂ¡Â»â€ T. CÄ‚Â¡c phiĂ¡ÂºÂ¿u Ă„â€˜Ä‚Â£ duyĂ¡Â»â€¡t hoĂ¡ÂºÂ·c Ă„â€˜Ä‚Â£ xuĂ¡ÂºÂ¥t kho khÄ‚Â´ng thĂ¡Â»Æ’ xÄ‚Â³a.");
        }
        inventoryNoteRepository.delete(note);
    }

    @Transactional
    public void deleteCheckNote(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kho."));

        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("Ă„ÂÄ‚Â¢y khÄ‚Â´ng phĂ¡ÂºÂ£i lÄ‚Â  phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kho.");
        }

        InventoryCheckWorkflowStatus workflowStatus = canonicalStatus(note);
        if (workflowStatus == InventoryCheckWorkflowStatus.COMPLETED) {
            throw new BadRequestException("Chá»‰ cho phĂ©p xĂ³a phiáº¿u kiá»ƒm kĂª chÆ°a Ä‘Æ°á»£c duyá»‡t cĂ¢n báº±ng.");
        }
        inventoryNoteRepository.delete(note);
    }

    @Transactional
    public InventoryNoteResponse submitCheckForApproval(Long id) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y lĂ¡Â»â€¡nh kiĂ¡Â»Æ’m kho."));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (note.getType() != InventoryNoteType.CHECK) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u nÄ‚Â y khÄ‚Â´ng phĂ¡ÂºÂ£i phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª.");
        }
        InventoryCheckWorkflowStatus workflowStatus = canonicalStatus(note);
        if (workflowStatus != InventoryCheckWorkflowStatus.COUNTING
                && workflowStatus != InventoryCheckWorkflowStatus.RECOUNT_REQUIRED) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª phĂ¡ÂºÂ£i Ă¡Â»Å¸ trĂ¡ÂºÂ¡ng thÄ‚Â¡i Ă„â€˜ang kiĂ¡Â»Æ’m kÄ‚Âª hoĂ¡ÂºÂ·c yÄ‚Âªu cĂ¡ÂºÂ§u kiĂ¡Â»Æ’m lĂ¡ÂºÂ¡i.");
        }
        if (note.getDetails() == null || note.getDetails().isEmpty()) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª chĂ†Â°a cÄ‚Â³ dĂ¡Â»Â¯ liĂ¡Â»â€¡u snapshot.");
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
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª."));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (canonicalStatus(note) != InventoryCheckWorkflowStatus.PENDING_APPROVAL) {
            throw new BadRequestException("ChĂ¡Â»â€° cÄ‚Â³ thĂ¡Â»Æ’ yÄ‚Âªu cĂ¡ÂºÂ§u kiĂ¡Â»Æ’m lĂ¡ÂºÂ¡i Ă¡Â»Å¸ trĂ¡ÂºÂ¡ng thÄ‚Â¡i chĂ¡Â»Â duyĂ¡Â»â€¡t.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Vui lÄ‚Â²ng nhĂ¡ÂºÂ­p lÄ‚Â½ do kiĂ¡Â»Æ’m lĂ¡ÂºÂ¡i.");
        }

        note.setCheckWorkflowStatus(InventoryCheckWorkflowStatus.RECOUNT_REQUIRED);
        note.setCheckRecountReason(reason.trim());
        note.setStatus(InventoryNoteStatus.PENDING);
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    @Transactional
    public InventoryNoteResponse cancelCheck(Long id, String reason) {
        InventoryNote note = inventoryNoteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª."));
        warehouseContext.assertAccess(note.getBranch().getId());

        InventoryCheckWorkflowStatus workflowStatus = canonicalStatus(note);
        if (workflowStatus == InventoryCheckWorkflowStatus.COMPLETED) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª Ă„â€˜Ä‚Â£ hoÄ‚Â n tĂ¡ÂºÂ¥t thÄ‚Â¬ khÄ‚Â´ng thĂ¡Â»Æ’ hĂ¡Â»Â§y.");
        }
        if (workflowStatus == InventoryCheckWorkflowStatus.CANCELLED) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª nÄ‚Â y Ă„â€˜Ä‚Â£ bĂ¡Â»â€¹ hĂ¡Â»Â§y.");
        }
        if (workflowStatus != InventoryCheckWorkflowStatus.DRAFT && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Vui lÄ‚Â²ng nhĂ¡ÂºÂ­p lÄ‚Â½ do hĂ¡Â»Â§y phiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª.");
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
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y lĂ¡Â»â€¡nh kiĂ¡Â»Æ’m kho ID: " + id));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (canonicalStatus(note) != InventoryCheckWorkflowStatus.PENDING_APPROVAL) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª phĂ¡ÂºÂ£i Ă¡Â»Å¸ trĂ¡ÂºÂ¡ng thÄ‚Â¡i chĂ¡Â»Â duyĂ¡Â»â€¡t.");
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
                        .reason("KiĂ¡Â»Æ’m kho: TĂ¡ÂºÂ¡o mĂ¡Â»â€ºi lÄ‚Â´ hÄ‚Â ng (PhiĂ¡ÂºÂ¿u: " + note.getCode() + ")")
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
                            .reason("KiĂ¡Â»Æ’m kho: Ă„ÂiĂ¡Â»Âu chĂ¡Â»â€°nh chÄ‚Âªnh lĂ¡Â»â€¡ch (PhiĂ¡ÂºÂ¿u: " + note.getCode() + ")")
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
            throw new BadRequestException("PhĂ¡ÂºÂ¡m vi kiĂ¡Â»Æ’m kÄ‚Âª khÄ‚Â´ng hĂ¡Â»Â£p lĂ¡Â»â€¡.");
        }
    }

    private InventoryCheckWorkflowStatus canonicalStatus(InventoryNote note) {
        return note.getCheckWorkflowStatus() != null
                ? note.getCheckWorkflowStatus().toCanonical()
                : InventoryCheckWorkflowStatus.DRAFT;
    }

    private void assertCheckDraftDetailsPresent(List<CheckNoteRequest.CheckNoteDetailRequest> requestDetails) {
        if (requestDetails == null || requestDetails.isEmpty()) {
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª phĂ¡ÂºÂ£i cÄ‚Â³ Ä‚Â­t nhĂ¡ÂºÂ¥t mĂ¡Â»â„¢t sĂ¡ÂºÂ£n phĂ¡ÂºÂ©m.");
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
                    .orElseThrow(() -> new NotFoundException("SĂ¡ÂºÂ£n phĂ¡ÂºÂ©m khÄ‚Â´ng tĂ¡Â»â€œn tĂ¡ÂºÂ¡i: " + detailReq.getProductVariantId()));

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

        throw new BadRequestException("Han su dung khong hop le: " + expiryDate);
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
            throw new BadRequestException("PhiĂ¡ÂºÂ¿u kiĂ¡Â»Æ’m kÄ‚Âª khÄ‚Â´ng cÄ‚Â³ dĂ¡Â»Â¯ liĂ¡Â»â€¡u sĂ¡ÂºÂ£n phĂ¡ÂºÂ©m.");
        }

        Map<String, InventoryNoteDetail> existingByKey = note.getDetails().stream()
                .collect(Collectors.toMap(this::detailKey, detail -> detail, (left, right) -> left, LinkedHashMap::new));

        Map<String, CheckNoteRequest.CheckNoteDetailRequest> requestByKey = request.getDetails().stream()
                .collect(Collectors.toMap(this::detailKey, detail -> detail, (left, right) -> left, LinkedHashMap::new));

        if (!existingByKey.keySet().equals(requestByKey.keySet())) {
            throw new BadRequestException("KhÄ‚Â´ng thĂ¡Â»Æ’ thay Ă„â€˜Ă¡Â»â€¢i danh sÄ‚Â¡ch sĂ¡ÂºÂ£n phĂ¡ÂºÂ©m sau khi Ă„â€˜Ä‚Â£ bĂ¡ÂºÂ¯t Ă„â€˜Ă¡ÂºÂ§u kiĂ¡Â»Æ’m kÄ‚Âª.");
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
                throw new BadRequestException("Vui lÄ‚Â²ng nhĂ¡ÂºÂ­p Ă„â€˜Ă¡Â»Â§ sĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng thĂ¡Â»Â±c tĂ¡ÂºÂ¿ trĂ†Â°Ă¡Â»â€ºc khi gĂ¡Â»Â­i duyĂ¡Â»â€¡t.");
            }
            if (realQty < 0) {
                throw new BadRequestException("SĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng thĂ¡Â»Â±c tĂ¡ÂºÂ¿ khÄ‚Â´ng Ă„â€˜Ă†Â°Ă¡Â»Â£c Ä‚Â¢m.");
            }
            if (rejectedQty < 0) {
                throw new BadRequestException("SĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng hĂ†Â° hĂ¡Â»Âng khÄ‚Â´ng Ă„â€˜Ă†Â°Ă¡Â»Â£c Ä‚Â¢m.");
            }
            if (rejectedQty > realQty) {
                throw new BadRequestException("SĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng hĂ†Â° hĂ¡Â»Âng khÄ‚Â´ng Ă„â€˜Ă†Â°Ă¡Â»Â£c lĂ¡Â»â€ºn hĂ†Â¡n sĂ¡Â»â€˜ lĂ†Â°Ă¡Â»Â£ng thĂ¡Â»Â±c tĂ¡ÂºÂ¿.");
            }

            int diffQty = realQty - snapshotQty;
            if ((diffQty != 0 || rejectedQty > 0) && (detail.getNote() == null || detail.getNote().isBlank())) {
                throw new BadRequestException("CÄ‚Â¡c dÄ‚Â²ng cÄ‚Â³ chÄ‚Âªnh lĂ¡Â»â€¡ch hoĂ¡ÂºÂ·c hĂ†Â° hĂ¡Â»Âng phĂ¡ÂºÂ£i nhĂ¡ÂºÂ­p ghi chÄ‚Âº hoĂ¡ÂºÂ·c nguyÄ‚Âªn nhÄ‚Â¢n.");
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
            partnerName = "[NĂ¡Â»â„¢i bĂ¡Â»â„¢] " + entity.getPartnerBranch().getName();
        } else if (entity.getSupplier() != null) {
            partnerName = "[TrĂ¡ÂºÂ£ NCC] " + entity.getSupplier().getName();
        } else if (entity.getDeliverer() != null && !entity.getDeliverer().isEmpty()) {
            partnerName = entity.getDeliverer();
        }

        String fullName = (entity.getCreatedBy() != null) ? entity.getCreatedBy().getFullName() : "HĂ¡Â»â€¡ thĂ¡Â»â€˜ng";

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
                // CÄ‚Â¡c trĂ†Â°Ă¡Â»Âng thÄ‚Â´ng tin kiĂ¡Â»Æ’m kho mĂ¡Â»â€ºi
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
                .shippingAddress(entity.getShippingAddress()) // BĂ¡Â»â€¢ sung Ă„â€˜Ă¡Â»â€¹a chĂ¡Â»â€°
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
                .unit("CÄ‚Â¡i") // Fallback hardcoded unit
                .quantity(d.getQuantity())           // HĂ¡Â»â€¡ thĂ¡Â»â€˜ng (KiĂ¡Â»Æ’m kho)
                .systemQuantity(d.getQuantity())     // Alias cho FE hiĂ¡Â»Æ’n thĂ¡Â»â€¹
                .quantityRequested(d.getQuantityRequested()) // YÄ‚Âªu cĂ¡ÂºÂ§u (Expected)
                .quantityReal(d.getQuantityReal())           // ThĂ¡Â»Â±c tĂ¡ÂºÂ¿ (Actual)
                .quantityAccepted(d.getQuantityAccepted())   // Ă„ÂĂ¡ÂºÂ¡t
                .quantityRejected(d.getQuantityRejected())   // LĂ¡Â»â€”i
                .batchNumber(d.getBatchNumber())
                .expiryDate(d.getExpiryDate() != null ? d.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null)
                .price(Objects.requireNonNullElse(d.getPrice(), BigDecimal.ZERO))
                .imageUrl(variant != null ? variant.getImageUrl() : null)
                .note(d.getNote())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getExportCommandById(Long id) {
        return inventoryNoteRepository.findByIdWithDetails(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y lĂ¡Â»â€¡nh xuĂ¡ÂºÂ¥t."));
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getCheckCommandByCode(String code) {
        return inventoryNoteRepository.findByCodeWithDetails(code)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("KhÄ‚Â´ng tÄ‚Â¬m thĂ¡ÂºÂ¥y lĂ¡Â»â€¡nh kiĂ¡Â»Æ’m kho vĂ¡Â»â€ºi mÄ‚Â£: " + code));
    }
}


