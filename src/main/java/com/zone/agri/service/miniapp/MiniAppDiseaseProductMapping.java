package com.zone.agri.service.miniapp;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ánh xạ disease_code → danh sách keyword tên danh mục sản phẩm.
 *
 * Phase BE-3: hardcode tạm làm nền tảng ranking candidate products.
 * Thiết kế đóng gói để Phase sau có thể thay bằng bảng DB
 * (disease_category_mapping) mà không cần đụng vào orchestration.
 *
 * Key: disease code (uppercase, có thể là prefix).
 * Value: list keyword lowercase, kiểm tra bằng String.contains() trên tên category.
 */
@Component
public class MiniAppDiseaseProductMapping {

    private static final Map<String, List<String>> DISEASE_CATEGORY_KEYWORDS = new HashMap<>();

    static {
        // Hội chứng chết sớm / Hoại tử gan tụy cấp (EMS / AHPND)
        DISEASE_CATEGORY_KEYWORDS.put("EMS",            List.of("kháng sinh", "kháng khuẩn", "vi sinh", "probiotic", "gan", "đường ruột", "hóa chất", "diệt khuẩn"));
        DISEASE_CATEGORY_KEYWORDS.put("AHPND",          List.of("kháng sinh", "kháng khuẩn", "vi sinh", "probiotic", "gan", "hóa chất", "diệt khuẩn"));
        // Bệnh đốm trắng (WSSV)
        DISEASE_CATEGORY_KEYWORDS.put("WSSV",           List.of("vi sinh", "probiotic", "khoáng", "vitamin", "hóa chất", "diệt khuẩn", "vôi"));
        // Hội chứng phân trắng / WFS (WSSV_BG trong YOLO)
        DISEASE_CATEGORY_KEYWORDS.put("WSSV_BG",        List.of("vi sinh", "probiotic", "đường ruột", "gan", "vitamin", "khoáng", "hóa chất"));
        // Viêm mang vi khuẩn (BG)
        DISEASE_CATEGORY_KEYWORDS.put("BG",             List.of("kháng sinh", "kháng khuẩn", "hóa chất", "diệt khuẩn", "vi sinh", "probiotic", "vitamin", "khoáng"));
        // Nhóm bệnh vi khuẩn tổng hợp
        DISEASE_CATEGORY_KEYWORDS.put("BACTERIAL_GROUP",List.of("kháng sinh", "kháng khuẩn", "vi sinh", "probiotic", "gan", "hóa chất", "diệt khuẩn", "vitamin", "khoáng"));
        // Bệnh đầu vàng (Yellowhead / YHV)
        DISEASE_CATEGORY_KEYWORDS.put("YELLOWHEAD",     List.of("vitamin", "khoáng", "hóa chất", "diệt khuẩn", "vôi", "vi sinh"));
        // Hoại tử gan tụy thể hoại tử (NHP)
        DISEASE_CATEGORY_KEYWORDS.put("NHP",            List.of("kháng sinh", "vi sinh", "gan", "hóa chất", "diệt khuẩn"));
        // Hoại tử cơ truyền nhiễm (IMNV)
        DISEASE_CATEGORY_KEYWORDS.put("IMNV",           List.of("vi sinh", "probiotic", "khoáng", "vitamin", "hóa chất"));
        // Taura Syndrome Virus
        DISEASE_CATEGORY_KEYWORDS.put("TSV",            List.of("vi sinh", "probiotic", "khoáng", "vitamin"));
        // Bệnh còi / chậm lớn
        DISEASE_CATEGORY_KEYWORDS.put("STUNTED",        List.of("vitamin", "khoáng", "tăng trưởng", "men vi sinh", "probiotic"));
        // Tôm khỏe mạnh
        DISEASE_CATEGORY_KEYWORDS.put("NORMAL",         List.of("vitamin", "khoáng", "men vi sinh", "probiotic", "tăng trưởng"));
        // Fallback mặc định — bao phủ rộng nhất có thể
        DISEASE_CATEGORY_KEYWORDS.put("DEFAULT",        List.of("thuốc", "kháng sinh", "hóa chất", "diệt khuẩn", "vi sinh", "probiotic", "khoáng", "vitamin", "gan", "đường ruột"));
    }

    /**
     * Trả về keywords danh mục phù hợp với diseaseCode.
     * Lookup theo thứ tự: chính xác → prefix → DEFAULT.
     */
    public List<String> getCategoryKeywords(String diseaseCode) {
        if (diseaseCode == null || diseaseCode.isBlank()) {
            return DISEASE_CATEGORY_KEYWORDS.getOrDefault("DEFAULT", Collections.emptyList());
        }
        String upper = diseaseCode.toUpperCase().trim();
        if (DISEASE_CATEGORY_KEYWORDS.containsKey(upper)) {
            return DISEASE_CATEGORY_KEYWORDS.get(upper);
        }
        // Prefix match: "EMS_STAGE2" → "EMS", "Yellowhead" → "YELLOWHEAD"
        return DISEASE_CATEGORY_KEYWORDS.entrySet().stream()
                .filter(e -> !"DEFAULT".equals(e.getKey()) && upper.startsWith(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElseGet(() -> DISEASE_CATEGORY_KEYWORDS.getOrDefault("DEFAULT", Collections.emptyList()));
    }

    /**
     * Kiểm tra tên category có chứa bất kỳ keyword nào của disease không.
     */
    public boolean categoryMatchesDisease(String categoryName, String diseaseCode) {
        if (categoryName == null || categoryName.isBlank()) return false;
        String lower = categoryName.toLowerCase();
        return getCategoryKeywords(diseaseCode).stream().anyMatch(lower::contains);
    }
}
