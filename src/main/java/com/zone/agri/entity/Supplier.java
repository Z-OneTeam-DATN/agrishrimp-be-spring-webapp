package com.zone.agri.entity;

import com.zone.agri.entity.enums.SupplierStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "suppliers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20, unique = true, nullable = false)
    String code;

    @Column(name = "name", length = 255, nullable = false)
    String name;

    @Column(name = "tax_code", length = 50, unique = true)
    String taxCode;

    @Column(name = "contact_name", length = 100)
    String contactName;

    @Column(name = "phone", length = 20)
    String phone;

    @Column(name = "email", length = 100)
    String email;

    @Column(name = "province_id", length = 50)
    String provinceId;

    @Column(name = "address_detail", columnDefinition = "TEXT")
    String addressDetail;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    SupplierStatus status;

    @Column(name = "issue_date")
    java.time.LocalDate issueDate;

    @Column(name = "tax_authority", length = 255)
    String taxAuthority;

    @Column(name = "main_business_sector", columnDefinition = "TEXT")
    String mainBusinessSector;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by_user_id", updatable = false)
    Long createdByUserId;

    @LastModifiedBy
    @Column(name = "updated_by_user_id")
    Long updatedByUserId;

    @OneToMany(mappedBy = "supplier", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<InventoryNote> inventoryNotes;

    @ManyToMany(mappedBy = "suppliers", fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Set<Product> products = new HashSet<>();
}
