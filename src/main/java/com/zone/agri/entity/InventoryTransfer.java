package com.zone.agri.entity;

import com.zone.agri.entity.enums.InventoryTransferStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "inventory_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "transfer_code", length = 20)
    String transferCode;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PENDING', 'SHIPPING', 'COMPLETED', 'CANCELLED')")
    InventoryTransferStatus status;

    @Column(name = "total_quantity")
    Integer totalQuantity;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // --- KHÓA NGOẠI ---


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch fromBranch;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch toBranch;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User sender;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User receiver;


    @OneToMany(mappedBy = "inventoryTransfer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    List<InventoryNoteDetail> details;

}