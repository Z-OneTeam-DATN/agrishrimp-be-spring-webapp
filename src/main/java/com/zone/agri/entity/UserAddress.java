package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "receiver_name", length = 50)
    String receiverName;

    @Column(name = "receiver_phone", length = 15)
    String receiverPhone;

    // Lưu mã Tỉnh/Huyện/Xã từ API bên ngoài (Lưu dạng String cho an toàn)
    @Column(name = "province_id", length = 10)
    String provinceId;

    @Column(name = "district_id", length = 10)
    String districtId;

    @Column(name = "ward_id", length = 10)
    String wardId;

    // Cột này sẽ lưu TOÀN BỘ chuỗi: "Số nhà 12, Phường A, Quận B, Tỉnh C"
    @Column(name = "address_detail", columnDefinition = "TEXT")
    String addressDetail;

    @Column(name = "is_default")
    Boolean isDefault;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // --- KHÓA NGOẠI DUY NHẤT LÀ USER ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    User user;

    @JsonProperty("wardCode")
    public String getWardCode() {
        return wardId;
    }
}
