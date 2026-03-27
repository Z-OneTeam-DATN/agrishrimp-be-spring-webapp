package com.zone.agri.dto.request.order;

import lombok.Data;
import java.util.List;

@Data
public class HandoverCreateRequest {
    private List<Long> subOrderIds;
    private String carrier;
    private Double totalWeight;
}
