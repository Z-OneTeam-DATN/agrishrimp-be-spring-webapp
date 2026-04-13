package com.zone.agri.dto.response.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddressResponse {
    private Long id;
    private String receiverName;
    private String receiverPhone;
    private String addressDetail;
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
