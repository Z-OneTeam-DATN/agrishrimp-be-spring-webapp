package com.zone.agri.dto.response.customer;

import com.zone.agri.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerStatusLogResponse {
    private Long id;
    private UserStatus fromStatus;
    private UserStatus toStatus;
    private String reason;
    private String changedByName;
    private LocalDateTime createdAt;
}
