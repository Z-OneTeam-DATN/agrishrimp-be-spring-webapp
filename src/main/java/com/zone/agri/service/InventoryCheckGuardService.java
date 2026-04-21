package com.zone.agri.service;

import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.enums.InventoryCheckWorkflowStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.InventoryNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryCheckGuardService {
    private static final Set<InventoryCheckWorkflowStatus> LOCKING_STATUSES = EnumSet.of(
            InventoryCheckWorkflowStatus.COUNTING_INIT,
            InventoryCheckWorkflowStatus.COUNTING_IN_PROGRESS,
            InventoryCheckWorkflowStatus.WAITING_FOR_ADJUSTMENT_APPROVAL
    );

    private final InventoryNoteRepository inventoryNoteRepository;

    public void assertNoOpenCheckForBranch(Long branchId, String actionLabel) {
        assertNoOpenCheckForBranch(branchId, null, actionLabel);
    }

    public void assertNoOpenCheckForBranch(Long branchId, Long excludeCheckId, String actionLabel) {
        if (branchId == null) {
            return;
        }

        List<InventoryNote> activeChecks = inventoryNoteRepository.findActiveChecksByBranchId(
                branchId,
                LOCKING_STATUSES,
                excludeCheckId
        );

        if (activeChecks.isEmpty()) {
            return;
        }

        String codes = activeChecks.stream()
                .map(note -> note.getCode() != null ? note.getCode() : String.valueOf(note.getId()))
                .collect(Collectors.joining(", "));

        throw new BadRequestException(
                "Kho đang có phiếu kiểm kê mở, tạm thời không thể " + actionLabel + ". Phiếu đang khóa: " + codes
        );
    }

    public Set<InventoryCheckWorkflowStatus> getLockingStatuses() {
        return LOCKING_STATUSES;
    }
}
