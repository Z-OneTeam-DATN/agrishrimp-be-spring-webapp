package com.zone.agri.common;

import com.zone.agri.entity.Category;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.exception.BadRequestException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class InventoryExpiryPolicy {
    private static final List<String> MANAGED_CATEGORY_KEYWORDS = List.of(
            "thuoc",
            "hoa chat",
            "thuc an",
            "che pham",
            "men vi sinh",
            "khoang",
            "dinh duong"
    );

    private InventoryExpiryPolicy() {
    }

    public static boolean requiresLotAndExpiry(ProductVariant variant) {
        Product product = variant != null ? variant.getProduct() : null;
        Category category = product != null ? product.getCategory() : null;
        while (category != null) {
            String normalizedName = normalize(category.getName());
            if (MANAGED_CATEGORY_KEYWORDS.stream().anyMatch(normalizedName::contains)) {
                return true;
            }
            category = category.getParent();
        }
        return false;
    }

    public static void assertRequiredForReceipt(ProductVariant variant, String batchNumber, LocalDateTime expiryDate) {
        if (!requiresLotAndExpiry(variant)) {
            return;
        }

        String sku = variant != null ? Objects.toString(variant.getSku(), "N/A") : "N/A";
        if (batchNumber == null || batchNumber.isBlank()) {
            throw new BadRequestException("SKU " + sku + ": nhóm hàng này bắt buộc nhập số lô.");
        }
        if (expiryDate == null) {
            throw new BadRequestException("SKU " + sku + ": nhóm hàng này bắt buộc nhập hạn sử dụng.");
        }
        if (expiryDate.toLocalDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("SKU " + sku + ": hạn sử dụng không được là ngày đã hết hạn khi nhập kho.");
        }
    }

    public static void assertRequiredForInventoryCheck(ProductVariant variant, String batchNumber, LocalDateTime expiryDate) {
        if (!requiresLotAndExpiry(variant)) {
            return;
        }

        String sku = variant != null ? Objects.toString(variant.getSku(), "N/A") : "N/A";
        if (batchNumber == null || batchNumber.isBlank()) {
            throw new BadRequestException("SKU " + sku + ": kiểm kê nhóm hàng này bắt buộc có số lô.");
        }
        if (expiryDate == null) {
            throw new BadRequestException("SKU " + sku + ": kiểm kê nhóm hàng này bắt buộc có hạn sử dụng.");
        }
    }

    private static String normalize(String value) {
        String raw = Objects.toString(value, "").toLowerCase(Locale.ROOT);
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }
}
