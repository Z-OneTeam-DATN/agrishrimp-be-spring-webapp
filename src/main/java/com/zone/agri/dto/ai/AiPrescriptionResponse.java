package com.zone.agri.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AiPrescriptionResponse {

    private boolean success;

    @JsonProperty("disease_info")
    private AiDiseaseInfo diseaseInfo;

    private List<String> causes;

    @JsonProperty("signs_summary")
    private String signsSummary;

    @JsonProperty("treatment_stages")
    private List<AiTreatmentStage> treatmentStages;
}
