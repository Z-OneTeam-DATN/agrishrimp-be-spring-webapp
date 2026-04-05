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
        DISEASE_CATEGORY_KEYWORDS.put("EMS",    List.of("kháng sinh", "vi sinh", "gan", "đường ruột", "hóa chất"));
        DISEASE_CATEGORY_KEYWORDS.put("AHPND",  List.of("kháng sinh", "vi sinh", "gan", "hóa chất", "diệt khuẩn"));
        // Bệnh đốm trắng (WSSV)
        DISEASE_CATEGORY_KEYWORDS.put("WSSV",   List.of("miễn dịch", "vi sinh", "khoáng", "vitamin", "diệt khuẩn"));
        // Hội chứng phân trắng (WFS)
        DISEASE_CATEGORY_KEYWORDS.put("WFS",    List.of("vi sinh", "đường ruột", "gan", "vitamin", "khoáng"));
        // Hoại tử gan tụy thể hoại tử (NHP)
        DISEASE_CATEGORY_KEYWORDS.put("NHP",    List.of("kháng sinh", "vi sinh", "gan", "hóa chất"));
        // Hoại tử cơ truyền nhiễm (IMNV)
        DISEASE_CATEGORY_KEYWORDS.put("IMNV",   List.of("miễn dịch", "vi sinh", "khoáng", "vitamin"));
        // Taura Syndrome Virus
        DISEASE_CATEGORY_KEYWORDS.put("TSV",    List.of("miễn dịch", "vi sinh", "khoáng", "vitamin"));
        // Bệnh còi / chậm lớn
        DISEASE_CATEGORY_KEYWORDS.put("STUNTED", List.of("vitamin", "khoáng", "tăng trưởng", "men vi sinh"));
        // Tôm khỏe mạnh / không phát hiện bệnh
        DISEASE_CATEGORY_KEYWORDS.put("NORMAL", List.of("vitamin", "khoáng", "men vi sinh", "tăng trưởng"));
        // Fallback mặc định
        DISEASE_CATEGORY_KEYWORDS.put("DEFAULT", List.of("thuốc", "vi sinh", "khoáng", "vitamin"));
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
        // Prefix match: "EMS_STAGE2" → "EMS"
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
