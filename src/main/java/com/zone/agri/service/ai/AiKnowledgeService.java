package com.zone.agri.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.ai.AiChatResponse;
import com.zone.agri.dto.ai.AiPredictResponse;
import com.zone.agri.dto.ai.AiPredictionItem;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.dto.response.ai.DiseaseResponse;
import com.zone.agri.dto.response.ai.SuggestedProductResponse;
import com.zone.agri.dto.response.ai.TopPredictionResponse;
import com.zone.agri.dto.response.ai.TreatmentStageResponse;
import com.zone.agri.dto.request.ai.AiDiseaseKnowledgeRequest;
import com.zone.agri.dto.request.ai.AiDoctorChatRequest;
import com.zone.agri.dto.request.ai.AiKeywordAnswerSetRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeCategoryRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeChatConfigRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeImportApplyRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeImportPreviewRowRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeTreatmentStageRequest;
import com.zone.agri.dto.request.ai.AiReviewCaseUpdateRequest;
import com.zone.agri.dto.response.ai.AiClarifyCandidateSummary;
import com.zone.agri.dto.response.ai.AiDiseaseKnowledgeResponse;
import com.zone.agri.dto.response.ai.AiDoctorChatPromptResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeCategoryResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeChatConfigResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeImportPreviewResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeImportPreviewRowResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeReportResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeReviewCaseResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeTreatmentStageResponse;
import com.zone.agri.dto.response.ai.AiKeywordAnswerSetResponse;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiKeywordAnswerSet;
import com.zone.agri.entity.AiKnowledgeCategory;
import com.zone.agri.entity.AiKnowledgeChatConfig;
import com.zone.agri.entity.AiKnowledgeChatLog;
import com.zone.agri.entity.AiKnowledgeReviewCase;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.enums.AiKnowledgeMatchType;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.entity.enums.AiReviewCaseReason;
import com.zone.agri.entity.enums.AiReviewCaseStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiKeywordAnswerSetRepository;
import com.zone.agri.repository.AiKnowledgeCategoryRepository;
import com.zone.agri.repository.AiKnowledgeChatConfigRepository;
import com.zone.agri.repository.AiKnowledgeChatLogRepository;
import com.zone.agri.repository.AiKnowledgeReviewCaseRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.service.aidoctor.AiDoctorProductSuggestionService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiKnowledgeService {

    // 30s cũ quá ngắn so với nhịp hỏi-đáp thật (nông dân đọc câu hỏi + gõ trả lời trên điện thoại
    // thường mất hơn 30s) — hầu hết các lượt hỏi trong AiDoctorClarifyService đều miss cache và
    // load lại toàn bộ bảng tri thức. Snapshot đã có evictApprovedSnapshot() invalidate ngay khi
    // admin/kỹ sư sửa tri thức, nên TTL dài hơn không làm mất tính "gần thời gian thực".
    private static final long SNAPSHOT_TTL_MS = 300_000L;
    private static final String DEFAULT_GREETING =
            "Xin chào, tôi sẽ tư vấn dựa trên kho tri thức đã được kỹ sư duyệt. "
                    + "Bạn có thể hỏi triệu chứng, tên bệnh hoặc gửi ảnh để hệ thống nhận diện.";
    private static final String DEFAULT_FALLBACK =
            "Xin lỗi, tôi chưa có đủ tri thức đã duyệt để trả lời chính xác. "
                    + "Vui lòng mô tả rõ hơn dấu hiệu hoặc liên hệ kỹ sư nông nghiệp để được hỗ trợ.";

    private final ObjectMapper objectMapper;
    private final AiKnowledgeCategoryRepository categoryRepository;
    private final AiKeywordAnswerSetRepository keywordAnswerSetRepository;
    private final AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;
    private final AiKnowledgeReviewCaseRepository reviewCaseRepository;
    private final AiKnowledgeChatConfigRepository chatConfigRepository;
    private final AiKnowledgeChatLogRepository chatLogRepository;
    private final ProductRepository productRepository;
    private final AiDoctorProductSuggestionService productSuggestionService;

    private final AtomicReference<ApprovedKnowledgeSnapshot> approvedSnapshotRef = new AtomicReference<>();
    private volatile long approvedSnapshotLoadedAt = 0L;

    @Transactional(readOnly = true)
    public List<AiKnowledgeCategoryResponse> getCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder", "name"))
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional
    public AiKnowledgeCategoryResponse createCategory(AiKnowledgeCategoryRequest request) {
        String name = requiredTrim(request.getName(), "Tên danh mục không được để trống");
        String slug = resolveCategorySlug(request.getSlug(), name, null);

        AiKnowledgeCategory entity = AiKnowledgeCategory.builder()
                .name(name)
                .slug(slug)
                .description(trimToNull(request.getDescription()))
                .enabled(defaultBoolean(request.getEnabled(), true))
                .sortOrder(defaultInt(request.getSortOrder(), 0))
                .build();

        evictApprovedSnapshot();
        return toCategoryResponse(categoryRepository.save(entity));
    }

    @Transactional
    public AiKnowledgeCategoryResponse updateCategory(Long id, AiKnowledgeCategoryRequest request) {
        AiKnowledgeCategory entity = categoryRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy danh mục tri thức với ID: " + id));

        String name = requiredTrim(request.getName(), "Tên danh mục không được để trống");
        entity.setName(name);
        entity.setSlug(resolveCategorySlug(request.getSlug(), name, id));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setEnabled(defaultBoolean(request.getEnabled(), entity.getEnabled()));
        entity.setSortOrder(defaultInt(request.getSortOrder(), entity.getSortOrder()));

        evictApprovedSnapshot();
        return toCategoryResponse(categoryRepository.save(entity));
    }

    @Transactional
    public void deleteCategory(Long id) {
        AiKnowledgeCategory entity = categoryRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy danh mục tri thức với ID: " + id));
        categoryRepository.delete(entity);
        evictApprovedSnapshot();
    }

    @Transactional(readOnly = true)
    public List<AiKeywordAnswerSetResponse> getKeywordAnswerSets() {
        return keywordAnswerSetRepository.findAll(Sort.by(Sort.Direction.DESC, "priority").and(Sort.by("name")))
                .stream()
                .map(this::toKeywordAnswerSetResponse)
                .toList();
    }

    @Transactional
    public AiKeywordAnswerSetResponse createKeywordAnswerSet(AiKeywordAnswerSetRequest request) {
        AiKeywordAnswerSet entity = AiKeywordAnswerSet.builder().build();
        applyKeywordAnswerSet(entity, request, true);
        evictApprovedSnapshot();
        return toKeywordAnswerSetResponse(keywordAnswerSetRepository.save(entity));
    }

    @Transactional
    public AiKeywordAnswerSetResponse updateKeywordAnswerSet(Long id, AiKeywordAnswerSetRequest request) {
        AiKeywordAnswerSet entity = keywordAnswerSetRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy bộ từ khóa với ID: " + id));
        applyKeywordAnswerSet(entity, request, false);
        evictApprovedSnapshot();
        return toKeywordAnswerSetResponse(keywordAnswerSetRepository.save(entity));
    }

    @Transactional
    public void deleteKeywordAnswerSet(Long id) {
        AiKeywordAnswerSet entity = keywordAnswerSetRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy bộ từ khóa với ID: " + id));
        keywordAnswerSetRepository.delete(entity);
        evictApprovedSnapshot();
    }

    @Transactional(readOnly = true)
    public List<AiDiseaseKnowledgeResponse> getDiseaseKnowledgeEntries() {
        return diseaseKnowledgeRepository.findAll(Sort.by(Sort.Direction.DESC, "priority").and(Sort.by("nameVi")))
                .stream()
                .map(this::toDiseaseKnowledgeResponse)
                .toList();
    }

    @Transactional
    public AiDiseaseKnowledgeResponse createDiseaseKnowledge(AiDiseaseKnowledgeRequest request) {
        AiDiseaseKnowledge entity = AiDiseaseKnowledge.builder().build();
        applyDiseaseKnowledge(entity, request, true);
        AiDiseaseKnowledge saved = diseaseKnowledgeRepository.save(entity);
        syncKeywordAnswerSetFromDisease(saved);
        evictApprovedSnapshot();
        return toDiseaseKnowledgeResponse(saved);
    }

    @Transactional
    public AiDiseaseKnowledgeResponse updateDiseaseKnowledge(Long id, AiDiseaseKnowledgeRequest request) {
        AiDiseaseKnowledge entity = diseaseKnowledgeRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy tri thức bệnh với ID: " + id));
        String previousCode = entity.getCode();
        applyDiseaseKnowledge(entity, request, false);
        AiDiseaseKnowledge saved = diseaseKnowledgeRepository.save(entity);
        if (!Objects.equals(previousCode, saved.getCode())) {
            keywordAnswerSetRepository.findByCode(derivedKeywordCodeForDisease(previousCode))
                    .ifPresent(keywordAnswerSetRepository::delete);
        }
        syncKeywordAnswerSetFromDisease(saved);
        evictApprovedSnapshot();
        return toDiseaseKnowledgeResponse(saved);
    }

    @Transactional
    public void deleteDiseaseKnowledge(Long id) {
        AiDiseaseKnowledge entity = diseaseKnowledgeRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy tri thức bệnh với ID: " + id));
        keywordAnswerSetRepository.findByCode(derivedKeywordCodeForDisease(entity.getCode()))
                .ifPresent(keywordAnswerSetRepository::delete);
        diseaseKnowledgeRepository.delete(entity);
        evictApprovedSnapshot();
    }

    /**
     * Chỉ tài khoản có quyền AI_KNOWLEDGE_APPROVE mới gọi được (xem AiKnowledgeController).
     * Duyệt xong bệnh mới thực sự được AI Doctor dùng để trả lời.
     */
    @Transactional
    public AiDiseaseKnowledgeResponse approveDiseaseKnowledge(Long id) {
        AiDiseaseKnowledge entity = diseaseKnowledgeRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy tri thức bệnh với ID: " + id));
        entity.setStatus(AiKnowledgeStatus.APPROVED);
        AiDiseaseKnowledge saved = diseaseKnowledgeRepository.save(entity);
        syncKeywordAnswerSetFromDisease(saved);
        evictApprovedSnapshot();
        return toDiseaseKnowledgeResponse(saved);
    }

    @Transactional
    public AiDiseaseKnowledgeResponse rejectDiseaseKnowledge(Long id) {
        AiDiseaseKnowledge entity = diseaseKnowledgeRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy tri thức bệnh với ID: " + id));
        entity.setStatus(AiKnowledgeStatus.DRAFT);
        AiDiseaseKnowledge saved = diseaseKnowledgeRepository.save(entity);
        syncKeywordAnswerSetFromDisease(saved);
        evictApprovedSnapshot();
        return toDiseaseKnowledgeResponse(saved);
    }

    /**
     * Kỹ sư chỉ nhập tri thức bệnh (phác đồ); bộ từ khóa & câu trả lời dùng cho chat
     * được suy ra tự động từ đây thay vì bắt kỹ sư soạn riêng một bản ghi FAQ.
     */
    private void syncKeywordAnswerSetFromDisease(AiDiseaseKnowledge disease) {
        String derivedCode = derivedKeywordCodeForDisease(disease.getCode());
        AiKeywordAnswerSet entity = keywordAnswerSetRepository.findByCode(derivedCode)
                .orElseGet(() -> AiKeywordAnswerSet.builder().build());

        PreparedDisease prepared = new PreparedDisease(
                disease,
                disease.getCategory(),
                prepareKeywords(disease.getNameVi(), disease.getAliasesRaw(), disease.getSymptomKeywordsRaw()));

        entity.setCode(derivedCode);
        entity.setName(disease.getNameVi());
        entity.setCategory(disease.getCategory());
        entity.setKeywordsRaw(buildDiseaseDerivedKeywords(disease));
        entity.setAnswerHtml(buildDiseaseAnswerHtml(prepared));
        entity.setEnabled(defaultBoolean(disease.getEnabled(), true));
        entity.setMatchThreshold(clampThreshold(entity.getMatchThreshold(), 0.35D));
        entity.setPriority(defaultInt(disease.getPriority(), 0));
        entity.setCanonical(defaultBoolean(disease.getCanonical(), false));
        entity.setStatus(disease.getStatus() != null ? disease.getStatus() : AiKnowledgeStatus.DRAFT);
        keywordAnswerSetRepository.save(entity);
    }

    private String derivedKeywordCodeForDisease(String diseaseCode) {
        return "DISEASE_" + diseaseCode;
    }

    private String buildDiseaseDerivedKeywords(AiDiseaseKnowledge disease) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (trimToNull(disease.getAliasesRaw()) != null) {
            keywords.addAll(Arrays.stream(disease.getAliasesRaw().split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList());
        }
        if (trimToNull(disease.getSymptomKeywordsRaw()) != null) {
            keywords.addAll(Arrays.stream(disease.getSymptomKeywordsRaw().split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList());
        }
        if (keywords.isEmpty()) {
            keywords.add(disease.getNameVi());
        }
        return String.join(", ", keywords);
    }

    @Transactional(readOnly = true)
    public AiKnowledgeChatConfigResponse getChatConfig() {
        return toChatConfigResponse(ensureChatConfig());
    }

    @Transactional
    public AiKnowledgeChatConfigResponse updateChatConfig(AiKnowledgeChatConfigRequest request) {
        AiKnowledgeChatConfig config = ensureChatConfig();
        config.setGreetingMessage(trimToNull(request.getGreetingMessage()) == null
                ? DEFAULT_GREETING
                : request.getGreetingMessage().trim());
        config.setFallbackMessage(trimToNull(request.getFallbackMessage()) == null
                ? DEFAULT_FALLBACK
                : request.getFallbackMessage().trim());
        return toChatConfigResponse(chatConfigRepository.save(config));
    }

    @Transactional(readOnly = true)
    public List<AiKnowledgeReviewCaseResponse> getReviewCases(AiReviewCaseStatus status) {
        List<AiKnowledgeReviewCase> data = status == null
                ? reviewCaseRepository.findTop100ByOrderByCreatedAtDesc()
                : reviewCaseRepository.findTop100ByStatusOrderByCreatedAtDesc(status);
        return data.stream().map(this::toReviewCaseResponse).toList();
    }

    @Transactional
    public AiKnowledgeReviewCaseResponse updateReviewCase(Long id, AiReviewCaseUpdateRequest request) {
        AiKnowledgeReviewCase entity = reviewCaseRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy review case với ID: " + id));

        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setMatchedKnowledgeCode(trimToNull(request.getMatchedKnowledgeCode()));
        entity.setResolutionNotes(trimToNull(request.getResolutionNotes()));
        return toReviewCaseResponse(reviewCaseRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<AiDoctorChatPromptResponse> getChatPrompts() {
        ApprovedKnowledgeSnapshot snapshot = getApprovedSnapshot();
        List<AiDoctorChatPromptResponse> prompts = new ArrayList<>();

        snapshot.keywordEntries.stream()
                .limit(6)
                .forEach(entry -> prompts.add(AiDoctorChatPromptResponse.builder()
                        .id("faq-" + entry.entity().getId())
                        .category(resolveCategoryLabel(entry.category()))
                        .label(entry.entity().getName())
                        .question(firstKeywordLabel(entry.keywords(), entry.entity().getName()))
                        .build()));

        if (prompts.size() < 8) {
            snapshot.diseaseEntries.stream()
                    .limit(8 - prompts.size())
                    .forEach(entry -> prompts.add(AiDoctorChatPromptResponse.builder()
                            .id("disease-" + entry.entity().getId())
                            .category(resolveCategoryLabel(entry.category()))
                            .label(entry.entity().getNameVi())
                            .question("Dấu hiệu của bệnh " + entry.entity().getNameVi() + " là gì?")
                            .build()));
        }

        return prompts;
    }

    @Transactional(readOnly = true)
    public AiKnowledgeImportPreviewResponse previewImport(MultipartFile file, String mode) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<AiKnowledgeImportPreviewRowResponse> rows = new ArrayList<>();

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                rows.add(parseImportRow(row));
            }

            long validRows = rows.stream().filter(AiKnowledgeImportPreviewRowResponse::isValid).count();
            return AiKnowledgeImportPreviewResponse.builder()
                    .mode(normalizeImportMode(mode))
                    .totalRows(rows.size())
                    .validRows((int) validRows)
                    .invalidRows(rows.size() - (int) validRows)
                    .rows(rows)
                    .build();
        } catch (IOException exception) {
            throw new RuntimeException("Không thể đọc file Excel import tri thức", exception);
        }
    }

    @Transactional
    public AiKnowledgeImportPreviewResponse applyImport(AiKnowledgeImportApplyRequest request) {
        String mode = normalizeImportMode(request.getMode());
        List<AiKnowledgeImportPreviewRowResponse> appliedRows = new ArrayList<>();

        for (AiKnowledgeImportPreviewRowRequest row : defaultList(request.getRows())) {
            AiKnowledgeImportPreviewRowResponse previewRow = validateImportRow(row);
            if (previewRow.isValid()) {
                if ("FAQ".equals(previewRow.getType())) {
                    upsertKeywordAnswerSet(row, mode);
                } else {
                    upsertDiseaseKnowledge(row, mode);
                }
            }
            appliedRows.add(previewRow);
        }

        evictApprovedSnapshot();
        long validRows = appliedRows.stream().filter(AiKnowledgeImportPreviewRowResponse::isValid).count();
        return AiKnowledgeImportPreviewResponse.builder()
                .mode(mode)
                .totalRows(appliedRows.size())
                .validRows((int) validRows)
                .invalidRows(appliedRows.size() - (int) validRows)
                .rows(appliedRows)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] buildImportTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("knowledge");
            Row header = sheet.createRow(0);
            List<String> headers = List.of(
                    "type",
                    "category",
                    "code",
                    "name",
                    "aliases",
                    "symptoms_keywords",
                    "answer_html",
                    "signs_summary",
                    "causes",
                    "treatment_stages",
                    "match_threshold",
                    "confidence_threshold",
                    "enabled");

            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
                sheet.setColumnWidth(index, 24 * 256);
            }

            Row faqExample = sheet.createRow(1);
            faqExample.createCell(0).setCellValue("FAQ");
            faqExample.createCell(1).setCellValue("Tư vấn chung");
            faqExample.createCell(2).setCellValue("FAQ_WHITE_SPOT");
            faqExample.createCell(3).setCellValue("Dấu hiệu bệnh đốm trắng");
            faqExample.createCell(4).setCellValue("bệnh đốm trắng, đốm trắng, white spot");
            faqExample.createCell(6).setCellValue("<p>Bệnh đốm trắng thường có biểu hiện thân tôm xuất hiện đốm trắng rõ trên vỏ.</p>");
            faqExample.createCell(10).setCellValue(0.4D);
            faqExample.createCell(12).setCellValue("true");

            Row diseaseExample = sheet.createRow(2);
            diseaseExample.createCell(0).setCellValue("DISEASE");
            diseaseExample.createCell(1).setCellValue("Bệnh virus");
            diseaseExample.createCell(2).setCellValue("WSSV");
            diseaseExample.createCell(3).setCellValue("Bệnh đốm trắng");
            diseaseExample.createCell(4).setCellValue("white spot syndrome virus, wssv");
            diseaseExample.createCell(5).setCellValue("đốm trắng trên vỏ, bơi lờ đờ, giảm ăn");
            diseaseExample.createCell(7).setCellValue("Tôm có đốm trắng, giảm ăn và yếu nhanh.");
            diseaseExample.createCell(8).setCellValue("môi trường biến động | nhiễm virus | mật độ nuôi cao");
            diseaseExample.createCell(9).setCellValue("Giai đoạn 1::Ổn định môi trường | Giảm sốc::101,102 || Giai đoạn 2::Theo dõi sức ăn | Bổ sung khoáng::103");
            diseaseExample.createCell(10).setCellValue(0.45D);
            diseaseExample.createCell(11).setCellValue(0.7D);
            diseaseExample.createCell(12).setCellValue("true");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Không thể tạo file mẫu import tri thức", exception);
        }
    }

    @Transactional(readOnly = true)
    public AiKnowledgeReportResponse getReport() {
        Map<String, Long> matchedTypeCounts = new LinkedHashMap<>();
        for (AiKnowledgeMatchType type : AiKnowledgeMatchType.values()) {
            matchedTypeCounts.put(type.name(), chatLogRepository.countByMatchedType(type));
        }

        List<AiKnowledgeReportResponse.QuestionFrequency> questions = chatLogRepository.findTopUnmatchedQuestions()
                .stream()
                .limit(10)
                .map(row -> AiKnowledgeReportResponse.QuestionFrequency.builder()
                        .question(String.valueOf(row[0]))
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        return AiKnowledgeReportResponse.builder()
                .totalQuestions(chatLogRepository.count())
                .matchedQuestions(chatLogRepository.countByMatched(true))
                .unmatchedQuestions(chatLogRepository.countByMatched(false))
                .reviewCaseCount(reviewCaseRepository.count())
                .matchedTypeCounts(matchedTypeCounts)
                .topUnmatchedQuestions(questions)
                .build();
    }

    @Transactional
    public AiChatResponse answerChat(
            AiDoctorChatRequest request,
            Long userId,
            String sourceChannel,
            boolean createReviewCaseWhenUnmatched) {
        String normalizedMessage = AiKnowledgeTextUtils.normalize(request.getMessage());
        ApprovedKnowledgeSnapshot snapshot = getApprovedSnapshot();
        AiKnowledgeChatConfig config = ensureChatConfig();
        String sessionId = trimToNull(request.getSessionId()) != null
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString();

        if (normalizedMessage.isBlank()) {
            return buildChatResponse(config.getGreetingMessage(), sessionId, getSuggestedActionLabels());
        }

        MatchOutcome outcome = resolveBestMatch(
                normalizedMessage,
                request.getDiagnosisContext() != null ? request.getDiagnosisContext().getDiseaseCode() : null,
                request.getDiagnosisContext() != null ? request.getDiagnosisContext().getDiseaseName() : null,
                snapshot);

        if (outcome.matched()) {
            persistChatLog(userId, sessionId, sourceChannel, request.getMessage(), outcome.answerHtml(),
                    true, outcome.matchType(), outcome.knowledgeCode(), outcome.score());

            return buildChatResponse(outcome.answerHtml(), sessionId, getSuggestedActionLabels());
        }

        persistChatLog(userId, sessionId, sourceChannel, request.getMessage(), config.getFallbackMessage(),
                false, null, null, 0D);

        if (createReviewCaseWhenUnmatched) {
            createReviewCase(
                    userId,
                    sessionId,
                    sourceChannel,
                    request.getMessage(),
                    null,
                    null,
                    null,
                    null,
                    0D,
                    AiReviewCaseReason.NO_KNOWLEDGE_MATCH);
        }

        return buildChatResponse(config.getFallbackMessage(), sessionId, getSuggestedActionLabels());
    }

    @Transactional(readOnly = true)
    public AiDoctorDiagnosisResponse enrichDiagnosis(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl,
            String userSymptoms,
            String sessionId,
            Long userId) {
        ApprovedKnowledgeSnapshot snapshot = getApprovedSnapshot();
        PreparedDisease directDisease = findDiseaseByPrediction(finalPrediction, snapshot);
        double confidence = finalPrediction.getConfidencePercent() == null ? 0D : finalPrediction.getConfidencePercent() / 100D;

        PreparedDisease resolvedDisease = directDisease;
        double diseaseScore = confidence;

        if (resolvedDisease == null && trimToNull(userSymptoms) != null) {
            MatchScore symptomMatch = matchDiseaseFromText(AiKnowledgeTextUtils.normalize(userSymptoms), snapshot.diseaseEntries);
            if (symptomMatch.score() > 0D) {
                resolvedDisease = symptomMatch.disease();
                diseaseScore = symptomMatch.score();
            }
        }

        if (resolvedDisease != null) {
            boolean confidencePass = confidence >= defaultDouble(resolvedDisease.entity().getConfidenceThreshold(), 0.65D);
            boolean matchPass = diseaseScore >= defaultDouble(resolvedDisease.entity().getMatchThreshold(), 0.40D);

            if (confidencePass || matchPass) {
                return buildDiagnosisResponse(
                        predictResponse,
                        finalPrediction,
                        diagnosisImageUrl,
                        resolvedDisease,
                        userSymptoms,
                        "DISEASE");
            }

            createReviewCase(
                    userId,
                    sessionId,
                    "AI_DOCTOR_DIAGNOSIS",
                    userSymptoms,
                    userSymptoms,
                    diagnosisImageUrl,
                    finalPrediction.getDiseaseCode(),
                    resolvedDisease.entity().getCode(),
                    diseaseScore,
                    AiReviewCaseReason.LOW_CONFIDENCE);

            return buildLowConfidenceDiagnosisResponse(predictResponse, finalPrediction, diagnosisImageUrl);
        }

        createReviewCase(
                userId,
                sessionId,
                "AI_DOCTOR_DIAGNOSIS",
                userSymptoms,
                userSymptoms,
                diagnosisImageUrl,
                finalPrediction.getDiseaseCode(),
                null,
                confidence,
                AiReviewCaseReason.NO_KNOWLEDGE_MATCH);
        return buildLowConfidenceDiagnosisResponse(predictResponse, finalPrediction, diagnosisImageUrl);
    }

    @Transactional(readOnly = true)
    public AiDoctorDiagnosisResponse buildPrescriptionFromApprovedKnowledge(String diseaseCode, Long diagnosisId) {
        PreparedDisease disease = findDiseaseByCodeFromSnapshot(diseaseCode, getApprovedSnapshot())
                .orElseThrow(() -> notFound("Chưa có tri thức APPROVED cho bệnh: " + diseaseCode));

        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId(diagnosisId != null ? String.valueOf(diagnosisId) : null)
                .causes(defaultList(readJsonList(disease.entity().getCausesJson(), new TypeReference<List<String>>() {
                })))
                .signsSummary(trimToNull(disease.entity().getSignsSummary()))
                .treatmentStages(toTreatmentStageResponses(disease.entity().getTreatmentStagesJson()))
                .build();
    }

    /**
     * Dùng cho luồng AI Doctor hỏi làm rõ bệnh (AiDoctorClarifyService): lọc một danh sách mã bệnh
     * (thường là top-N dự đoán YOLO) xuống còn đúng những mã đang APPROVED trong kho tri thức.
     * Mã nào không tồn tại/chưa duyệt sẽ bị loại thầm lặng — đây là lớp guardrail đầu tiên đảm bảo
     * Gemini chỉ bao giờ thấy một tập bệnh đã được kỹ sư + Admin xác nhận.
     */
    @Transactional(readOnly = true)
    public List<AiClarifyCandidateSummary> resolveApprovedCandidates(List<String> diseaseCodes) {
        if (diseaseCodes == null || diseaseCodes.isEmpty()) {
            return Collections.emptyList();
        }
        ApprovedKnowledgeSnapshot snapshot = getApprovedSnapshot();
        List<AiClarifyCandidateSummary> result = new ArrayList<>();
        for (String code : new LinkedHashSet<>(diseaseCodes)) {
            findDiseaseByCodeFromSnapshot(code, snapshot).ifPresent(disease -> result.add(toClarifyCandidateSummary(disease)));
        }
        return result;
    }

    /**
     * Guardrail cuối cùng trước khi chấp nhận một quyết định (DECISION) từ Gemini: mã bệnh đó phải
     * vẫn đang APPROVED tại thời điểm chốt, không chỉ dựa vào tập candidate đã khoá lúc bắt đầu phiên.
     */
    @Transactional(readOnly = true)
    public Optional<AiClarifyCandidateSummary> findApprovedCandidate(String diseaseCode) {
        return findDiseaseByCodeFromSnapshot(diseaseCode, getApprovedSnapshot()).map(this::toClarifyCandidateSummary);
    }

    private AiClarifyCandidateSummary toClarifyCandidateSummary(PreparedDisease disease) {
        return AiClarifyCandidateSummary.builder()
                .diseaseCode(disease.entity().getCode())
                .nameVi(disease.entity().getNameVi())
                .nameEn(disease.entity().getNameEn())
                .symptomKeywordsRaw(disease.entity().getSymptomKeywordsRaw())
                .signsSummary(disease.entity().getSignsSummary())
                .build();
    }

    private void applyKeywordAnswerSet(AiKeywordAnswerSet entity, AiKeywordAnswerSetRequest request, boolean isCreate) {
        String code = requiredTrim(request.getCode(), "Mã bộ từ khóa không được để trống").toUpperCase(Locale.ROOT);
        if (isCreate ? keywordAnswerSetRepository.existsByCode(code) : keywordAnswerSetRepository.existsByCodeAndIdNot(code, entity.getId())) {
            throw new ConflictException("Mã bộ từ khóa đã tồn tại: " + code, true);
        }

        entity.setCode(code);
        entity.setName(requiredTrim(request.getName(), "Tên bộ từ khóa không được để trống"));
        entity.setCategory(resolveCategory(request.getCategoryId()));
        entity.setKeywordsRaw(requiredTrim(request.getKeywordsRaw(), "Từ khóa không được để trống"));
        entity.setAnswerHtml(requiredTrim(request.getAnswerHtml(), "Câu trả lời không được để trống"));
        entity.setEnabled(defaultBoolean(request.getEnabled(), true));
        entity.setMatchThreshold(clampThreshold(request.getMatchThreshold(), 0.35D));
        entity.setPriority(defaultInt(request.getPriority(), 0));
        entity.setCanonical(defaultBoolean(request.getCanonical(), false));
        entity.setStatus(request.getStatus() != null ? request.getStatus() : AiKnowledgeStatus.DRAFT);
    }

    private void applyDiseaseKnowledge(AiDiseaseKnowledge entity, AiDiseaseKnowledgeRequest request, boolean isCreate) {
        String code = requiredTrim(request.getCode(), "Mã bệnh không được để trống").toUpperCase(Locale.ROOT);
        if (isCreate ? diseaseKnowledgeRepository.existsByCode(code) : diseaseKnowledgeRepository.existsByCodeAndIdNot(code, entity.getId())) {
            throw new ConflictException("Mã bệnh đã tồn tại: " + code, true);
        }

        entity.setCode(code);
        entity.setNameVi(requiredTrim(request.getNameVi(), "Tên bệnh không được để trống"));
        entity.setNameEn(trimToNull(request.getNameEn()));
        entity.setCategory(resolveCategory(request.getCategoryId()));
        entity.setAliasesRaw(trimToNull(request.getAliasesRaw()));
        entity.setSymptomKeywordsRaw(requiredTrim(request.getSymptomKeywordsRaw(), "Dấu hiệu bệnh không được để trống"));
        entity.setSignsSummary(requiredTrim(request.getSignsSummary(), "Mô tả dấu hiệu không được để trống"));
        entity.setCausesJson(writeJson(defaultList(request.getCauses())));
        entity.setTreatmentStagesJson(writeJson(toKnowledgeStages(defaultList(request.getTreatmentStages()))));
        entity.setConfidenceThreshold(clampThreshold(request.getConfidenceThreshold(), 0.65D));
        entity.setMatchThreshold(clampThreshold(request.getMatchThreshold(), 0.40D));
        entity.setEnabled(defaultBoolean(request.getEnabled(), true));
        entity.setPriority(defaultInt(request.getPriority(), 0));
        entity.setCanonical(defaultBoolean(request.getCanonical(), false));
        // Kỹ sư không được tự duyệt: mọi lần tạo/sửa qua đường này chỉ có thể vào DRAFT/IN_REVIEW/DISABLED.
        // Chuyển sang APPROVED bắt buộc phải qua approveDiseaseKnowledge() (quyền AI_KNOWLEDGE_APPROVE).
        AiKnowledgeStatus requestedStatus = request.getStatus() != null ? request.getStatus() : AiKnowledgeStatus.DRAFT;
        entity.setStatus(requestedStatus == AiKnowledgeStatus.APPROVED ? AiKnowledgeStatus.IN_REVIEW : requestedStatus);
    }

    private AiKnowledgeCategory resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> notFound("Không tìm thấy danh mục tri thức với ID: " + categoryId));
    }

    private AiKnowledgeCategory resolveOrCreateCategoryByName(String categoryName) {
        String normalized = requiredTrim(categoryName, "Tên danh mục import không được để trống");
        return categoryRepository.findAll(Sort.by("name")).stream()
                .filter(category -> normalized.equalsIgnoreCase(category.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(AiKnowledgeCategory.builder()
                        .name(normalized)
                        .slug(resolveCategorySlug(null, normalized, null))
                        .enabled(true)
                        .sortOrder(0)
                        .build()));
    }

    private String resolveCategorySlug(String requestedSlug, String name, Long currentId) {
        String baseSlug = AiKnowledgeTextUtils.buildSlug(trimToNull(requestedSlug) != null ? requestedSlug : name, "category");
        String candidate = baseSlug;
        int suffix = 2;

        while (currentId == null ? categoryRepository.existsBySlug(candidate) : categoryRepository.existsBySlugAndIdNot(candidate, currentId)) {
            candidate = baseSlug + "-" + suffix++;
        }

        return candidate;
    }

    private AiKnowledgeImportPreviewRowResponse parseImportRow(Row row) {
        AiKnowledgeImportPreviewRowRequest request = AiKnowledgeImportPreviewRowRequest.builder()
                .rowNumber(row.getRowNum() + 1)
                .type(getCellString(row, 0))
                .categoryName(getCellString(row, 1))
                .code(getCellString(row, 2))
                .name(getCellString(row, 3))
                .aliasesRaw(getCellString(row, 4))
                .symptomKeywordsRaw(getCellString(row, 5))
                .answerHtml(getCellString(row, 6))
                .signsSummary(getCellString(row, 7))
                .causes(splitPipeValues(getCellString(row, 8)))
                .treatmentStages(parseStages(getCellString(row, 9)))
                .matchThreshold(parseOptionalDouble(getCellString(row, 10)))
                .confidenceThreshold(parseOptionalDouble(getCellString(row, 11)))
                .enabled(parseOptionalBoolean(getCellString(row, 12)))
                .build();
        return validateImportRow(request);
    }

    private AiKnowledgeImportPreviewRowResponse validateImportRow(AiKnowledgeImportPreviewRowRequest row) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String type = normalizeImportType(row.getType());
        if (type == null) {
            errors.add("Cột type phải là FAQ hoặc DISEASE.");
        }
        if (trimToNull(row.getCategoryName()) == null) {
            errors.add("Thiếu category.");
        }
        if (trimToNull(row.getCode()) == null) {
            errors.add("Thiếu code.");
        }
        if (trimToNull(row.getName()) == null) {
            errors.add("Thiếu name.");
        }

        if ("FAQ".equals(type)) {
            if (trimToNull(row.getAliasesRaw()) == null && trimToNull(row.getSymptomKeywordsRaw()) == null) {
                warnings.add("FAQ nên có ít nhất một từ khóa trong cột aliases hoặc symptoms_keywords.");
            }
            if (trimToNull(row.getAnswerHtml()) == null) {
                errors.add("FAQ bắt buộc có answer_html.");
            }
        }

        if ("DISEASE".equals(type)) {
            if (trimToNull(row.getSymptomKeywordsRaw()) == null) {
                errors.add("DISEASE bắt buộc có symptoms_keywords.");
            }
            if (trimToNull(row.getSignsSummary()) == null) {
                errors.add("DISEASE bắt buộc có signs_summary.");
            }
        }

        return AiKnowledgeImportPreviewRowResponse.builder()
                .rowNumber(row.getRowNumber())
                .type(type)
                .categoryName(row.getCategoryName())
                .code(trimToNull(row.getCode()))
                .name(trimToNull(row.getName()))
                .status(errors.isEmpty() ? AiKnowledgeStatus.IN_REVIEW.name() : AiKnowledgeStatus.DRAFT.name())
                .valid(errors.isEmpty())
                .warnings(warnings)
                .errors(errors)
                .aliasesRaw(trimToNull(row.getAliasesRaw()))
                .symptomKeywordsRaw(trimToNull(row.getSymptomKeywordsRaw()))
                .answerHtml(trimToNull(row.getAnswerHtml()))
                .signsSummary(trimToNull(row.getSignsSummary()))
                .causes(defaultList(row.getCauses()))
                .treatmentStages(toTreatmentStageResponses(row.getTreatmentStages()))
                .matchThreshold(defaultDouble(row.getMatchThreshold(), "FAQ".equals(type) ? 0.35D : 0.4D))
                .confidenceThreshold(defaultDouble(row.getConfidenceThreshold(), 0.65D))
                .enabled(defaultBoolean(row.getEnabled(), true))
                .build();
    }

    private void upsertKeywordAnswerSet(AiKnowledgeImportPreviewRowRequest row, String mode) {
        String code = row.getCode().trim().toUpperCase(Locale.ROOT);
        Optional<AiKeywordAnswerSet> existing = keywordAnswerSetRepository.findByCode(code);
        if (existing.isPresent() && "UPSERT_NEW".equals(mode)) {
            return;
        }

        AiKeywordAnswerSet entity = existing.orElseGet(() -> AiKeywordAnswerSet.builder().build());
        entity.setCode(code);
        entity.setName(row.getName().trim());
        entity.setCategory(resolveOrCreateCategoryByName(row.getCategoryName()));
        entity.setKeywordsRaw(buildImportKeywords(row));
        entity.setAnswerHtml(row.getAnswerHtml().trim());
        entity.setEnabled(defaultBoolean(row.getEnabled(), true));
        entity.setMatchThreshold(clampThreshold(row.getMatchThreshold(), 0.35D));
        entity.setPriority(entity.getPriority() == null ? 0 : entity.getPriority());
        entity.setCanonical(defaultBoolean(entity.getCanonical(), false));
        entity.setStatus(AiKnowledgeStatus.IN_REVIEW);
        keywordAnswerSetRepository.save(entity);
    }

    private void upsertDiseaseKnowledge(AiKnowledgeImportPreviewRowRequest row, String mode) {
        String code = row.getCode().trim().toUpperCase(Locale.ROOT);
        Optional<AiDiseaseKnowledge> existing = diseaseKnowledgeRepository.findByCode(code);
        if (existing.isPresent() && "UPSERT_NEW".equals(mode)) {
            return;
        }

        AiDiseaseKnowledge entity = existing.orElseGet(() -> AiDiseaseKnowledge.builder().build());
        entity.setCode(code);
        entity.setNameVi(row.getName().trim());
        entity.setCategory(resolveOrCreateCategoryByName(row.getCategoryName()));
        entity.setAliasesRaw(trimToNull(row.getAliasesRaw()));
        entity.setSymptomKeywordsRaw(row.getSymptomKeywordsRaw().trim());
        entity.setSignsSummary(row.getSignsSummary().trim());
        entity.setCausesJson(writeJson(defaultList(row.getCauses())));
        entity.setTreatmentStagesJson(writeJson(toKnowledgeStages(defaultList(row.getTreatmentStages()).stream()
                .map(stage -> AiKnowledgeTreatmentStageRequest.builder()
                        .stageTitle(stage.getStageTitle())
                        .instructions(stage.getInstructions())
                        .productIds(stage.getProductIds())
                        .build())
                .toList())));
        entity.setConfidenceThreshold(clampThreshold(row.getConfidenceThreshold(), 0.65D));
        entity.setMatchThreshold(clampThreshold(row.getMatchThreshold(), 0.4D));
        entity.setEnabled(defaultBoolean(row.getEnabled(), true));
        entity.setPriority(entity.getPriority() == null ? 0 : entity.getPriority());
        entity.setCanonical(defaultBoolean(entity.getCanonical(), false));
        entity.setStatus(AiKnowledgeStatus.IN_REVIEW);
        diseaseKnowledgeRepository.save(entity);
    }

    private String buildImportKeywords(AiKnowledgeImportPreviewRowRequest row) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (trimToNull(row.getAliasesRaw()) != null) {
            keywords.addAll(Arrays.stream(row.getAliasesRaw().split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList());
        }
        if (trimToNull(row.getSymptomKeywordsRaw()) != null) {
            keywords.addAll(Arrays.stream(row.getSymptomKeywordsRaw().split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList());
        }
        if (keywords.isEmpty()) {
            keywords.add(row.getName().trim());
        }
        return String.join(", ", keywords);
    }

    private MatchOutcome resolveBestMatch(
            String normalizedMessage,
            String diagnosisDiseaseCode,
            String diagnosisDiseaseName,
            ApprovedKnowledgeSnapshot snapshot) {
        if (trimToNull(diagnosisDiseaseCode) != null) {
            Optional<PreparedDisease> directDisease = findDiseaseByCodeFromSnapshot(diagnosisDiseaseCode, snapshot);
            if (directDisease.isPresent()) {
                PreparedDisease disease = directDisease.get();
                return MatchOutcome.builder()
                        .matched(true)
                        .matchType(AiKnowledgeMatchType.DISEASE_KNOWLEDGE)
                        .knowledgeCode(disease.entity().getCode())
                        .score(1D)
                        .answerHtml(buildDiseaseAnswerHtml(disease))
                        .build();
            }
        }

        if (trimToNull(diagnosisDiseaseName) != null) {
            MatchScore namedDiseaseScore = matchDiseaseFromText(AiKnowledgeTextUtils.normalize(diagnosisDiseaseName), snapshot.diseaseEntries);
            if (namedDiseaseScore.score() >= defaultDouble(namedDiseaseScore.disease().entity().getMatchThreshold(), 0.4D)) {
                return MatchOutcome.builder()
                        .matched(true)
                        .matchType(AiKnowledgeMatchType.DISEASE_KNOWLEDGE)
                        .knowledgeCode(namedDiseaseScore.disease().entity().getCode())
                        .score(namedDiseaseScore.score())
                        .answerHtml(buildDiseaseAnswerHtml(namedDiseaseScore.disease()))
                        .build();
            }
        }

        MatchScore diseaseMatch = matchDiseaseFromText(normalizedMessage, snapshot.diseaseEntries);
        MatchScore keywordMatch = matchKeywordSetFromText(normalizedMessage, snapshot.keywordEntries);

        boolean diseaseWins = diseaseMatch.score() > 0D
                && diseaseMatch.disease() != null
                && diseaseMatch.score() >= defaultDouble(diseaseMatch.disease().entity().getMatchThreshold(), 0.4D)
                && diseaseMatch.score() + 0.05D >= keywordMatch.score();
        if (diseaseWins) {
            return MatchOutcome.builder()
                    .matched(true)
                    .matchType(AiKnowledgeMatchType.DISEASE_KNOWLEDGE)
                    .knowledgeCode(diseaseMatch.disease().entity().getCode())
                    .score(diseaseMatch.score())
                    .answerHtml(buildDiseaseAnswerHtml(diseaseMatch.disease()))
                    .build();
        }

        boolean keywordWins = keywordMatch.score() > 0D
                && keywordMatch.keyword() != null
                && keywordMatch.score() >= defaultDouble(keywordMatch.keyword().entity().getMatchThreshold(), 0.35D);
        if (keywordWins) {
            return MatchOutcome.builder()
                    .matched(true)
                    .matchType(AiKnowledgeMatchType.KEYWORD_SET)
                    .knowledgeCode(keywordMatch.keyword().entity().getCode())
                    .score(keywordMatch.score())
                    .answerHtml(keywordMatch.keyword().entity().getAnswerHtml())
                    .build();
        }

        return MatchOutcome.builder().matched(false).build();
    }

    private MatchScore matchDiseaseFromText(String normalizedMessage, List<PreparedDisease> diseases) {
        MatchScore best = MatchScore.none();
        Set<String> messageTokens = new LinkedHashSet<>(AiKnowledgeTextUtils.tokenize(normalizedMessage));

        for (PreparedDisease disease : diseases) {
            double score = scoreAgainstKeywords(normalizedMessage, messageTokens, disease.keywords());
            score += disease.entity().getCanonical() != null && disease.entity().getCanonical() ? 0.03D : 0D;
            score += Math.min(defaultInt(disease.entity().getPriority(), 0), 10) * 0.002D;

            if (score > best.score()) {
                best = MatchScore.disease(score, disease);
            }
        }
        return best;
    }

    private MatchScore matchKeywordSetFromText(String normalizedMessage, List<PreparedKeywordSet> keywordSets) {
        MatchScore best = MatchScore.none();
        Set<String> messageTokens = new LinkedHashSet<>(AiKnowledgeTextUtils.tokenize(normalizedMessage));

        for (PreparedKeywordSet keywordSet : keywordSets) {
            double score = scoreAgainstKeywords(normalizedMessage, messageTokens, keywordSet.keywords());
            score += keywordSet.entity().getCanonical() != null && keywordSet.entity().getCanonical() ? 0.02D : 0D;
            score += Math.min(defaultInt(keywordSet.entity().getPriority(), 0), 10) * 0.002D;

            if (score > best.score()) {
                best = MatchScore.keyword(score, keywordSet);
            }
        }
        return best;
    }

    private double scoreAgainstKeywords(String normalizedMessage, Set<String> messageTokens, Set<String> keywords) {
        double bestScore = 0D;
        for (String keyword : keywords) {
            if (keyword.isBlank()) {
                continue;
            }

            if (normalizedMessage.equals(keyword)) {
                return 1D;
            }

            if (normalizedMessage.contains(keyword)) {
                bestScore = Math.max(bestScore, keyword.contains(" ") ? 0.92D : 0.82D);
            }

            Set<String> keywordTokens = new LinkedHashSet<>(AiKnowledgeTextUtils.tokenize(keyword));
            if (keywordTokens.isEmpty()) {
                continue;
            }

            long overlap = keywordTokens.stream().filter(messageTokens::contains).count();
            if (overlap > 0) {
                double overlapRatio = (double) overlap / keywordTokens.size();
                bestScore = Math.max(bestScore, overlapRatio * 0.75D);
            }
        }
        return bestScore;
    }

    private PreparedDisease findDiseaseByPrediction(AiPredictionItem prediction, ApprovedKnowledgeSnapshot snapshot) {
        if (prediction == null) {
            return null;
        }

        if (trimToNull(prediction.getDiseaseCode()) != null) {
            Optional<PreparedDisease> direct = findDiseaseByCodeFromSnapshot(prediction.getDiseaseCode(), snapshot);
            if (direct.isPresent()) {
                return direct.get();
            }
        }

        List<String> nameCandidates = List.of(
                trimToNull(prediction.getVietnameseName()),
                trimToNull(prediction.getEnglishName()))
                .stream()
                .filter(Objects::nonNull)
                .toList();

        for (String candidate : nameCandidates) {
            String normalizedCandidate = AiKnowledgeTextUtils.normalize(candidate);
            for (PreparedDisease disease : snapshot.diseaseEntries) {
                if (disease.keywords().contains(normalizedCandidate)) {
                    return disease;
                }
            }
        }

        return null;
    }

    private Optional<PreparedDisease> findDiseaseByCodeFromSnapshot(String diseaseCode, ApprovedKnowledgeSnapshot snapshot) {
        String normalized = trimToNull(diseaseCode);
        if (normalized == null) {
            return Optional.empty();
        }
        return snapshot.diseaseEntries.stream()
                .filter(entry -> normalized.equalsIgnoreCase(entry.entity().getCode()))
                .findFirst();
    }

    private AiDoctorDiagnosisResponse buildDiagnosisResponse(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl,
            PreparedDisease disease,
            String userSymptoms,
            String status) {
        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId("diag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .status(status)
                .imageUrl(diagnosisImageUrl)
                .disease(DiseaseResponse.builder()
                        .code(disease.entity().getCode())
                        .nameVi(disease.entity().getNameVi())
                        .nameEn(disease.entity().getNameEn())
                        .confidencePercent(finalPrediction.getConfidencePercent())
                        .build())
                .topPredictions(toTopPredictions(predictResponse))
                .causes(defaultList(readJsonList(disease.entity().getCausesJson(), new TypeReference<List<String>>() {
                })))
                .signsSummary(disease.entity().getSignsSummary())
                .treatmentStages(toTreatmentStageResponses(disease.entity().getTreatmentStagesJson()))
                .build();
    }

    private AiDoctorDiagnosisResponse buildLowConfidenceDiagnosisResponse(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl) {
        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId("diag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .status("DISEASE")
                .imageUrl(diagnosisImageUrl)
                .disease(DiseaseResponse.builder()
                        .code(finalPrediction.getDiseaseCode())
                        .nameVi(finalPrediction.getVietnameseName())
                        .nameEn(finalPrediction.getEnglishName())
                        .confidencePercent(finalPrediction.getConfidencePercent())
                        .build())
                .topPredictions(toTopPredictions(predictResponse))
                .signsSummary("Tôi cần thêm dấu hiệu từ người nuôi để kết luận an toàn hơn. "
                        + "Vui lòng mô tả rõ các biểu hiện như giảm ăn, đường ruột, màu gan tụy hoặc tình trạng bơi lờ đờ.")
                .treatmentStages(Collections.emptyList())
                .needsClarification(true)
                .build();
    }

    private List<TopPredictionResponse> toTopPredictions(AiPredictResponse predictResponse) {
        if (predictResponse == null || predictResponse.getTopPredictions() == null) {
            return Collections.emptyList();
        }
        return predictResponse.getTopPredictions().stream()
                .map(prediction -> TopPredictionResponse.builder()
                        .diseaseCode(prediction.getDiseaseCode())
                        .nameVi(prediction.getVietnameseName())
                        .nameEn(prediction.getEnglishName())
                        .confidencePercent(prediction.getConfidencePercent())
                        .build())
                .toList();
    }

    private void persistChatLog(
            Long userId,
            String sessionId,
            String sourceChannel,
            String questionText,
            String answerText,
            boolean matched,
            AiKnowledgeMatchType matchedType,
            String matchedKnowledgeCode,
            Double matchScore) {
        chatLogRepository.save(AiKnowledgeChatLog.builder()
                .userId(userId)
                .sessionId(sessionId)
                .sourceChannel(sourceChannel)
                .questionText(questionText)
                .answerText(answerText)
                .matched(matched)
                .matchedType(matchedType)
                .matchedKnowledgeCode(matchedKnowledgeCode)
                .matchScore(matchScore)
                .build());
    }

    @Transactional
    public AiKnowledgeReviewCase createReviewCase(
            Long userId,
            String sessionId,
            String sourceChannel,
            String questionText,
            String userSymptoms,
            String imageUrl,
            String aiSuggestedDiseaseCode,
            String matchedKnowledgeCode,
            Double matchScore,
            AiReviewCaseReason reason) {
        return reviewCaseRepository.save(AiKnowledgeReviewCase.builder()
                .userId(userId)
                .sessionId(sessionId)
                .sourceChannel(sourceChannel)
                .questionText(trimToNull(questionText))
                .userSymptoms(trimToNull(userSymptoms))
                .imageUrl(trimToNull(imageUrl))
                .aiSuggestedDiseaseCode(trimToNull(aiSuggestedDiseaseCode))
                .matchedKnowledgeCode(trimToNull(matchedKnowledgeCode))
                .matchScore(matchScore)
                .reason(reason)
                .status(AiReviewCaseStatus.NEW)
                .build());
    }

    private AiChatResponse buildChatResponse(String replyHtml, String sessionId, List<String> suggestedActions) {
        AiChatResponse response = new AiChatResponse();
        response.setSuccess(true);
        response.setConversationId(sessionId);
        response.setReply(replyHtml);
        response.setSuggestedActions(suggestedActions);
        return response;
    }

    private List<String> getSuggestedActionLabels() {
        return getChatPrompts().stream()
                .limit(4)
                .map(AiDoctorChatPromptResponse::getQuestion)
                .toList();
    }

    private String buildDiseaseAnswerHtml(PreparedDisease disease) {
        List<String> causes = defaultList(readJsonList(disease.entity().getCausesJson(), new TypeReference<List<String>>() {
        }));
        List<TreatmentStageResponse> treatmentStages = toTreatmentStageResponses(disease.entity().getTreatmentStagesJson());
        StringBuilder builder = new StringBuilder();
        builder.append("<div><strong>")
                .append(escapeHtml(disease.entity().getNameVi()))
                .append("</strong>");

        if (trimToNull(disease.entity().getNameEn()) != null) {
            builder.append(" <em>(").append(escapeHtml(disease.entity().getNameEn())).append(")</em>");
        }
        builder.append("</div>");

        if (trimToNull(disease.entity().getSignsSummary()) != null) {
            builder.append("<p>").append(escapeHtml(disease.entity().getSignsSummary())).append("</p>");
        }

        if (!causes.isEmpty()) {
            builder.append("<p><strong>Nguyên nhân thường gặp:</strong></p><ul>");
            for (String cause : causes) {
                builder.append("<li>").append(escapeHtml(cause)).append("</li>");
            }
            builder.append("</ul>");
        }

        if (!treatmentStages.isEmpty()) {
            builder.append("<p><strong>Phác đồ đã duyệt:</strong></p><ol>");
            for (TreatmentStageResponse stage : treatmentStages) {
                builder.append("<li><strong>")
                        .append(escapeHtml(stage.getStageTitle()))
                        .append("</strong>");
                if (!defaultList(stage.getInstructions()).isEmpty()) {
                    builder.append("<ul>");
                    for (String instruction : stage.getInstructions()) {
                        builder.append("<li>").append(escapeHtml(instruction)).append("</li>");
                    }
                    builder.append("</ul>");
                }
                List<String> productLabels = new ArrayList<>();
                defaultList(stage.getProducts()).forEach(product -> productLabels.add(product.getName()));
                productLabels.addAll(defaultList(stage.getExtraProductNames()));
                if (!productLabels.isEmpty()) {
                    builder.append("<div>Sản phẩm: ")
                            .append(productLabels.stream()
                                    .map(this::escapeHtml)
                                    .collect(Collectors.joining(", ")))
                            .append("</div>");
                }
                builder.append("</li>");
            }
            builder.append("</ol>");
        }

        return builder.toString();
    }

    private ApprovedKnowledgeSnapshot getApprovedSnapshot() {
        ApprovedKnowledgeSnapshot current = approvedSnapshotRef.get();
        long now = System.currentTimeMillis();
        if (current != null && now - approvedSnapshotLoadedAt <= SNAPSHOT_TTL_MS) {
            return current;
        }

        synchronized (approvedSnapshotRef) {
            current = approvedSnapshotRef.get();
            if (current != null && now - approvedSnapshotLoadedAt <= SNAPSHOT_TTL_MS) {
                return current;
            }

            List<PreparedKeywordSet> keywordEntries = keywordAnswerSetRepository
                    .findAllByStatusAndEnabledTrueOrderByPriorityDescNameAsc(AiKnowledgeStatus.APPROVED)
                    .stream()
                    .map(entity -> new PreparedKeywordSet(
                            entity,
                            entity.getCategory(),
                            prepareKeywords(entity.getName(), entity.getKeywordsRaw(), null)))
                    .toList();

            List<PreparedDisease> diseaseEntries = diseaseKnowledgeRepository
                    .findAllByStatusAndEnabledTrueOrderByPriorityDescNameViAsc(AiKnowledgeStatus.APPROVED)
                    .stream()
                    .map(entity -> new PreparedDisease(
                            entity,
                            entity.getCategory(),
                            prepareKeywords(entity.getNameVi(), entity.getAliasesRaw(), entity.getSymptomKeywordsRaw())))
                    .toList();

            current = new ApprovedKnowledgeSnapshot(keywordEntries, diseaseEntries);
            approvedSnapshotRef.set(current);
            approvedSnapshotLoadedAt = now;
            return current;
        }
    }

    private void evictApprovedSnapshot() {
        approvedSnapshotRef.set(null);
        approvedSnapshotLoadedAt = 0L;
    }

    private Set<String> prepareKeywords(String name, String aliasesRaw, String symptomKeywordsRaw) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (trimToNull(name) != null) {
            keywords.add(AiKnowledgeTextUtils.normalize(name));
        }
        keywords.addAll(AiKnowledgeTextUtils.keywordSet(aliasesRaw));
        keywords.addAll(AiKnowledgeTextUtils.keywordSet(symptomKeywordsRaw));
        return keywords.stream().filter(part -> !part.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AiKnowledgeCategoryResponse toCategoryResponse(AiKnowledgeCategory category) {
        if (category == null) {
            return null;
        }
        return AiKnowledgeCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .enabled(category.getEnabled())
                .sortOrder(category.getSortOrder())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private AiKeywordAnswerSetResponse toKeywordAnswerSetResponse(AiKeywordAnswerSet entity) {
        return AiKeywordAnswerSetResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .category(toCategoryResponse(entity.getCategory()))
                .keywordsRaw(entity.getKeywordsRaw())
                .answerHtml(entity.getAnswerHtml())
                .enabled(entity.getEnabled())
                .matchThreshold(entity.getMatchThreshold())
                .priority(entity.getPriority())
                .canonical(entity.getCanonical())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AiDiseaseKnowledgeResponse toDiseaseKnowledgeResponse(AiDiseaseKnowledge entity) {
        return AiDiseaseKnowledgeResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .nameVi(entity.getNameVi())
                .nameEn(entity.getNameEn())
                .category(toCategoryResponse(entity.getCategory()))
                .aliasesRaw(entity.getAliasesRaw())
                .symptomKeywordsRaw(entity.getSymptomKeywordsRaw())
                .signsSummary(entity.getSignsSummary())
                .causes(defaultList(readJsonList(entity.getCausesJson(), new TypeReference<List<String>>() {
                })))
                .treatmentStages(toKnowledgeTreatmentStageResponses(entity.getTreatmentStagesJson()))
                .confidenceThreshold(entity.getConfidenceThreshold())
                .matchThreshold(entity.getMatchThreshold())
                .enabled(entity.getEnabled())
                .priority(entity.getPriority())
                .canonical(entity.getCanonical())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AiKnowledgeReviewCaseResponse toReviewCaseResponse(AiKnowledgeReviewCase entity) {
        return AiKnowledgeReviewCaseResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .sessionId(entity.getSessionId())
                .sourceChannel(entity.getSourceChannel())
                .questionText(entity.getQuestionText())
                .userSymptoms(entity.getUserSymptoms())
                .imageUrl(entity.getImageUrl())
                .aiSuggestedDiseaseCode(entity.getAiSuggestedDiseaseCode())
                .matchedKnowledgeCode(entity.getMatchedKnowledgeCode())
                .matchScore(entity.getMatchScore())
                .reason(entity.getReason())
                .status(entity.getStatus())
                .resolutionNotes(entity.getResolutionNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AiKnowledgeChatConfigResponse toChatConfigResponse(AiKnowledgeChatConfig config) {
        return AiKnowledgeChatConfigResponse.builder()
                .id(config.getId())
                .greetingMessage(config.getGreetingMessage())
                .fallbackMessage(config.getFallbackMessage())
                .build();
    }

    private List<TreatmentStageResponse> toTreatmentStageResponses(String treatmentStagesJson) {
        return defaultList(readJsonList(treatmentStagesJson, new TypeReference<List<KnowledgeStage>>() {
        })).stream()
                .map(stage -> TreatmentStageResponse.builder()
                        .stageTitle(stage.getStageTitle())
                        .instructions(defaultList(stage.getInstructions()))
                        .products(resolveSuggestedProducts(stage.getProductIds()))
                        .extraProductNames(defaultList(stage.getExtraProductNames()))
                        .build())
                .toList();
    }

    private List<AiKnowledgeTreatmentStageResponse> toKnowledgeTreatmentStageResponses(String treatmentStagesJson) {
        return defaultList(readJsonList(treatmentStagesJson, new TypeReference<List<KnowledgeStage>>() {
        })).stream()
                .map(stage -> AiKnowledgeTreatmentStageResponse.builder()
                        .stageTitle(stage.getStageTitle())
                        .instructions(defaultList(stage.getInstructions()))
                        .productIds(defaultList(stage.getProductIds()))
                        .products(resolveSuggestedProducts(stage.getProductIds()))
                        .extraProductNames(defaultList(stage.getExtraProductNames()))
                        .build())
                .toList();
    }

    private List<AiKnowledgeTreatmentStageResponse> toTreatmentStageResponses(List<AiKnowledgeTreatmentStageRequest> stages) {
        return defaultList(stages).stream()
                .map(stage -> AiKnowledgeTreatmentStageResponse.builder()
                        .stageTitle(stage.getStageTitle())
                        .instructions(defaultList(stage.getInstructions()))
                        .productIds(defaultList(stage.getProductIds()))
                        .products(resolveSuggestedProducts(stage.getProductIds()))
                        .extraProductNames(defaultList(stage.getExtraProductNames()))
                        .build())
                .toList();
    }

    private List<KnowledgeStage> toKnowledgeStages(List<AiKnowledgeTreatmentStageRequest> stages) {
        return defaultList(stages).stream()
                .map(stage -> KnowledgeStage.builder()
                        .stageTitle(trimToNull(stage.getStageTitle()))
                        .instructions(defaultList(stage.getInstructions()))
                        .productIds(defaultList(stage.getProductIds()))
                        .extraProductNames(defaultList(stage.getExtraProductNames()).stream()
                                .map(this::trimToNull)
                                .filter(Objects::nonNull)
                                .toList())
                        .build())
                .toList();
    }

    private List<SuggestedProductResponse> resolveSuggestedProducts(List<Long> productIds) {
        List<Long> ids = defaultList(productIds).stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Product> productMap = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        Map<Long, Long> priceMap = productSuggestionService.getPriceMap(new ArrayList<>(productMap.values()));
        return ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(product -> productSuggestionService.mapRelatedProductIdsToSuggestedProducts(
                        List.of(product.getId()),
                        productMap,
                        priceMap))
                .flatMap(List::stream)
                .toList();
    }

    private AiKnowledgeChatConfig ensureChatConfig() {
        return chatConfigRepository.findById(1L)
                .orElseGet(() -> chatConfigRepository.save(AiKnowledgeChatConfig.builder()
                        .id(1L)
                        .greetingMessage(DEFAULT_GREETING)
                        .fallbackMessage(DEFAULT_FALLBACK)
                        .build()));
    }

    private String normalizeImportMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "OVERWRITE";
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "UPSERT_NEW", "OVERWRITE" -> normalized;
            default -> "OVERWRITE";
        };
    }

    private String normalizeImportType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FAQ", "DISEASE" -> normalized;
            default -> null;
        };
    }

    private List<AiKnowledgeTreatmentStageRequest> parseStages(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }

        List<AiKnowledgeTreatmentStageRequest> stages = new ArrayList<>();
        String[] stageParts = raw.split("\\|\\|");
        for (String stagePart : stageParts) {
            String trimmedStage = stagePart.trim();
            if (trimmedStage.isBlank()) {
                continue;
            }

            String[] pieces = trimmedStage.split("::");
            String title = pieces.length > 0 ? pieces[0].trim() : null;
            List<String> instructions = pieces.length > 1 ? splitPipeValues(pieces[1]) : Collections.emptyList();
            List<Long> productIds = pieces.length > 2
                    ? Arrays.stream(pieces[2].split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .map(this::parseOptionalLong)
                    .filter(Objects::nonNull)
                    .toList()
                    : Collections.emptyList();

            stages.add(AiKnowledgeTreatmentStageRequest.builder()
                    .stageTitle(title)
                    .instructions(instructions)
                    .productIds(productIds)
                    .build());
        }
        return stages;
    }

    private boolean isBlankRow(Row row) {
        for (int index = 0; index < 13; index++) {
            if (trimToNull(getCellString(row, index)) != null) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(Row row, int index) {
        if (row.getCell(index) == null) {
            return null;
        }
        row.getCell(index).setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
        return trimToNull(row.getCell(index).getStringCellValue());
    }

    private List<String> splitPipeValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private Double parseOptionalDouble(String raw) {
        try {
            return trimToNull(raw) == null ? null : Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseOptionalLong(String raw) {
        try {
            return trimToNull(raw) == null ? null : Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean parseOptionalBoolean(String raw) {
        if (trimToNull(raw) == null) {
            return null;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()) || "yes".equalsIgnoreCase(raw.trim());
    }

    private <T> T readJsonList(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception exception) {
            log.warn("Không đọc được JSON tri thức AI: {}", exception.getMessage());
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new RuntimeException("Không thể lưu JSON tri thức AI", exception);
        }
    }

    private String requiredTrim(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean defaultBoolean(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private int defaultInt(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private double defaultDouble(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private Double clampThreshold(Double value, double fallback) {
        double threshold = value != null ? value : fallback;
        if (threshold < 0D) {
            threshold = 0D;
        }
        if (threshold > 1D) {
            threshold = 1D;
        }
        return threshold;
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private String resolveCategoryLabel(AiKnowledgeCategory category) {
        return category != null && trimToNull(category.getName()) != null ? category.getName() : "Tri thức chung";
    }

    private String firstKeywordLabel(Set<String> keywords, String fallback) {
        return keywords.stream().findFirst().orElse(fallback);
    }

    private NotFoundException notFound(String message) {
        NotFoundException exception = new NotFoundException();
        exception.setMessage(message);
        return exception;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record ApprovedKnowledgeSnapshot(
            List<PreparedKeywordSet> keywordEntries,
            List<PreparedDisease> diseaseEntries) {
    }

    private record PreparedKeywordSet(
            AiKeywordAnswerSet entity,
            AiKnowledgeCategory category,
            Set<String> keywords) {
    }

    private record PreparedDisease(
            AiDiseaseKnowledge entity,
            AiKnowledgeCategory category,
            Set<String> keywords) {
    }

    @Builder
    private record MatchOutcome(
            boolean matched,
            AiKnowledgeMatchType matchType,
            String knowledgeCode,
            Double score,
            String answerHtml) {
    }

    private record MatchScore(
            double score,
            PreparedKeywordSet keyword,
            PreparedDisease disease) {
        static MatchScore none() {
            return new MatchScore(0D, null, null);
        }

        static MatchScore keyword(double score, PreparedKeywordSet keyword) {
            return new MatchScore(score, keyword, null);
        }

        static MatchScore disease(double score, PreparedDisease disease) {
            return new MatchScore(score, null, disease);
        }
    }

    @Builder
    private static class KnowledgeStage {
        private String stageTitle;
        private List<String> instructions;
        private List<Long> productIds;
        private List<String> extraProductNames;

        public String getStageTitle() {
            return stageTitle;
        }

        public List<String> getInstructions() {
            return instructions;
        }

        public List<Long> getProductIds() {
            return productIds;
        }

        public List<String> getExtraProductNames() {
            return extraProductNames;
        }
    }
}
