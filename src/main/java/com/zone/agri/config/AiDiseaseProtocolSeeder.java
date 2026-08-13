package com.zone.agri.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiKnowledgeCategory;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiKnowledgeCategoryRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.ai.AiKnowledgeTextUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.startup.seed-data.enabled", havingValue = "true", matchIfMissing = true)
public class AiDiseaseProtocolSeeder implements CommandLineRunner {

    private static final String DEFAULT_AUTHOR_EMAIL = "cthiez@gmail.com";
    private static final List<String> PROTOCOL_RESOURCE_PATHS = List.of(
            "seed/ai-disease-protocols/WSSV.md",
            "seed/ai-disease-protocols/YHD.md",
            "seed/ai-disease-protocols/BG.md");
    private static final Pattern SUB_STAGE_HEADING =
            Pattern.compile("^#{2,4}\\s+(\\d+)\\.(\\d+)\\s+(?:—|-)\\s+(.+)$");
    private static final Map<String, DiseaseSeedMetadata> METADATA_OVERRIDES = Map.of(
            "WSSV", new DiseaseSeedMetadata(
                    "WSSV, bệnh đốm trắng, hội chứng đốm trắng, white spot disease, white spot syndrome, white spot syndrome virus",
                    "đốm trắng trên vỏ, vỏ đầu ngực có đốm trắng, tôm giảm ăn, tôm bỏ ăn, tôm đỏ thân, tôm hồng thân, bơi lờ đờ, bơi yếu, tấp mé, nổi đầu, chết rải rác, chết tăng nhanh, nghi wssv, pcr wssv",
                    List.of("White Spot Syndrome Virus (WSSV)", "Con giống hoặc vật chủ trung gian mang mầm bệnh", "Stress môi trường, biến động oxy, pH, nhiệt độ, độ mặn", "Lây lan qua nước, bùn, tôm bệnh, dụng cụ hoặc giáp xác mang virus"),
                    100),
            "YHD", new DiseaseSeedMetadata(
                    "YHD, YHV1, yellowhead, yellow head disease, bệnh đầu vàng, bệnh đầu vàng trên tôm",
                    "đầu vàng, đầu ngực vàng, gan tụy vàng, mang vàng, tôm ăn mạnh bất thường, giảm ăn đột ngột, ruột rỗng, bơi yếu, lờ đờ, chết rải rác, chết nhanh, nghi yhv1, pcr yhv1",
                    List.of("Yellow Head Virus genotype 1 (YHV1)", "Con giống hoặc nguồn nước mang mầm bệnh", "Stress môi trường làm bệnh bùng phát nhanh hơn", "Lây lan qua nước, tôm bệnh, bùn, dụng cụ và giáp xác nguy cơ"),
                    95),
            "BG", new DiseaseSeedMetadata(
                    "BG, hội chứng đen mang, đen mang, tổn thương mang, black gill syndrome, bacterial-associated black gill",
                    "mang vàng, mang nâu, mang đen, mang bám cặn, mang bám nhớt, tôm giảm ăn, bơi chậm, nổi đầu, tấp mé, tập trung quanh quạt, chết rải rác, chất hữu cơ cao, đáy ao bẩn, nghi vi khuẩn dạng sợi",
                    List.of("Chất hữu cơ và cặn bẩn tích tụ trên mang", "Đáy ao xấu, khí độc hoặc oxy thấp gây stress hô hấp", "Vi khuẩn dạng sợi Leucothrix spp. hoặc vi khuẩn cơ hội như Vibrio spp.", "Có thể liên quan ký sinh trùng, nấm hoặc sinh vật bám ngoài nên cần chẩn đoán phân biệt"),
                    90));

    private final ObjectMapper objectMapper;
    private final AiKnowledgeCategoryRepository categoryRepository;
    private final AiDiseaseKnowledgeRepository diseaseRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.startup.seed-data.ai-disease-protocols.enabled:true}")
    private boolean enabled;

    @Value("${app.startup.seed-data.ai-disease-protocols.author-email:" + DEFAULT_AUTHOR_EMAIL + "}")
    private String authorEmail;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info(">>> SEED PHÁC ĐỒ AI DOCTOR CHUẨN ĐANG TẮT.");
            return;
        }

        Long authorUserId = resolveAuthorUserId();
        int seeded = 0;
        for (String resourcePath : PROTOCOL_RESOURCE_PATHS) {
            try {
                ProtocolDraft protocol = parseProtocol(resourcePath);
                upsertDiseaseProtocol(protocol, authorUserId);
                seeded++;
            } catch (Exception exception) {
                log.error("Không thể seed phác đồ AI Doctor từ {}: {}", resourcePath, exception.getMessage(), exception);
                throw new IllegalStateException("Không thể seed phác đồ AI Doctor từ " + resourcePath, exception);
            }
        }
        log.info(">>> ĐÃ SEED/CẬP NHẬT {} PHÁC ĐỒ AI DOCTOR CHUẨN.", seeded);
    }

    private Long resolveAuthorUserId() {
        String normalizedEmail = trimToNull(authorEmail);
        if (normalizedEmail == null) {
            normalizedEmail = DEFAULT_AUTHOR_EMAIL;
        }

        Optional<User> author = userRepository.findByEmail(normalizedEmail);
        if (author.isPresent()) {
            return author.get().getId();
        }

        log.warn("Không tìm thấy user {} để gắn createdBy/updatedBy cho phác đồ AI Doctor.", normalizedEmail);
        return null;
    }

    private void upsertDiseaseProtocol(ProtocolDraft protocol, Long authorUserId) throws JsonProcessingException {
        AiDiseaseKnowledge entity = diseaseRepository.findByCode(protocol.code)
                .orElseGet(() -> AiDiseaseKnowledge.builder().code(protocol.code).build());
        DiseaseSeedMetadata metadata = METADATA_OVERRIDES.get(protocol.code);
        if (metadata == null) {
            throw new IllegalStateException("Thiếu metadata seed cho mã bệnh " + protocol.code);
        }

        entity.setCode(protocol.code);
        entity.setNameVi(protocol.nameVi);
        entity.setNameEn(protocol.nameEn);
        entity.setCategory(resolveOrCreateCategory(protocol.categoryName));
        entity.setAliasesRaw(metadata.aliasesRaw());
        entity.setSymptomKeywordsRaw(metadata.symptomKeywordsRaw());
        entity.setSignsSummary(buildSignsSummary(protocol));
        entity.setCausesJson(objectMapper.writeValueAsString(metadata.causes()));
        entity.setTreatmentStagesJson(objectMapper.writeValueAsString(protocol.stages.stream()
                .map(StageDraft::toJsonMap)
                .toList()));
        if (trimToNull(entity.getImageUrlsJson()) == null) {
            entity.setImageUrlsJson("[]");
        }
        entity.setConfidenceThreshold(0.65D);
        entity.setMatchThreshold(0.40D);
        entity.setEnabled(true);
        entity.setPriority(metadata.priority());
        entity.setCanonical(true);
        entity.setStatus(AiKnowledgeStatus.APPROVED);
        entity.setReviewNote(null);

        AiDiseaseKnowledge saved = diseaseRepository.saveAndFlush(entity);
        syncAuditUser(saved.getId(), authorUserId);
        log.info("Seeded AI disease protocol: code={}, id={}, stages={}",
                saved.getCode(), saved.getId(), protocol.stages.size());
    }

    private AiKnowledgeCategory resolveOrCreateCategory(String categoryName) {
        String normalized = requiredText(categoryName, "Tên danh mục phác đồ không được trống");
        return categoryRepository.findAll().stream()
                .filter(category -> normalized.equalsIgnoreCase(category.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(AiKnowledgeCategory.builder()
                        .name(normalized)
                        .slug(resolveCategorySlug(normalized))
                        .enabled(true)
                        .sortOrder(0)
                        .build()));
    }

    private String resolveCategorySlug(String name) {
        String baseSlug = AiKnowledgeTextUtils.buildSlug(name, "ai-disease-category");
        String candidate = baseSlug;
        int suffix = 2;
        while (categoryRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix++;
        }
        return candidate;
    }

    private void syncAuditUser(Long diseaseId, Long authorUserId) {
        if (diseaseId == null || authorUserId == null) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE ai_disease_knowledge SET created_by_user_id = ?, updated_by_user_id = ? WHERE id = ?",
                authorUserId,
                authorUserId,
                diseaseId);
    }

    private ProtocolDraft parseProtocol(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("Không tìm thấy resource " + resourcePath);
        }

        String markdown;
        try (var inputStream = resource.getInputStream()) {
            markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "");
        }
        List<String> lines = markdown.lines().toList();

        ProtocolDraft protocol = new ProtocolDraft();
        protocol.code = normalizeDiseaseCode(requiredText(
                extractMetadataValue(lines, "**Mã bệnh:**"),
                "Thiếu mã bệnh trong " + resourcePath));
        protocol.nameVi = requiredText(extractMetadataValue(lines, "**Tên bệnh (VN):**"), "Thiếu tên bệnh VN");
        protocol.nameEn = trimToNull(extractMetadataValue(lines, "**Tên tiếng Anh:**"));
        protocol.categoryName = requiredText(extractMetadataValue(lines, "**Danh mục:**"), "Thiếu danh mục");

        StageDraft currentStage = null;
        SubStageDraft currentSubStage = null;
        ParserSection activeSection = ParserSection.NONE;
        boolean insideFence = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (isEndSectionHeading(trimmed)) {
                break;
            }

            if (trimmed.startsWith("```")) {
                insideFence = !insideFence;
                continue;
            }

            Matcher subStageMatcher = SUB_STAGE_HEADING.matcher(trimmed);
            if (subStageMatcher.matches()) {
                addSubStage(currentStage, currentSubStage);
                currentSubStage = new SubStageDraft(stripMarkdown(subStageMatcher.group(3)));
                activeSection = ParserSection.NONE;
                continue;
            }

            if (isMajorStageHeading(trimmed)) {
                addSubStage(currentStage, currentSubStage);
                addStage(protocol, currentStage);
                currentStage = new StageDraft(cleanHeadingTitle(trimmed));
                currentSubStage = null;
                activeSection = ParserSection.NONE;
                continue;
            }

            if (currentStage != null && startsWithContent(trimmed, "Tên giai đoạn lớn:")) {
                currentStage.stageTitle = requiredText(afterColon(stripHeadingPrefix(trimmed)), "Tên giai đoạn lớn trống");
                continue;
            }

            if (currentStage != null && startsWithContent(trimmed, "Dấu hiệu bổ sung:")) {
                currentStage.stageSigns = afterColon(stripMarkdown(trimmed));
                continue;
            }

            if (currentStage != null && startsWithContent(trimmed, "Mục tiêu xử lý:")) {
                currentStage.treatmentGoal = afterColon(stripMarkdown(trimmed));
                continue;
            }

            if (currentSubStage == null) {
                continue;
            }

            if (startsWithContent(trimmed, "Sản phẩm áp dụng:")) {
                splitProductNames(afterColon(stripMarkdown(trimmed))).forEach(currentSubStage::addExtraProductName);
                activeSection = ParserSection.NONE;
                continue;
            }

            if (startsWithContent(trimmed, "Hướng dẫn xử lý:")) {
                activeSection = ParserSection.INSTRUCTIONS;
                continue;
            }

            if (startsWithContent(trimmed, "Tên thuốc/sản phẩm khác:")) {
                activeSection = ParserSection.EXTRA_PRODUCTS;
                continue;
            }

            if (!insideFence || trimmed.isBlank()) {
                continue;
            }

            if (activeSection == ParserSection.INSTRUCTIONS) {
                currentSubStage.addInstruction(stripBullet(trimmed));
            } else if (activeSection == ParserSection.EXTRA_PRODUCTS) {
                currentSubStage.addExtraProductName(stripBullet(trimmed));
            }
        }

        addSubStage(currentStage, currentSubStage);
        addStage(protocol, currentStage);
        if (protocol.stages.isEmpty()) {
            throw new IllegalStateException("Không parse được giai đoạn nào từ " + resourcePath);
        }
        return protocol;
    }

    private String buildSignsSummary(ProtocolDraft protocol) {
        return protocol.stages.stream()
                .map(stage -> {
                    String title = trimToNull(stage.stageTitle);
                    String signs = trimToNull(stage.stageSigns);
                    if (title == null) {
                        return signs;
                    }
                    if (signs == null) {
                        return title;
                    }
                    return title + ": " + signs;
                })
                .filter(Objects::nonNull)
                .toList()
                .stream()
                .reduce((left, right) -> left + " | " + right)
                .orElse(protocol.nameVi);
    }

    private String extractMetadataValue(List<String> lines, String marker) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith(marker))
                .map(line -> line.substring(marker.length()).trim())
                .map(this::stripMarkdown)
                .findFirst()
                .orElse(null);
    }

    private void addStage(ProtocolDraft protocol, StageDraft stage) {
        if (stage == null) {
            return;
        }
        if (trimToNull(stage.stageTitle) == null && stage.subStages.isEmpty()) {
            return;
        }
        protocol.stages.add(stage);
    }

    private void addSubStage(StageDraft stage, SubStageDraft subStage) {
        if (stage == null || subStage == null) {
            return;
        }
        if (trimToNull(subStage.subStageTitle) == null
                && subStage.instructions.isEmpty()
                && subStage.extraProductNames.isEmpty()) {
            return;
        }
        stage.subStages.add(subStage);
    }

    private boolean isMajorStageHeading(String value) {
        if (!value.startsWith("# ") || value.startsWith("##")) {
            return false;
        }
        String content = stripMarkdown(stripHeadingPrefix(value)).toUpperCase(Locale.ROOT);
        return content.contains("GIAI ĐOẠN LỚN") || content.contains("GIAI ĐOẠN SAU");
    }

    private boolean isEndSectionHeading(String value) {
        if (!value.startsWith("#")) {
            return false;
        }
        String content = stripMarkdown(stripHeadingPrefix(value)).toUpperCase(Locale.ROOT);
        return content.startsWith("TÓM TẮT LUỒNG") || content.startsWith("NGUYÊN TẮC");
    }

    private boolean startsWithContent(String line, String prefix) {
        return stripMarkdown(stripHeadingPrefix(line)).startsWith(prefix);
    }

    private String cleanHeadingTitle(String heading) {
        String value = stripMarkdown(stripHeadingPrefix(heading))
                .replaceFirst("^[^\\p{L}\\p{N}]+", "")
                .trim();
        if (value.isBlank()) {
            return "Giai đoạn";
        }
        return value;
    }

    private String stripHeadingPrefix(String value) {
        return value == null ? "" : value.replaceFirst("^#+\\s*", "").trim();
    }

    private String afterColon(String value) {
        int index = value.indexOf(':');
        return index >= 0 ? stripMarkdown(value.substring(index + 1)) : stripMarkdown(value);
    }

    private String stripMarkdown(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("**", "")
                .replace("*", "")
                .replace("`", "")
                .trim();
    }

    private List<String> splitProductNames(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return List.of();
        }
        return List.of(normalized.split("\\s*;\\s*")).stream()
                .map(this::stripMarkdown)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private String stripBullet(String value) {
        String clean = stripMarkdown(value).replaceFirst("^[-•]\\s*", "").trim();
        return clean;
    }

    private String normalizeDiseaseCode(String rawCode) {
        return rawCode.split("/")[0].trim().toUpperCase(Locale.ROOT);
    }

    private String requiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private enum ParserSection {
        NONE,
        INSTRUCTIONS,
        EXTRA_PRODUCTS
    }

    private record DiseaseSeedMetadata(
            String aliasesRaw,
            String symptomKeywordsRaw,
            List<String> causes,
            int priority) {
    }

    private static class ProtocolDraft {
        private String code;
        private String nameVi;
        private String nameEn;
        private String categoryName;
        private final List<StageDraft> stages = new ArrayList<>();
    }

    private static class StageDraft {
        private String stageTitle;
        private String stageSigns;
        private String treatmentGoal;
        private final List<SubStageDraft> subStages = new ArrayList<>();

        private StageDraft(String stageTitle) {
            this.stageTitle = stageTitle;
        }

        private Map<String, Object> toJsonMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("stageTitle", stageTitle);
            value.put("stageSigns", stageSigns);
            value.put("treatmentGoal", treatmentGoal);
            value.put("instructions", List.of());
            value.put("productIds", List.of());
            value.put("extraProductNames", List.of());
            value.put("subStages", subStages.stream().map(SubStageDraft::toJsonMap).toList());
            return value;
        }
    }

    private static class SubStageDraft {
        private final String subStageTitle;
        private final List<String> instructions = new ArrayList<>();
        private final Set<String> extraProductNames = new LinkedHashSet<>();

        private SubStageDraft(String subStageTitle) {
            this.subStageTitle = subStageTitle;
        }

        private void addInstruction(String value) {
            if (value != null && !value.isBlank()) {
                instructions.add(value.trim());
            }
        }

        private void addExtraProductName(String value) {
            if (value != null && !value.isBlank()) {
                extraProductNames.add(value.trim());
            }
        }

        private Map<String, Object> toJsonMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("subStageTitle", subStageTitle);
            value.put("instructions", instructions);
            value.put("productIds", List.of());
            value.put("extraProductNames", List.copyOf(extraProductNames));
            return value;
        }
    }
}
