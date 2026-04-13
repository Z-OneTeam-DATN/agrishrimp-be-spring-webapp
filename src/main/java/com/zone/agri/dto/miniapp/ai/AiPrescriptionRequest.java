package com.zone.agri.dto.miniapp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiPrescriptionRequest {

    @JsonProperty("disease_code")
    private String diseaseCode;

    @JsonProperty("disease_name")
    private String diseaseName;

    @JsonProperty("user_symptoms")
    private String userSymptoms;

    // ideal_protocol đã được loại bỏ — Python AI tự lookup từ protocol_map.py (Knowledge Base)

    @JsonProperty("available_stock")
    private List<AiStockItem> availableStock;
}
