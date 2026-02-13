package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.CustomerGender;
import com.zone.agri.entity.enums.CustomerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    private String email;

    // ❌ ĐÃ XÓA CustomerType

    @Enumerated(EnumType.STRING)
    private CustomerGender gender;

    // Địa chỉ
    private String provinceId;
    private String districtId;
    private String wardId;
    private String addressDetail;

    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    @Column(columnDefinition = "TEXT")
    private String note;

    // Liên kết với User để đăng nhập
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonManagedReference
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}