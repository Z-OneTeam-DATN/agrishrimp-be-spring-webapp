package com.zone.agri.service;

import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryNoteDetail;
import com.zone.agri.entity.enums.InventoryCheckScopeType;
import com.zone.agri.entity.enums.InventoryCheckWorkflowStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.InventoryNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryCheckGuardService {
    private static final Set<InventoryCheckWorkflowStatus> LOCKING_STATUSES = EnumSet.of(
            InventoryCheckWorkflowStatus.COUNTING,
            InventoryCheckWorkflowStatus.PENDING_APPROVAL,
            InventoryCheckWorkflowStatus.RECOUNT_REQUIRED,
            InventoryCheckWorkflowStatus.COUNTING_IN_PROGRESS,
            InventoryCheckWorkflowStatus.WAITING_FOR_ADJUSTMENT_APPROVAL
    );

    private static final int SKU_PREVIEW_LIMIT = 3;

    private final InventoryNoteRepository inventoryNoteRepository;

    public void assertCheckCanStart(
            Long branchId,
            InventoryCheckScopeType scopeType,
            Collection<Long> variantIds,
            Long excludeCheckId
    ) {
        InventoryCheckBlock block = findBlockingCheck(branchId, scopeType, variantIds, excludeCheckId);
        if (block == null) {
            return;
        }

        throw new BadRequestException(buildConflictMessage("bat dau kiem ke", block));
    }

    public void assertStockMutationAllowed(Long branchId, Collection<Long> variantIds, String actionLabel) {
        InventoryCheckBlock block = findBlockingCheck(branchId, null, variantIds, null);
        if (block == null) {
            return;
        }

        throw new BadRequestException(buildMutationMessage(actionLabel, block));
    }

    public void assertNoOpenCheckForBranch(Long branchId, String actionLabel) {
        assertStockMutationAllowed(branchId, null, actionLabel);
    }

    public void assertNoOpenCheckForBranch(Long branchId, Long excludeCheckId, String actionLabel) {
        InventoryCheckBlock block = findBlockingCheck(
                branchId,
                InventoryCheckScopeType.FULL_WAREHOUSE,
                null,
                excludeCheckId
        );
        if (block == null) {
            return;
        }

        throw new BadRequestException(buildConflictMessage(actionLabel, block));
    }

    public Set<InventoryCheckWorkflowStatus> getLockingStatuses() {
        return LOCKING_STATUSES;
    }

    private InventoryCheckBlock findBlockingCheck(
            Long branchId,
            InventoryCheckScopeType requestedScopeType,
            Collection<Long> requestedVariantIds,
            Long excludeCheckId
    ) {
        if (branchId == null) {
            return null;
        }

        Set<Long> normalizedVariantIds = normalizeVariantIds(requestedVariantIds);
        InventoryCheckScopeType normalizedScope = requestedScopeType != null
                ? requestedScopeType
                : (normalizedVariantIds.isEmpty() ? InventoryCheckScopeType.FULL_WAREHOUSE : InventoryCheckScopeType.SELECTED_VARIANTS);

        List<InventoryNote> activeChecks = inventoryNoteRepository.findActiveChecksByBranchId(
                branchId,
                LOCKING_STATUSES,
                excludeCheckId
        );

        for (InventoryNote note : activeChecks) {
            InventoryCheckScopeType noteScope = resolveScopeType(note);
            Set<Long> noteVariantIds = extractVariantIds(note);

            if (noteScope == InventoryCheckScopeType.FULL_WAREHOUSE
                    || normalizedScope == InventoryCheckScopeType.FULL_WAREHOUSE) {
                return new InventoryCheckBlock(note, noteScope, noteVariantIds, noteVariantIds);
            }

            Set<Long> overlap = new LinkedHashSet<>(noteVariantIds);
            overlap.retainAll(normalizedVariantIds);
            if (!overlap.isEmpty()) {
                return new InventoryCheckBlock(note, noteScope, noteVariantIds, overlap);
            }
        }

        return null;
    }

    private InventoryCheckScopeType resolveScopeType(InventoryNote note) {
        return note.getCheckScopeType() != null ? note.getCheckScopeType() : InventoryCheckScopeType.FULL_WAREHOUSE;
    }

    private Set<Long> extractVariantIds(InventoryNote note) {
        if (note.getDetails() == null) {
            return Set.of();
        }

        return note.getDetails().stream()
                .map(InventoryNoteDetail::getProductVariant)
                .filter(Objects::nonNull)
                .map(productVariant -> productVariant.getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> normalizeVariantIds(Collection<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Set.of();
        }

        return variantIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String buildConflictMessage(String actionLabel, InventoryCheckBlock block) {
        String normalizedActionLabel = normalizeActionLabel(actionLabel);
        String branchName = resolveBranchName(block.note());
        String code = resolveCode(block.note());
        if (block.scopeType() == InventoryCheckScopeType.FULL_WAREHOUSE) {
            return "Không thể " + normalizedActionLabel + ". Kho " + branchName
                    + " đang được kiểm kê toàn kho bởi phiếu " + code + ".";
        }

        return "Không thể " + normalizedActionLabel + ". Có " + block.overlapVariantIds().size()
                + " SKU trùng phạm vi với phiếu kiểm kê " + code + " tại kho " + branchName
                + formatSkuPreview(block.note(), block.overlapVariantIds()) + ".";
    }

    private String buildMutationMessage(String actionLabel, InventoryCheckBlock block) {
        String normalizedActionLabel = normalizeActionLabel(actionLabel);
        String branchName = resolveBranchName(block.note());
        String code = resolveCode(block.note());
        if (block.scopeType() == InventoryCheckScopeType.FULL_WAREHOUSE) {
            return "Không thể " + normalizedActionLabel + ". Kho " + branchName
                    + " đang được kiểm kê. Vui lòng hoàn tất hoặc hủy phiếu kiểm kê " + code + " trước khi tiếp tục.";
        }

        return "Không thể " + normalizedActionLabel + ". Có " + block.overlapVariantIds().size()
                + " sản phẩm trong chứng từ đang được kiểm kê tại kho " + branchName
                + formatSkuPreview(block.note(), block.overlapVariantIds())
                + ". Phiếu kiểm kê: " + code + ".";
    }

    private String formatSkuPreview(InventoryNote note, Set<Long> overlapVariantIds) {
        List<String> skuPreview = note.getDetails() == null ? List.of() : note.getDetails().stream()
                .filter(detail -> detail.getProductVariant() != null
                        && overlapVariantIds.contains(detail.getProductVariant().getId()))
                .map(detail -> detail.getProductVariant().getSku())
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .limit(SKU_PREVIEW_LIMIT)
                .toList();

        if (skuPreview.isEmpty()) {
            return "";
        }

        int remaining = Math.max(0, overlapVariantIds.size() - skuPreview.size());
        String preview = String.join(", ", skuPreview);
        if (remaining == 0) {
            return " (" + preview + ")";
        }

        return " (" + preview + " và " + remaining + " sản phẩm khác)";
    }

    private String resolveBranchName(InventoryNote note) {
        return note.getBranch() != null && note.getBranch().getName() != null
                ? note.getBranch().getName()
                : "kho hiện tại";
    }

    private String resolveCode(InventoryNote note) {
        return note.getCode() != null ? note.getCode() : String.valueOf(note.getId());
    }

    private String normalizeActionLabel(String actionLabel) {
        if (actionLabel == null || actionLabel.isBlank()) {
            return "thực hiện thao tác";
        }

        return switch (actionLabel.trim().toLowerCase()) {
            case "bat dau kiem ke" -> "bắt đầu kiểm kê";
            case "tao phieu dieu chuyen" -> "tạo phiếu điều chuyển";
            case "sua phieu dieu chuyen" -> "sửa phiếu điều chuyển";
            case "duyet phieu dieu chuyen" -> "duyệt phiếu điều chuyển";
            case "xac nhan dieu chuyen" -> "xác nhận điều chuyển";
            case "tao phieu nhap kho" -> "tạo phiếu nhập kho";
            case "cap nhat phieu nhap kho" -> "cập nhật phiếu nhập kho";
            default -> actionLabel;
        };
    }

    private record InventoryCheckBlock(
            InventoryNote note,
            InventoryCheckScopeType scopeType,
            Set<Long> noteVariantIds,
            Set<Long> overlapVariantIds
    ) {
    }
}
