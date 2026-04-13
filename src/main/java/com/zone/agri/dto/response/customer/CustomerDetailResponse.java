package com.zone.agri.dto.response.customer;

import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.CustomerStatus;
import com.zone.agri.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDetailResponse {
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private AuthProvider provider;
    private UserStatus userStatus;
    private LocalDateTime createdAt;

    private Long customerId;
    private CustomerStatus customerStatus;
    private String addressDetail;

    private Long totalOrders;
    private BigDecimal totalSpent;
    private Double reputationScore;
    private String riskLevel;
    private Boolean onlinePaymentOnly;

    private LocalDateTime lastOrderDate;
    private BigDecimal averageOrderValue;

    private List<CustomerAddressResponse> addresses;
    private List<CustomerInternalNoteResponse> internalNotes;
    private List<CustomerStatusLogResponse> statusLogs;
}
