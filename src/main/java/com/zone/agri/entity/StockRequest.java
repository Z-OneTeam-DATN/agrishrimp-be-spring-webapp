package com.zone.agri.entity;

import com.zone.agri.entity.enums.StockRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StockRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "request_code", length = 30, nullable = false, unique = true)
    String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_branch_id", nullable = false)
    Branch fromBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_branch_id", nullable = false)
    Branch toBranch;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    StockRequestStatus status = StockRequestStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    String note;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    String rejectReason;

    @Column(name = "approved_by")
    Long approvedBy;

    @Column(name = "approved_at")
    LocalDateTime approvedAt;

    @OneToMany(mappedBy = "stockRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<StockRequestItem> items = new ArrayList<>();
}
