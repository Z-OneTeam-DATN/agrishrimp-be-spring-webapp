package com.zone.agri.dto.response.order;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplenishmentRequestResponse {
    private String message;
    private List<String> transferCodes;
    private List<String> purchaseRequestCodes;
}
