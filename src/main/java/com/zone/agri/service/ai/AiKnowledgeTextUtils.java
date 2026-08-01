package com.zone.agri.service.ai;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class AiKnowledgeTextUtils {

    // "tom" (tôm) va "bi" (bị) xuat hien trong HAU HET ten benh ("Tom bi X") va HAU HET cau mo ta
    // trieu chung cua nguoi dung ("tom ... bi ..."), nen khong mang tin hieu phan biet gi khi tinh
    // do trung token — bo qua o buoc tokenize (chi dung cho scoring) de ten benh khong tu thang
    // diem chi vi trung 2 tu de đem nay voi bat ky cau nao ma nguoi dung go, du trieu chung thuc
    // te khong khop. Danh sach co tinh, chi gom tu chac chan vo nghia trong moi truong hop khop.
    private static final Set<String> STOP_WORDS = Set.of("tom", "bi");

    private AiKnowledgeTextUtils() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    public static List<String> tokenize(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank() && !STOP_WORDS.contains(token))
                .distinct()
                .toList();
    }

    public static Set<String> keywordSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .map(AiKnowledgeTextUtils::normalize)
                .filter(part -> !part.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String buildSlug(String value, String fallback) {
        String normalized = normalize(value)
                .replace(' ', '-')
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? fallback : normalized;
    }
}
