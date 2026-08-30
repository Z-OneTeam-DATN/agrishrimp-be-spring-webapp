package com.zone.agri.entity;

import com.zone.agri.entity.enums.ReturnIssueType;
import com.zone.agri.entity.enums.ReturnRefundMethod;
import com.zone.agri.entity.enums.ReturnRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class ReturnRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 30, nullable = false, unique = true)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40, nullable = false)
    ReturnRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", length = 40, nullable = false)
    ReturnIssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", length = 40, nullable = false)
    ReturnRefundMethod refundMethod;

    @Column(name = "requires_physical_return", nullable = false)
    Boolean requiresPhysicalReturn;

    @Column(name = "customer_name", length = 150, nullable = false)
    String customerName;

    @Column(name = "customer_phone", length = 20, nullable = false)
    String customerPhone;

    @Column(name = "customer_email", length = 150)
    String customerEmail;

    @Column(name = "bank_account_name", length = 150)
    String bankAccountName;

    @Column(name = "bank_account_number", length = 50)
    String bankAccountNumber;

    @Column(name = "bank_name", length = 150)
    String bankName;

    @Column(name = "bank_branch", length = 150)
    String bankBranch;

    @Column(name = "reason", length = 255, nullable = false)
    String reason;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    String description;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    String rejectReason;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    String internalNote;

    @Column(name = "total_refund_amount", precision = 38, scale = 2, nullable = false)
    BigDecimal totalRefundAmount;

    @Column(name = "approved_at")
    LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    LocalDateTime rejectedAt;

    @Column(name = "received_at")
    LocalDateTime receivedAt;

    @Column(name = "refunded_at")
    LocalDateTime refundedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch branch;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Set<ReturnRequestItem> items = new LinkedHashSet<>();

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Set<ReturnRequestEvidence> evidences = new LinkedHashSet<>();
}
