package com.zone.agri.entity.enums;

public enum InventoryCheckWorkflowStatus {
    DRAFT,
    COUNTING,
    PENDING_APPROVAL,
    RECOUNT_REQUIRED,
    COMPLETED,
    CANCELLED,

    // Legacy values kept for backward-compatible reads.
    COUNTING_INIT,
    COUNTING_IN_PROGRESS,
    WAITING_FOR_ADJUSTMENT_APPROVAL,
    COUNTING_COMPLETED;

    public InventoryCheckWorkflowStatus toCanonical() {
        return switch (this) {
            case COUNTING_INIT -> DRAFT;
            case COUNTING_IN_PROGRESS -> COUNTING;
            case WAITING_FOR_ADJUSTMENT_APPROVAL -> PENDING_APPROVAL;
            case COUNTING_COMPLETED -> COMPLETED;
            default -> this;
        };
    }

    public boolean isLockingStatus() {
        InventoryCheckWorkflowStatus canonical = toCanonical();
        return canonical == COUNTING
                || canonical == PENDING_APPROVAL
                || canonical == RECOUNT_REQUIRED;
    }
}
