package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * Lưu lịch sử mỗi lần chẩn đoán bệnh tôm qua Zalo Mini App.
 *
 * Thiết kế:
 * - gắn theo user_id nội bộ (không dùng zaloId)
 * - lưu full JSON response đủ để FE render lại kết quả mà không cần gọi AI lại
 * - extends BaseEntity để có createdAt / updatedAt tự động qua AuditingEntityListener
 *
 * Phase BE-4: lưu sau mỗi diagnosis thành công (kể cả partial nếu prescription fail).
 * Phase BE-5+: có thể thêm imagePublicId (Cloudinary), purchaseSession, shared flag...
 */
@Entity
@Table(name = "miniapp_diagnosis_history",
        indexes = {
                @Index(name = "idx_diagnosis_history_user_id", columnList = "user_id"),
                @Index(name = "idx_diagnosis_history_user_created", columnList = "user_id, created_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MiniAppDiagnosisHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** ID user nội bộ — không lưu zaloId */
    @Column(name = "user_id", nullable = false)
    Long userId;

    /** URL ảnh chẩn đoán — null nếu chưa upload (Phase BE-5: Cloudinary) */
    @Column(name = "image_url", columnDefinition = "TEXT")
    String imageUrl;

    /** Triệu chứng người dùng mô tả — đã normalize (trim + truncate 500 ký tự) */
    @Column(name = "user_symptoms", columnDefinition = "TEXT")
    String userSymptoms;

    // --- Disease ---

    @Column(name = "final_disease_code", length = 50)
    String finalDiseaseCode;

    @Column(name = "final_disease_name_vi", length = 255)
    String finalDiseaseNameVi;

    @Column(name = "final_disease_name_en", length = 255)
    String finalDiseaseNameEn;

    @Column(name = "final_confidence_percent")
    Double finalConfidencePercent;

    // --- AI Output (JSON) ---

    /**
     * JSON array of TopPredictionResponse — top N dự đoán bệnh từ AI predict-image.
     * Deserialize lại bằng ObjectMapper để FE render danh sách dự đoán.
     */
    @Column(name = "top_predictions_json", columnDefinition = "TEXT")
    String topPredictionsJson;

    /**
     * JSON array of String — nguyên nhân gây bệnh từ AI prescription.
     * Null nếu prescription thất bại (graceful degradation).
     */
    @Column(name = "causes_json", columnDefinition = "TEXT")
    String causesJson;

    /**
     * Tóm tắt biểu hiện bệnh từ AI prescription.
     * Null nếu prescription thất bại.
     */
    @Column(name = "signs_summary", columnDefinition = "TEXT")
    String signsSummary;

    /**
     * JSON array of TreatmentStageResponse — phác đồ điều trị kèm sản phẩm gợi ý.
     * Lưu full structure đủ để FE render mà không cần gọi AI lại.
     * Null nếu prescription thất bại.
     */
    @Column(name = "treatment_stages_json", columnDefinition = "TEXT")
    String treatmentStagesJson;

    /** Purchase link — Phase BE-4 placeholder (null), Phase BE-5: bổ sung */
    @Column(name = "purchase_url", columnDefinition = "TEXT")
    String purchaseUrl;
}
