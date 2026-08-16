package com.zone.agri.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.ai.AiChatResponse;
import com.zone.agri.dto.ai.AiClarifyLlmResult;
import com.zone.agri.dto.ai.AiClarifyTurn;
import com.zone.agri.dto.ai.AiPredictResponse;
import com.zone.agri.dto.ai.AiPredictionItem;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.dto.response.ai.DiseaseResponse;
import com.zone.agri.dto.response.ai.SuggestedProductResponse;
import com.zone.agri.dto.response.ai.TopPredictionResponse;
import com.zone.agri.dto.response.ai.TreatmentStageResponse;
import com.zone.agri.dto.response.ai.TreatmentStageSelectionResponse;
import com.zone.agri.dto.request.ai.AiDiseaseKnowledgeRequest;
import com.zone.agri.dto.request.ai.AiDoctorChatRequest;
import com.zone.agri.dto.request.ai.AiKeywordAnswerSetRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeCategoryRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeChatConfigRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeImportApplyRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeImportPreviewRowRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeTreatmentSubStageRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeTreatmentStageRequest;
import com.zone.agri.dto.request.ai.AiReviewCaseUpdateRequest;
import com.zone.agri.dto.response.ai.AiClarifyCandidateSummary;
import com.zone.agri.dto.response.ai.AiDiseaseKnowledgeResponse;
import com.zone.agri.dto.response.ai.AiDoctorChatPromptResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeCategoryResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeChatConfigResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeImportPreviewResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeImportPreviewRowResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeReviewCaseResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeTreatmentSubStageResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeTreatmentStageResponse;
import com.zone.agri.dto.response.ai.AiKeywordAnswerSetResponse;
import com.zone.agri.entity.AiChatClarifySession;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiKeywordAnswerSet;
import com.zone.agri.entity.AiKnowledgeCategory;
import com.zone.agri.entity.AiKnowledgeChatConfig;
import com.zone.agri.entity.AiKnowledgeChatLog;
import com.zone.agri.entity.AiKnowledgeReviewCase;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.enums.AiClarifySessionStatus;
import com.zone.agri.entity.enums.AiKnowledgeMatchType;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.entity.enums.AiReviewCaseReason;
import com.zone.agri.entity.enums.AiReviewCaseStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.AiChatClarifySessionRepository;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiKeywordAnswerSetRepository;
import com.zone.agri.repository.AiKnowledgeCategoryRepository;
import com.zone.agri.repository.AiKnowledgeChatConfigRepository;
import com.zone.agri.repository.AiKnowledgeChatLogRepository;
import com.zone.agri.repository.AiKnowledgeReviewCaseRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.service.NotificationService;
import com.zone.agri.service.aidoctor.AiDoctorProductSuggestionService;
import com.zone.agri.utils.AiTextFormatUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String CHAT_CLARIFY_ESCALATION_MESSAGE =
            "Dấu hiệu bạn mô tả chưa đủ để tôi kết luận chắc chắn. "
                    + "Vui lòng mô tả rõ hơn hoặc liên hệ kỹ sư nông nghiệp để được hỗ trợ.";
    // Bệnh có điểm khớp thấp hơn ngưỡng riêng nhưng vẫn đạt tối thiểu 60% ngưỡng đó được coi là
    // "gần đạt" — đủ để chủ động hỏi thêm các dấu hiệu phân biệt còn thiếu (qua Gemini) thay vì
    // rơi thẳng vào fallback cứng như trước.
    private static final double NEAR_MISS_THRESHOLD_RATIO = 0.6D;
    private static final int CHAT_CLARIFY_CANDIDATE_LIMIT = 4;
    private static final double RELATED_DISEASE_MIN_SCORE = 0.25D;

    private final ObjectMapper objectMapper;
    private final AiKnowledgeCategoryRepository categoryRepository;
    private final AiKeywordAnswerSetRepository keywordAnswerSetRepository;
    private final AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;
    private final AiKnowledgeReviewCaseRepository reviewCaseRepository;
    private final AiKnowledgeChatConfigRepository chatConfigRepository;
    private final AiKnowledgeChatLogRepository chatLogRepository;
    private final AiChatClarifySessionRepository chatClarifySessionRepository;
    private final ProductRepository productRepository;
    private final AiDoctorProductSuggestionService productSuggestionService;
    private final GeminiClarifyClient geminiClarifyClient;
    private final NotificationService notificationService;

    @Value("${ai.doctor.clarify.max-turns:8}")
    private int maxClarifyTurns;

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
        entity.setReviewNote(null);
        AiDiseaseKnowledge saved = diseaseKnowledgeRepository.save(entity);
        syncKeywordAnswerSetFromDisease(saved);
        evictApprovedSnapshot();
        return toDiseaseKnowledgeResponse(saved);
    }

    @Transactional
    public AiDiseaseKnowledgeResponse rejectDiseaseKnowledge(Long id, String reason) {
        AiDiseaseKnowledge entity = diseaseKnowledgeRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy tri thức bệnh với ID: " + id));
        entity.setStatus(AiKnowledgeStatus.DRAFT);
        entity.setReviewNote(trimToNull(reason));
        AiDiseaseKnowledge saved = diseaseKnowledgeRepository.save(entity);
        syncKeywordAnswerSetFromDisease(saved);
        evictApprovedSnapshot();
        return toDiseaseKnowledgeResponse(saved);
    }

    /**
     * Chi doi co enabled — KHONG tai dung applyDiseaseKnowledge()/PUT full-record vi ham do luon
     * ha APPROVED ve IN_REVIEW moi lan luu, se khien 1 lan an/hien don gian bi bat duyet lai.
     */
    @Transactional
    public AiDiseaseKnowledgeResponse setDiseaseKnowledgeVisibility(Long id, boolean enabled) {
        AiDiseaseKnowledge entity = diseaseKnowledgeRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy tri thức bệnh với ID: " + id));
        entity.setEnabled(enabled);
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
        entity.setAnswerHtml(buildRequestImageAnswerHtml(prepared));
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
        config.setFallbackContactName(trimToNull(request.getFallbackContactName()));
        config.setFallbackContactPhone(trimToNull(request.getFallbackContactPhone()));
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
            Sheet sheet = resolveImportSheet(workbook);
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
            CellStyle titleStyle = createTemplateTitleStyle(workbook);
            CellStyle sectionStyle = createTemplateSectionStyle(workbook);
            CellStyle guideKeyStyle = createTemplateGuideKeyStyle(workbook);
            CellStyle guideValueStyle = createTemplateGuideValueStyle(workbook);
            CellStyle headerStyle = createTemplateHeaderStyle(workbook);
            CellStyle requiredHeaderStyle = createTemplateRequiredHeaderStyle(workbook);
            CellStyle bodyStyle = createTemplateBodyStyle(workbook);
            CellStyle numberStyle = createTemplateNumberStyle(workbook);
            CellStyle exampleStyle = createTemplateExampleStyle(workbook);

            Sheet guideSheet = workbook.createSheet("README_HUONG_DAN");
            buildTemplateGuideSheet(guideSheet, titleStyle, sectionStyle, guideKeyStyle, guideValueStyle);

            Sheet sheet = workbook.createSheet("knowledge");
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

            List<String> requiredHeaders = List.of("type", "category", "code", "name");
            Row header = sheet.createRow(0);
            header.setHeightInPoints(30);
            for (int index = 0; index < headers.size(); index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(headers.get(index));
                boolean required = requiredHeaders.contains(headers.get(index))
                        || "answer_html".equals(headers.get(index))
                        || "symptoms_keywords".equals(headers.get(index))
                        || "signs_summary".equals(headers.get(index));
                cell.setCellStyle(required ? requiredHeaderStyle : headerStyle);
            }

            int rowIndex = 1;
            rowIndex = addTemplateRow(sheet, rowIndex, exampleStyle, numberStyle, List.of(
                    "FAQ",
                    "Tư vấn chung",
                    "FAQ_WHITE_SPOT",
                    "Dấu hiệu nhận biết bệnh đốm trắng",
                    "bệnh đốm trắng, đốm trắng, white spot, WSSV",
                    "",
                    "<p><strong>Dấu hiệu thường gặp:</strong> tôm xuất hiện đốm trắng trên vỏ, bơi lờ đờ, giảm ăn, có thể chết nhanh khi bệnh nặng.</p><p><strong>Lưu ý:</strong> chỉ nhìn bằng mắt chưa đủ kết luận 100%; nên gửi mẫu PCR khi nghi WSSV.</p>",
                    "",
                    "",
                    "",
                    0.4D,
                    "",
                    "true"));

            rowIndex = addTemplateRow(sheet, rowIndex, bodyStyle, numberStyle, List.of(
                    "DISEASE",
                    "Bệnh virus",
                    "WSSV",
                    "Bệnh đốm trắng",
                    "white spot syndrome virus, wssv, bệnh đốm trắng",
                    "đốm trắng trên vỏ, bơi lờ đờ, giảm ăn, tôm tấp mé, chết nhanh, vỏ mềm",
                    "",
                    "Tôm giảm ăn, bơi lờ đờ/tấp mé, xuất hiện đốm trắng trên vỏ hoặc giáp đầu ngực; có thể chết nhanh trong vài ngày.",
                    "môi trường biến động | mầm bệnh WSSV | con giống nhiễm virus | mật độ nuôi cao",
                    "Giai đoạn 1::Tạm ngưng/giảm thức ăn khi tôm giảm ăn | Tăng oxy, giữ DO ổn định | Hạn chế thay nước đột ngột:: || Giai đoạn 2::Vớt tôm chết, cô lập ao nghi bệnh | Kiểm tra pH, kiềm, độ mặn, nhiệt độ | Lấy mẫu PCR WSSV càng sớm càng tốt:: || Giai đoạn 3::Ổn định môi trường, sát khuẩn dụng cụ riêng | Không tự dùng kháng sinh vì đây là bệnh virus | Báo kỹ thuật nếu tôm chết tăng::",
                    0.45D,
                    0.7D,
                    "true"));

            rowIndex = addTemplateRow(sheet, rowIndex, bodyStyle, numberStyle, List.of(
                    "DISEASE",
                    "Bệnh vi khuẩn",
                    "AHPND_EMS",
                    "Hoại tử gan tụy cấp",
                    "AHPND, EMS, hoại tử gan tụy, gan tụy nhạt",
                    "tôm bỏ ăn, gan tụy nhạt màu, ruột rỗng, chết sớm, mềm vỏ, bơi yếu",
                    "",
                    "Tôm giảm ăn nhanh, ruột rỗng, gan tụy nhạt/teo, chết rải rác hoặc tăng nhanh trong giai đoạn đầu vụ.",
                    "Vibrio gây độc | đáy ao bẩn | thức ăn dư | stress môi trường | mật độ nuôi cao",
                    "Giai đoạn 1::Giảm 30-50% thức ăn, siphon đáy và vớt xác | Tăng oxy, kiểm tra NH3/NO2/pH/kiềm:: || Giai đoạn 2::Dùng vi sinh xử lý đáy/nước theo khuyến cáo nhãn | Bổ sung khoáng, vitamin và chất hỗ trợ gan tụy:: || Giai đoạn 3::Nếu chết tăng, gửi mẫu xét nghiệm Vibrio/AHPND | Không xả nước ao bệnh ra ngoài khi chưa xử lý::",
                    0.45D,
                    0.7D,
                    "true"));

            rowIndex = addTemplateRow(sheet, rowIndex, bodyStyle, numberStyle, List.of(
                    "DISEASE",
                    "Bệnh đường ruột",
                    "EHP_WHITE_FECES",
                    "EHP và phân trắng",
                    "EHP, phân trắng, white feces, tôm chậm lớn",
                    "phân trắng nổi mặt nước, ruột đứt khúc, tôm chậm lớn, mềm vỏ, ăn yếu",
                    "",
                    "Xuất hiện dây phân trắng nổi trên mặt nước hoặc sàng ăn, tôm chậm lớn, ruột không đầy, sức ăn giảm kéo dài.",
                    "EHP | Vibrio đường ruột | đáy ao dơ | thức ăn kém | quản lý môi trường chưa ổn",
                    "Giai đoạn 1::Giảm thức ăn, kiểm tra sàng ăn kỹ | Siphon phân/thức ăn dư, xử lý đáy ao:: || Giai đoạn 2::Bổ sung men tiêu hóa, vitamin và khoáng | Dùng vi sinh cạnh tranh, ổn định nước:: || Giai đoạn 3::Nếu kéo dài, xét nghiệm EHP và kiểm tra Vibrio | Theo dõi tăng trưởng, điều chỉnh khẩu phần::",
                    0.45D,
                    0.7D,
                    "true"));

            addTemplateRow(sheet, rowIndex, bodyStyle, numberStyle, List.of(
                    "DISEASE",
                    "Bệnh virus",
                    "YHD",
                    "Bệnh đầu vàng",
                    "yellow head disease, YHD, đầu vàng, mang vàng",
                    "đầu ngực vàng, mang vàng, bơi lờ đờ, giảm ăn đột ngột, chết nhanh",
                    "",
                    "Tôm giảm ăn đột ngột, phần đầu ngực/mang chuyển vàng, bơi yếu và có thể chết nhanh khi bùng phát.",
                    "virus YHV | con giống mang mầm bệnh | stress môi trường | lây lan qua nước/dụng cụ",
                    "Giai đoạn 1::Ngưng chuyển tôm/nước/dụng cụ sang ao khác | Tăng oxy, giữ môi trường ổn định:: || Giai đoạn 2::Vớt tôm chết, khử trùng dụng cụ | Lấy mẫu xét nghiệm YHV/PCR:: || Giai đoạn 3::Quản lý an toàn sinh học, hạn chế xả thải | Báo kỹ thuật khi tỷ lệ chết tăng::",
                    0.45D,
                    0.7D,
                    "true"));

            formatKnowledgeSheet(sheet);
            addKnowledgeSheetValidation(sheet);

            Sheet formatSheet = workbook.createSheet("FORMAT_THAM_CHIEU");
            buildTemplateFormatSheet(formatSheet, titleStyle, sectionStyle, guideKeyStyle, guideValueStyle);

            workbook.setActiveSheet(workbook.getSheetIndex(guideSheet));

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Không thể tạo file mẫu import tri thức", exception);
        }
    }

    private Sheet resolveImportSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.getSheet("knowledge");
        return sheet != null ? sheet : workbook.getSheetAt(0);
    }

    private void buildTemplateGuideSheet(
            Sheet sheet,
            CellStyle titleStyle,
            CellStyle sectionStyle,
            CellStyle keyStyle,
            CellStyle valueStyle) {
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 110 * 256);

        int rowIndex = 0;
        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.setHeightInPoints(32);
        createStyledCell(titleRow, 0, "HƯỚNG DẪN IMPORT PHÁC ĐỒ AI DOCTOR", titleStyle);
        createStyledCell(titleRow, 1, "Đọc kỹ sheet này trước khi nhập dữ liệu vào sheet knowledge.", titleStyle);

        rowIndex++;
        rowIndex = addGuideSection(sheet, rowIndex, "CÁCH DÙNG", sectionStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "Sheet nhập dữ liệu", "Nhập/sửa dữ liệu ở sheet knowledge. Không đổi tên sheet, không đổi tên cột, không xóa dòng header.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "Dòng ví dụ", "Các dòng mẫu từ dòng 2 trở xuống dùng để tham khảo format. Có thể xóa hoặc sửa thành dữ liệu thật trước khi import.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "Import mode", "OVERWRITE: cập nhật nếu code đã tồn tại. UPSERT_NEW: bỏ qua code đã tồn tại, chỉ thêm mới.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "Trạng thái sau import", "Dữ liệu hợp lệ sẽ vào trạng thái CHỜ DUYỆT/IN_REVIEW để kỹ sư kiểm tra trước khi dùng chính thức.", keyStyle, valueStyle);

        rowIndex++;
        rowIndex = addGuideSection(sheet, rowIndex, "CỘT BẮT BUỘC", sectionStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "FAQ", "Bắt buộc: type, category, code, name, answer_html. Nên có aliases hoặc symptoms_keywords để AI nhận diện câu hỏi.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "DISEASE", "Bắt buộc: type, category, code, name, symptoms_keywords, signs_summary. Nên có aliases, causes, treatment_stages.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "code", "Viết không dấu, không khoảng trắng, nên dùng chữ hoa và dấu gạch dưới. Ví dụ: WSSV, AHPND_EMS, EHP_WHITE_FECES.", keyStyle, valueStyle);

        rowIndex++;
        rowIndex = addGuideSection(sheet, rowIndex, "QUY ƯỚC FORMAT", sectionStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "aliases / symptoms_keywords", "Nhiều từ khóa phân tách bằng dấu phẩy. Ví dụ: đốm trắng trên vỏ, bơi lờ đờ, giảm ăn.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "causes", "Nhiều nguyên nhân phân tách bằng dấu |. Ví dụ: môi trường biến động | mật độ nuôi cao | mầm bệnh.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "treatment_stages", "Mỗi giai đoạn dùng: Tên giai đoạn::Việc cần làm 1 | Việc cần làm 2::productId1,productId2. Nhiều giai đoạn phân tách bằng ||.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "threshold", "match_threshold và confidence_threshold nhập số từ 0 đến 1. Gợi ý: FAQ 0.35-0.45, DISEASE 0.40-0.50, confidence 0.65-0.75.", keyStyle, valueStyle);
        rowIndex = addGuideRow(sheet, rowIndex, "enabled", "Nhập true để bật, false để tắt. Nếu để trống hệ thống hiểu là true.", keyStyle, valueStyle);

        rowIndex++;
        rowIndex = addGuideSection(sheet, rowIndex, "LƯU Ý AN TOÀN", sectionStyle);
        addGuideRow(sheet, rowIndex, "Nội dung phác đồ", "Không ghi liều lượng nguy hiểm hoặc khẳng định chẩn đoán 100% nếu cần xét nghiệm. Với bệnh virus, nên hướng dẫn xét nghiệm/PCR và quản lý an toàn sinh học.", keyStyle, valueStyle);
    }

    private void buildTemplateFormatSheet(
            Sheet sheet,
            CellStyle titleStyle,
            CellStyle sectionStyle,
            CellStyle keyStyle,
            CellStyle valueStyle) {
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 64 * 256);
        sheet.setColumnWidth(3, 58 * 256);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        createStyledCell(titleRow, 0, "THAM CHIẾU FORMAT TỪNG CỘT", titleStyle);

        Row header = sheet.createRow(2);
        createStyledCell(header, 0, "Cột", sectionStyle);
        createStyledCell(header, 1, "Bắt buộc", sectionStyle);
        createStyledCell(header, 2, "Cách nhập", sectionStyle);
        createStyledCell(header, 3, "Ví dụ", sectionStyle);

        int rowIndex = 3;
        rowIndex = addFormatRow(sheet, rowIndex, "type", "Có", "Chỉ nhập FAQ hoặc DISEASE.", "DISEASE", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "category", "Có", "Nhóm tri thức/phác đồ. Nếu chưa có, hệ thống tự tạo category.", "Bệnh virus", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "code", "Có", "Mã duy nhất để cập nhật về sau. Nên viết HOA_KHONG_DAU.", "WSSV", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "name", "Có", "Tên câu hỏi FAQ hoặc tên bệnh/phác đồ.", "Bệnh đốm trắng", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "aliases", "Không", "Tên gọi khác, phân tách bằng dấu phẩy.", "white spot syndrome virus, bệnh đốm trắng", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "symptoms_keywords", "DISEASE có", "Từ khóa triệu chứng, phân tách bằng dấu phẩy.", "đốm trắng trên vỏ, bơi lờ đờ, giảm ăn", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "answer_html", "FAQ có", "Câu trả lời dạng HTML đơn giản: <p>, <strong>, <ul><li>.", "<p>Nội dung tư vấn...</p>", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "signs_summary", "DISEASE có", "Tóm tắt dấu hiệu chính để AI hiển thị/tư vấn.", "Tôm giảm ăn, bơi yếu, có đốm trắng trên vỏ.", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "causes", "Không", "Nhiều nguyên nhân phân tách bằng dấu |.", "môi trường biến động | mầm bệnh | mật độ cao", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "treatment_stages", "Không", "Tên giai đoạn::Việc 1 | Việc 2::ID sản phẩm. Nhiều giai đoạn phân tách bằng ||.", "Giai đoạn 1::Tăng oxy | Giảm thức ăn::101,102 || Giai đoạn 2::Xét nghiệm PCR::", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "match_threshold", "Không", "Số 0-1. Ngưỡng khớp từ khóa; thấp hơn dễ khớp hơn.", "0.45", keyStyle, valueStyle);
        rowIndex = addFormatRow(sheet, rowIndex, "confidence_threshold", "Không", "Số 0-1. Ngưỡng tin cậy khi chẩn đoán bằng ảnh.", "0.70", keyStyle, valueStyle);
        addFormatRow(sheet, rowIndex, "enabled", "Không", "true để bật, false để tắt.", "true", keyStyle, valueStyle);
    }

    private int addTemplateRow(Sheet sheet, int rowIndex, CellStyle textStyle, CellStyle numberStyle, List<?> values) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(88);
        for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
            Object value = values.get(columnIndex);
            Cell cell = row.createCell(columnIndex);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
                cell.setCellStyle(numberStyle);
            } else {
                cell.setCellValue(value == null ? "" : value.toString());
                cell.setCellStyle(textStyle);
            }
        }
        return rowIndex + 1;
    }

    private int addGuideSection(Sheet sheet, int rowIndex, String title, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(24);
        createStyledCell(row, 0, title, style);
        createStyledCell(row, 1, "", style);
        return rowIndex + 1;
    }

    private int addGuideRow(Sheet sheet, int rowIndex, String key, String value, CellStyle keyStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(42);
        createStyledCell(row, 0, key, keyStyle);
        createStyledCell(row, 1, value, valueStyle);
        return rowIndex + 1;
    }

    private int addFormatRow(
            Sheet sheet,
            int rowIndex,
            String column,
            String required,
            String format,
            String example,
            CellStyle keyStyle,
            CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(48);
        createStyledCell(row, 0, column, keyStyle);
        createStyledCell(row, 1, required, valueStyle);
        createStyledCell(row, 2, format, valueStyle);
        createStyledCell(row, 3, example, valueStyle);
        return rowIndex + 1;
    }

    private void formatKnowledgeSheet(Sheet sheet) {
        int[] widths = {
                14, 22, 22, 30, 44, 54, 72, 54, 54, 96, 18, 22, 14
        };
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, 12));
    }

    private void addKnowledgeSheetValidation(Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();

        DataValidationConstraint typeConstraint = helper.createExplicitListConstraint(new String[]{"FAQ", "DISEASE"});
        DataValidation typeValidation = helper.createValidation(
                typeConstraint,
                new CellRangeAddressList(1, 5000, 0, 0));
        typeValidation.setShowErrorBox(true);
        sheet.addValidationData(typeValidation);

        DataValidationConstraint enabledConstraint = helper.createExplicitListConstraint(new String[]{"true", "false"});
        DataValidation enabledValidation = helper.createValidation(
                enabledConstraint,
                new CellRangeAddressList(1, 5000, 12, 12));
        enabledValidation.setShowErrorBox(true);
        sheet.addValidationData(enabledValidation);
    }

    private Cell createStyledCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }

    private CellStyle createTemplateTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createTemplateSectionStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyThinBorder(style);
        return style;
    }

    private CellStyle createTemplateGuideKeyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        applyThinBorder(style);
        return style;
    }

    private CellStyle createTemplateGuideValueStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        applyThinBorder(style);
        return style;
    }

    private CellStyle createTemplateHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyThinBorder(style);
        return style;
    }

    private CellStyle createTemplateRequiredHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = createTemplateHeaderStyle(workbook);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        return style;
    }

    private CellStyle createTemplateBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        applyThinBorder(style);
        return style;
    }

    private CellStyle createTemplateExampleStyle(XSSFWorkbook workbook) {
        CellStyle style = createTemplateBodyStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createTemplateNumberStyle(XSSFWorkbook workbook) {
        CellStyle style = createTemplateBodyStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        return style;
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    /**
     * KHÔNG @Transactional: khi câu hỏi mơ hồ (nhiều bệnh cùng khớp) hoặc gần đạt ngưỡng 1 bệnh,
     * hàm này mở/tiếp tục một phiên hỏi-đáp gọi Gemini (HTTP ra ngoài, tối đa ~40s) qua
     * bootstrapChatClarify/continueChatClarify — cùng lý do với AiDoctorClarifyService#continueClarify:
     * bọc trong 1 transaction sẽ giữ connection DB mở suốt thời gian gọi Gemini. Từng thao tác DB
     * (save/find) bên dưới đã tự có transaction riêng ở tầng repository.
     */
    public AiChatResponse answerChat(
            AiDoctorChatRequest request,
            Long userId,
            String sourceChannel,
            boolean createReviewCaseWhenUnmatched) {
        return answerChat(request, userId, sourceChannel, createReviewCaseWhenUnmatched, null);
    }

    /**
     * Overload danh cho trang "Chat thu nghiem" — cho phep xem truoc 1 phac do chua duyet
     * (previewDiseaseCode) bang cach chen no vao snapshot khi khop tri thuc. Moi duong chat/chan
     * doan that cua khach hang deu di qua overload 4-tham-so o tren (previewDiseaseCode luon null),
     * khong bao gio bi anh huong.
     */
    public AiChatResponse answerChat(
            AiDoctorChatRequest request,
            Long userId,
            String sourceChannel,
            boolean createReviewCaseWhenUnmatched,
            String previewDiseaseCode) {
        if (request == null) {
            request = new AiDoctorChatRequest();
        }
        String normalizedMessage = AiKnowledgeTextUtils.normalize(request.getMessage());
        AiKnowledgeChatConfig config = ensureChatConfig();
        String sessionId = trimToNull(request.getSessionId()) != null
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString();

        if (normalizedMessage.isBlank()) {
            return buildChatResponse(config.getGreetingMessage(), sessionId, getSuggestedActionLabels());
        }

        String imageBase64 = trimToNull(request.getImageBase64());
        String imageMimeType = request.getImageMimeType();

        Optional<AiChatClarifySession> activeSession = chatClarifySessionRepository
                .findFirstBySessionIdAndStatusOrderByIdDesc(sessionId, AiClarifySessionStatus.ACTIVE);
        if (activeSession.isPresent() && chatSessionOwnerMatches(activeSession.get(), userId)) {
            AiChatClarifySession session = activeSession.get();
            return readChatCandidateCodes(session).isEmpty()
                    ? continueFreeConsult(session, request.getMessage(), imageBase64, imageMimeType)
                    : continueChatClarify(session, request.getMessage(), imageBase64, imageMimeType, createReviewCaseWhenUnmatched);
        }
        if (activeSession.isPresent()) {
            // sessionId là chuỗi phía client tự sinh và lưu localStorage — trên thiết bị dùng
            // chung, một người khác đăng nhập sau có thể vô tình mang cùng sessionId cũ. Không
            // được tiếp tục hộ hội thoại của người khác: coi như chưa có phiên nào, xử lý như
            // câu hỏi mới (phiên cũ tự hết hiệu lực khi hết turn hoặc người chủ thực sự quay lại).
            log.warn("[AiChatClarify] sessionId={} dang ACTIVE nhung khac userId (session={}, caller={}) — bo qua, xu ly nhu cau hoi moi",
                    sessionId, activeSession.get().getUserId(), userId);
        }

        ApprovedKnowledgeSnapshot snapshot = getSnapshotForPreview(previewDiseaseCode);
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

        if (outcome.clarifyCandidates() != null && !outcome.clarifyCandidates().isEmpty()) {
            return bootstrapChatClarify(sessionId, userId, sourceChannel, request.getMessage(),
                    outcome.clarifyCandidates(), imageBase64, imageMimeType, createReviewCaseWhenUnmatched);
        }

        // Khong khop bat ky tri thuc nao da duyet — thay vi tra thang fallback cung, mo phien tu
        // van tu do voi Gemini (khong bi khoa vao 1 danh sach benh cho truoc). advanceFreeConsult
        // tu dong roi ve dung config.getFallbackMessage() neu Gemini loi/chua cau hinh, nen day
        // van la luoi an toan cuoi cung giong hanh vi cu, khong lam mat guardrail.
        return bootstrapFreeConsult(sessionId, userId, sourceChannel, request.getMessage(),
                imageBase64, imageMimeType, createReviewCaseWhenUnmatched);
    }

    // =========================================================
    // Chat clarify — tái dùng GeminiClarifyClient (đã schema-lock vào candidate APPROVED) để hỏi
    // thêm khi chat gõ chữ khớp mơ hồ hoặc gần đạt ngưỡng, thay vì trả lời cứng/liệt kê rồi bỏ đó.
    // =========================================================

    private boolean chatSessionOwnerMatches(AiChatClarifySession session, Long callerUserId) {
        return Objects.equals(session.getUserId(), callerUserId);
    }

    private AiChatResponse bootstrapChatClarify(
            String sessionId,
            Long userId,
            String sourceChannel,
            String farmerMessage,
            List<PreparedDisease> candidates,
            String imageBase64,
            String imageMimeType,
            boolean createReviewCaseWhenUnmatched) {
        List<AiClarifyCandidateSummary> candidateSummaries = candidates.stream()
                .map(this::toClarifyCandidateSummary)
                .toList();

        AiChatClarifySession session = AiChatClarifySession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .sourceChannel(sourceChannel)
                .candidateDiseaseCodesJson(writeJson(candidateSummaries.stream()
                        .map(AiClarifyCandidateSummary::getDiseaseCode)
                        .toList()))
                .conversationJson(writeJson(List.of(
                        AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_FARMER).text(farmerMessage).build())))
                .turnCount(0)
                .status(AiClarifySessionStatus.ACTIVE)
                .build();
        session = chatClarifySessionRepository.save(session);

        return advanceChatClarify(session, candidateSummaries, farmerMessage, imageBase64, imageMimeType, createReviewCaseWhenUnmatched);
    }

    private AiChatResponse continueChatClarify(
            AiChatClarifySession session, String farmerAnswer, String imageBase64, String imageMimeType,
            boolean createReviewCaseWhenUnmatched) {
        List<AiClarifyTurn> turns = new ArrayList<>(readChatTurns(session));
        turns.add(AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_FARMER).text(farmerAnswer).build());
        session.setConversationJson(writeJson(turns));
        session = chatClarifySessionRepository.save(session);

        if (session.getTurnCount() >= maxClarifyTurns) {
            log.info("[AiChatClarify] sessionId={} cham tran an toan ({} luot), escalate", session.getSessionId(), maxClarifyTurns);
            return escalateChatClarify(session, farmerAnswer, createReviewCaseWhenUnmatched);
        }

        return advanceChatClarify(session, resolveChatCandidates(session), farmerAnswer, imageBase64, imageMimeType, createReviewCaseWhenUnmatched);
    }

    private AiChatResponse advanceChatClarify(
            AiChatClarifySession session,
            List<AiClarifyCandidateSummary> candidates,
            String latestFarmerText,
            String imageBase64,
            String imageMimeType,
            boolean createReviewCaseWhenUnmatched) {
        AiClarifyLlmResult llmResult;
        try {
            llmResult = geminiClarifyClient.clarify(candidates, readChatTurns(session), imageBase64, imageMimeType);
        } catch (Exception ex) {
            log.warn("[AiChatClarify] sessionId={} Gemini call fail, escalate: {}", session.getSessionId(), ex.getMessage());
            return escalateChatClarify(session, latestFarmerText, createReviewCaseWhenUnmatched);
        }
        if (llmResult == null) {
            log.warn("[AiChatClarify] sessionId={} Gemini tra ve null, escalate", session.getSessionId());
            return escalateChatClarify(session, latestFarmerText, createReviewCaseWhenUnmatched);
        }

        String responseType = llmResult.getResponseType();

        if ("DECISION".equalsIgnoreCase(responseType)) {
            Optional<PreparedDisease> decided = validateChatDecision(session, llmResult.getDiseaseCode());
            if (decided.isEmpty()) {
                log.warn("[AiChatClarify] sessionId={} Gemini decision khong hop le (diseaseCode={}), escalate",
                        session.getSessionId(), llmResult.getDiseaseCode());
                return escalateChatClarify(session, latestFarmerText, createReviewCaseWhenUnmatched);
            }
            return finalizeChatDecision(session, decided.get(), latestFarmerText);
        }

        if ("QUESTION".equalsIgnoreCase(responseType) && trimToNull(llmResult.getQuestionText()) != null) {
            String question = llmResult.getQuestionText().trim();

            // Phong thu o tang code, khong chi dua vao prompt: neu Gemini hoi lai gan nhu y nguyen
            // cau hoi truoc (loi model / bi dan vao vong lap "vet") thi khong tiep tuc hoi them —
            // escalate ngay thay vi de nong dan bi hoi lap lai toi khi cham tran max-turns.
            if (isRepeatOfLastAssistantQuestion(session, question)) {
                log.warn("[AiChatClarify] sessionId={} Gemini hoi lap lai cau truoc, khong tien trien, escalate",
                        session.getSessionId());
                return escalateChatClarify(session, latestFarmerText, createReviewCaseWhenUnmatched);
            }

            List<AiClarifyTurn> turns = new ArrayList<>(readChatTurns(session));
            turns.add(AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_ASSISTANT).text(question).build());
            session.setConversationJson(writeJson(turns));
            session.setTurnCount(session.getTurnCount() + 1);
            chatClarifySessionRepository.save(session);

            persistChatLog(session.getUserId(), session.getSessionId(), session.getSourceChannel(),
                    latestFarmerText, question, false, AiKnowledgeMatchType.AMBIGUOUS, null, null);

            // escapeHtml ở biên trả về (FE render "reply" bằng dangerouslySetInnerHTML): question
            // là văn bản do Gemini sinh ra, không phải HTML admin đã duyệt như answerHtml/fallback
            // — câu hỏi người dùng (farmer) gửi lên nằm trong lịch sử hội thoại gửi cho Gemini nên
            // về lý thuyết có thể prompt-inject Gemini in ra thẻ HTML/script trong questionText.
            // Lưu turns ở trên vẫn giữ bản gốc (chưa escape) để gửi lại đúng nguyên văn cho Gemini
            // ở lượt sau — chỉ escape đúng lúc trả ra ngoài.
            return buildChatResponse(formatPlainTextAsHtml(question), session.getSessionId(), Collections.emptyList());
        }

        log.warn("[AiChatClarify] sessionId={} Gemini tra ve output khong hop le: responseType={}",
                session.getSessionId(), responseType);
        return escalateChatClarify(session, latestFarmerText, createReviewCaseWhenUnmatched);
    }

    private Optional<PreparedDisease> validateChatDecision(AiChatClarifySession session, String diseaseCode) {
        String normalized = trimToNull(diseaseCode);
        if (normalized == null || !readChatCandidateCodes(session).contains(normalized)) {
            return Optional.empty();
        }
        // Guardrail cuối: bệnh đó phải vẫn đang APPROVED tại thời điểm chốt, không chỉ dựa vào
        // tập candidate đã khoá lúc bắt đầu phiên.
        return findDiseaseByCodeFromSnapshot(normalized, getApprovedSnapshot());
    }

    private AiChatResponse finalizeChatDecision(AiChatClarifySession session, PreparedDisease disease, String latestFarmerText) {
        session.setStatus(AiClarifySessionStatus.DECIDED);
        session.setDecidedDiseaseCode(disease.entity().getCode());
        chatClarifySessionRepository.save(session);

        String answerHtml = buildRequestImageAnswerHtml(disease);
        persistChatLog(session.getUserId(), session.getSessionId(), session.getSourceChannel(),
                latestFarmerText, answerHtml, true, AiKnowledgeMatchType.DISEASE_KNOWLEDGE, disease.entity().getCode(), 1D);

        log.info("[AiChatClarify] sessionId={} DECIDED: diseaseCode={}, turns={}",
                session.getSessionId(), disease.entity().getCode(), session.getTurnCount());

        return buildChatResponse(answerHtml, session.getSessionId(), getSuggestedActionLabels());
    }

    private AiChatResponse escalateChatClarify(
            AiChatClarifySession session, String latestFarmerText, boolean createReviewCaseWhenUnmatched) {
        session.setStatus(AiClarifySessionStatus.ESCALATED);
        if (createReviewCaseWhenUnmatched) {
            createReviewCaseSafely(
                    session.getUserId(),
                    session.getSessionId(),
                    session.getSourceChannel(),
                    latestFarmerText,
                    null,
                    null,
                    null,
                    null,
                    0D,
                    AiReviewCaseReason.LOW_CONFIDENCE)
                    .ifPresent(reviewCase -> session.setReviewCaseId(reviewCase.getId()));
        }
        chatClarifySessionRepository.save(session);

        persistChatLog(session.getUserId(), session.getSessionId(), session.getSourceChannel(),
                latestFarmerText, CHAT_CLARIFY_ESCALATION_MESSAGE, false, AiKnowledgeMatchType.AMBIGUOUS, null, 0D);

        return buildChatResponse(CHAT_CLARIFY_ESCALATION_MESSAGE, session.getSessionId(), getSuggestedActionLabels());
    }

    // =========================================================
    // Free consult — khi chat gõ chữ KHÔNG khớp bất kỳ tri thức nào đã duyệt (không phải AMBIGUOUS/
    // near-miss — hoàn toàn không có candidate). Thay vì trả thẳng fallback cứng, để Gemini tư vấn
    // sơ bộ mở (không khoá vào danh sách bệnh cho trước) — nhưng KHÔNG BAO GIỜ dùng làm phác đồ
    // chính thức: buildFreeConsultAnswerHtml luôn tự thêm khuyến cáo liên hệ kỹ sư, không dựa vào
    // Gemini tự nhớ thêm. Session dùng chung bảng/luồng với chat clarify, chỉ khác candidate list
    // rỗng "[]" — đó là dấu hiệu để answerChat() phân biệt 2 chế độ khi tiếp tục hội thoại.
    // =========================================================

    private AiChatResponse bootstrapFreeConsult(
            String sessionId,
            Long userId,
            String sourceChannel,
            String farmerMessage,
            String imageBase64,
            String imageMimeType,
            boolean createReviewCaseWhenUnmatched) {
        AiChatClarifySession session = AiChatClarifySession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .sourceChannel(sourceChannel)
                .candidateDiseaseCodesJson(writeJson(Collections.emptyList()))
                .conversationJson(writeJson(List.of(
                        AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_FARMER).text(farmerMessage).build())))
                .turnCount(0)
                .status(AiClarifySessionStatus.ACTIVE)
                .build();
        session = chatClarifySessionRepository.save(session);

        if (createReviewCaseWhenUnmatched) {
            Optional<AiKnowledgeReviewCase> reviewCase = createReviewCaseSafely(
                    userId, sessionId, sourceChannel, farmerMessage,
                    null, null, null, null, 0D, AiReviewCaseReason.NO_KNOWLEDGE_MATCH);
            if (reviewCase.isPresent()) {
                session.setReviewCaseId(reviewCase.get().getId());
                session = chatClarifySessionRepository.save(session);
            }
        }

        return advanceFreeConsult(session, farmerMessage, imageBase64, imageMimeType);
    }

    private AiChatResponse continueFreeConsult(
            AiChatClarifySession session, String farmerAnswer, String imageBase64, String imageMimeType) {
        List<AiClarifyTurn> turns = new ArrayList<>(readChatTurns(session));
        turns.add(AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_FARMER).text(farmerAnswer).build());
        session.setConversationJson(writeJson(turns));
        session = chatClarifySessionRepository.save(session);

        if (session.getTurnCount() >= maxClarifyTurns) {
            log.info("[AiFreeConsult] sessionId={} cham tran an toan ({} luot), dung tu van mo",
                    session.getSessionId(), maxClarifyTurns);
            session.setStatus(AiClarifySessionStatus.ESCALATED);
            chatClarifySessionRepository.save(session);
            persistChatLog(session.getUserId(), session.getSessionId(), session.getSourceChannel(),
                    farmerAnswer, CHAT_CLARIFY_ESCALATION_MESSAGE, false, null, null, 0D);
            return buildChatResponse(CHAT_CLARIFY_ESCALATION_MESSAGE, session.getSessionId(), getSuggestedActionLabels());
        }

        return advanceFreeConsult(session, farmerAnswer, imageBase64, imageMimeType);
    }

    private AiChatResponse advanceFreeConsult(
            AiChatClarifySession session, String latestFarmerText, String imageBase64, String imageMimeType) {
        AiKnowledgeChatConfig config = ensureChatConfig();
        String geminiText;
        try {
            geminiText = geminiClarifyClient.freeConsult(readChatTurns(session), imageBase64, imageMimeType);
        } catch (Exception ex) {
            log.warn("[AiFreeConsult] sessionId={} Gemini call fail, dung fallback tinh: {}",
                    session.getSessionId(), ex.getMessage());
            persistChatLog(session.getUserId(), session.getSessionId(), session.getSourceChannel(),
                    latestFarmerText, config.getFallbackMessage(), false, null, null, 0D);
            return buildChatResponse(config.getFallbackMessage(), session.getSessionId(), getSuggestedActionLabels());
        }

        List<AiClarifyTurn> turns = new ArrayList<>(readChatTurns(session));
        turns.add(AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_ASSISTANT).text(geminiText).build());
        session.setConversationJson(writeJson(turns));
        session.setTurnCount(session.getTurnCount() + 1);
        chatClarifySessionRepository.save(session);

        String answerHtml = buildFreeConsultAnswerHtml(geminiText, config, isGreetingOnly(latestFarmerText));
        persistChatLog(session.getUserId(), session.getSessionId(), session.getSourceChannel(),
                latestFarmerText, answerHtml, false, null, null, null);

        return buildChatResponse(answerHtml, session.getSessionId(), Collections.emptyList());
    }

    /**
     * geminiText la van ban tu do (khong phai HTML admin duyet) — escape roi format thanh p/ul,
     * KHONG dua thang vao dangerouslySetInnerHTML o FE. Dong khuyen cao lien he ky su luon do
     * code tu them (khong phu thuoc Gemini co nho nhac hay khong) — day la guardrail chinh cua che
     * do tu van mo: khong bao gio de nong dan hieu day la phac do da duyet.
     */
    private static final Set<String> GREETING_ONLY_PHRASES = Set.of(
            "xin chao", "chao", "chao ban", "chao bac", "chao anh", "chao chi",
            "chao bac si", "chao bac si tom", "hi", "hello", "hey", "alo");

    /**
     * Loi chao suong (khong kem trieu chung/cau hoi gi) thi bo qua dong khuyen cao lien he ky su —
     * dong do chi hop ly khi Gemini vua dua ra nhan dinh/goi y benh dua tren dau hieu nguoi dung mo
     * ta, khong phai khi ca cuoc hoi thoai moi chi la loi chao. Doi sanh CHINH XAC sau khi chuan hoa
     * (bo dau, ha chu, bo ky tu la) de tranh bo sot dong khuyen cao voi nhung tin nhan vua chao vua
     * co trieu chung thuc, vd "chao ban, tom bi do dau" van giu nguyen khuyen cao.
     */
    private boolean isGreetingOnly(String farmerText) {
        return GREETING_ONLY_PHRASES.contains(AiKnowledgeTextUtils.normalize(farmerText));
    }

    private String buildFreeConsultAnswerHtml(String geminiText, AiKnowledgeChatConfig config, boolean isGreetingOnly) {
        return buildFreeConsultAnswerHtml(geminiText, config, isGreetingOnly, false);
    }

    private String buildFreeConsultAnswerHtml(
            String geminiText,
            AiKnowledgeChatConfig config,
            boolean isGreetingOnly,
            boolean suppressImageObservationIntro) {
        StringBuilder builder = new StringBuilder();
        String cleanedGeminiText = cleanFreeConsultText(geminiText, !isGreetingOnly, suppressImageObservationIntro);
        builder.append(formatFreeConsultTextAsHtml(cleanedGeminiText));

        if (isGreetingOnly) {
            return builder.toString();
        }

        builder.append(buildEngineerContactFooterHtml(config));
        return builder.toString();
    }

    private String buildEngineerContactFooterHtml(AiKnowledgeChatConfig config) {
        String contactName = trimToNull(config.getFallbackContactName());
        String contactPhone = trimToNull(config.getFallbackContactPhone());
        StringBuilder builder = new StringBuilder("<p><em>Vui lòng liên hệ ngay kỹ sư thủy sản");
        if (contactName != null || contactPhone != null) {
            builder.append(": ").append(buildEngineerAvatarHtml());
            if (contactName != null) {
                builder.append(" <strong>").append(escapeHtml(contactName)).append("</strong>");
            }
            if (contactPhone != null) {
                builder.append(contactName != null ? ": " : "").append(escapeHtml(contactPhone));
            }
        }
        builder.append(" để được hỗ trợ chính xác nhất.</em></p>");
        return builder.toString();
    }

    private String buildEngineerAvatarHtml() {
        return "<span style=\"display:inline-flex;align-items:center;justify-content:center;"
                + "width:22px;height:22px;border-radius:999px;background:#e8f1ff;color:#1965a2;"
                + "font-weight:700;font-size:10px;margin:0 6px;vertical-align:middle;\">KS</span>";
    }

    private String cleanFreeConsultText(String text, boolean stripGreeting, boolean stripImageObservationIntro) {
        String normalizedText = trimToNull(text);
        if (normalizedText == null) {
            return "";
        }

        List<String> paragraphs = new ArrayList<>(Arrays.stream(normalizedText
                        .replace("\r\n", "\n")
                        .replace("\r", "\n")
                        .split("\n\\s*\n"))
                .map(String::trim)
                .filter(paragraph -> !paragraph.isBlank())
                .toList());

        while (!paragraphs.isEmpty()) {
            String first = paragraphs.get(0);
            if (stripGreeting && looksLikeFreeConsultGreeting(first)) {
                paragraphs.remove(0);
                stripGreeting = false;
                continue;
            }
            if (stripImageObservationIntro && looksLikeRepeatedImageObservationIntro(first)) {
                paragraphs.remove(0);
                stripImageObservationIntro = false;
                continue;
            }
            break;
        }

        return String.join("\n\n", paragraphs);
    }

    private boolean looksLikeFreeConsultGreeting(String paragraph) {
        String normalized = AiKnowledgeTextUtils.normalize(paragraph);
        return normalized != null
                && normalized.length() <= 140
                && normalized.contains("bac si tom")
                && (normalized.startsWith("chao") || normalized.startsWith("xin chao"));
    }

    private boolean looksLikeRepeatedImageObservationIntro(String paragraph) {
        String normalized = AiKnowledgeTextUtils.normalize(paragraph);
        if (normalized == null) {
            return false;
        }
        return normalized.startsWith("dua tren hinh anh")
                || normalized.startsWith("dua tren anh")
                || normalized.startsWith("minh quan sat thay")
                || normalized.startsWith("quan sat thay")
                || normalized.startsWith("anh ban gui")
                || normalized.startsWith("nhin vao anh")
                || normalized.startsWith("nhin vao hinh");
    }

    private String formatPlainTextAsHtml(String text) {
        return escapeHtml(text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "<br>");
    }

    private String formatFreeConsultTextAsHtml(String text) {
        String html = AiTextFormatUtils.plainTextToHtml(text);
        return trimToNull(html) != null ? html : "";
    }

    private boolean isRepeatOfLastAssistantQuestion(AiChatClarifySession session, String newQuestion) {
        List<AiClarifyTurn> turns = readChatTurns(session);
        for (int i = turns.size() - 1; i >= 0; i--) {
            AiClarifyTurn turn = turns.get(i);
            if (AiClarifyTurn.ROLE_ASSISTANT.equals(turn.getRole())) {
                return newQuestion.equalsIgnoreCase(trimToNull(turn.getText()));
            }
        }
        return false;
    }

    private List<AiClarifyTurn> readChatTurns(AiChatClarifySession session) {
        List<AiClarifyTurn> turns = readJsonList(session.getConversationJson(), new TypeReference<List<AiClarifyTurn>>() {
        });
        return turns != null ? turns : Collections.emptyList();
    }

    private List<String> readChatCandidateCodes(AiChatClarifySession session) {
        List<String> codes = readJsonList(session.getCandidateDiseaseCodesJson(), new TypeReference<List<String>>() {
        });
        return codes != null ? codes : Collections.emptyList();
    }

    private List<AiClarifyCandidateSummary> resolveChatCandidates(AiChatClarifySession session) {
        ApprovedKnowledgeSnapshot snapshot = getApprovedSnapshot();
        return readChatCandidateCodes(session).stream()
                .map(code -> findDiseaseByCodeFromSnapshot(code, snapshot))
                .flatMap(Optional::stream)
                .map(this::toClarifyCandidateSummary)
                .toList();
    }

    // KHONG @Transactional: nhanh NO_KNOWLEDGE_MATCH goi buildUnrecognizedDiagnosisResponse(...),
    // ben trong co goi Gemini qua HTTP (freeConsult) — giu mot transaction readOnly mo suot thoi
    // gian do se giu connection DB khong can thiet, cung ly do da tranh o answerChat/
    // AiDoctorClarifyService.continueClarify. getApprovedSnapshot()/createReviewCase() ben duoi tu
    // co transaction rieng o tang repository/service, khong phu thuoc mot transaction bao ngoai.
    public AiDoctorDiagnosisResponse enrichDiagnosis(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl,
            String userSymptoms,
            String sessionId,
            Long userId,
            String geminiImageDescription) {
        return enrichDiagnosis(predictResponse, finalPrediction, diagnosisImageUrl, userSymptoms,
                sessionId, userId, geminiImageDescription, null, true);
    }

    /**
     * Overload danh cho luong test anh ("Chat thu nghiem") — cho phep xem truoc 1 phac do chua duyet
     * (previewDiseaseCode) va tuy chon khong tao review case that (allowReviewCase=false) de khong lam
     * nhieu hang doi "Cau hoi chua dap" cua khach that. Duong chan doan that (AiDoctorDiagnosisService
     * .diagnose) luon goi overload 7-tham-so o tren, khong bao gio bi anh huong.
     */
    public AiDoctorDiagnosisResponse enrichDiagnosis(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl,
            String userSymptoms,
            String sessionId,
            Long userId,
            String geminiImageDescription,
            String previewDiseaseCode,
            boolean allowReviewCase) {
        ApprovedKnowledgeSnapshot snapshot = getSnapshotForPreview(previewDiseaseCode);
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
                        "DISEASE",
                        geminiImageDescription);
            }

            if (allowReviewCase) {
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
            }

            return buildLowConfidenceDiagnosisResponse(
                    predictResponse, finalPrediction, diagnosisImageUrl, geminiImageDescription, resolvedDisease);
        }

        return buildUnrecognizedDiagnosisResponse(
                predictResponse, finalPrediction, diagnosisImageUrl, userSymptoms, sessionId, userId, geminiImageDescription, allowReviewCase);
    }

    @Transactional(readOnly = true)
    public AiDoctorDiagnosisResponse buildPrescriptionFromApprovedKnowledge(String diseaseCode, Long diagnosisId) {
        PreparedDisease disease = findDiseaseByCodeFromSnapshot(diseaseCode, getApprovedSnapshot())
                .orElseThrow(() -> notFound("Chưa có tri thức APPROVED cho bệnh: " + diseaseCode));

        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId(diagnosisId != null ? String.valueOf(diagnosisId) : null)
                .disease(toDiseaseResponse(disease, null))
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
        entity.setImageUrlsJson(writeJson(defaultList(request.getImageUrls())));
        entity.setEngineerName(trimToNull(request.getEngineerName()));
        entity.setEngineerPhone(trimToNull(request.getEngineerPhone()));
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
                        .stageSigns(stage.getStageSigns())
                        .treatmentGoal(stage.getTreatmentGoal())
                        .instructions(stage.getInstructions())
                        .productIds(stage.getProductIds())
                        .extraProductNames(stage.getExtraProductNames())
                        .subStages(defaultList(stage.getSubStages()).stream()
                                .map(subStage -> AiKnowledgeTreatmentSubStageRequest.builder()
                                        .subStageTitle(subStage.getSubStageTitle())
                                        .instructions(subStage.getInstructions())
                                        .productIds(subStage.getProductIds())
                                        .extraProductNames(subStage.getExtraProductNames())
                                        .build())
                                .toList())
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
                        .answerHtml(buildRequestImageAnswerHtml(disease))
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
                        .answerHtml(buildRequestImageAnswerHtml(namedDiseaseScore.disease()))
                        .build();
            }
        }

        MatchScore diseaseMatch = matchDiseaseFromText(normalizedMessage, snapshot.diseaseEntries);
        MatchScore keywordMatch = matchKeywordSetFromText(normalizedMessage, snapshot.keywordEntries);

        // Nhiều bệnh cùng vượt ngưỡng riêng của chúng (vd "tôm lờ đờ" là dấu hiệu chung của nhiều
        // bệnh) → không tự chọn đại 1 bệnh và cũng không đoán mò: trả về danh sách candidate để
        // answerChat() mở phiên hỏi-đáp Gemini (giống luồng ảnh) nhằm hỏi thêm câu phân biệt thay
        // vì chỉ liệt kê tên bệnh rồi bỏ đó. Chỉ áp dụng cho luồng gõ chữ tự do — hai nhánh phía
        // trên (đã có diseaseCode/diseaseName xác định từ ảnh) không đi qua đây.
        List<PreparedDisease> qualifyingDiseases = findQualifyingDiseases(normalizedMessage, snapshot.diseaseEntries);
        if (qualifyingDiseases.size() > 1) {
            return MatchOutcome.builder()
                    .matched(false)
                    .matchType(AiKnowledgeMatchType.AMBIGUOUS)
                    .score(diseaseMatch.score())
                    .clarifyCandidates(buildClarifyCandidateList(normalizedMessage, snapshot.diseaseEntries, diseaseMatch.disease()))
                    .build();
        }

        boolean diseaseWins = diseaseMatch.score() > 0D
                && diseaseMatch.disease() != null
                && diseaseMatch.score() >= defaultDouble(diseaseMatch.disease().entity().getMatchThreshold(), 0.4D)
                && diseaseMatch.score() + 0.05D >= keywordMatch.score();
        if (diseaseWins) {
            return MatchOutcome.builder()
                    .matched(false)
                    .matchType(AiKnowledgeMatchType.DISEASE_KNOWLEDGE)
                    .score(diseaseMatch.score())
                    .clarifyCandidates(buildClarifyCandidateList(normalizedMessage, snapshot.diseaseEntries, diseaseMatch.disease()))
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

        // Không đủ điểm để kết luận, nhưng đủ gần ngưỡng của chính bệnh đó để tin là người dùng
        // đang mô tả đúng hướng và chỉ thiếu vài dấu hiệu — mở phiên hỏi-đáp Gemini để hỏi thêm
        // (dựa trên symptomKeywordsRaw/signsSummary của đúng bệnh này) thay vì rơi thẳng fallback.
        if (diseaseMatch.score() > 0D && diseaseMatch.disease() != null) {
            double threshold = defaultDouble(diseaseMatch.disease().entity().getMatchThreshold(), 0.4D);
            if (diseaseMatch.score() >= threshold * NEAR_MISS_THRESHOLD_RATIO) {
                return MatchOutcome.builder()
                        .matched(false)
                        .matchType(AiKnowledgeMatchType.DISEASE_KNOWLEDGE)
                        .score(diseaseMatch.score())
                        .clarifyCandidates(buildClarifyCandidateList(normalizedMessage, snapshot.diseaseEntries, diseaseMatch.disease()))
                        .build();
            }
        }

        return MatchOutcome.builder().matched(false).build();
    }

    private MatchScore matchDiseaseFromText(String normalizedMessage, List<PreparedDisease> diseases) {
        MatchScore best = MatchScore.none();
        Set<String> messageTokens = new LinkedHashSet<>(AiKnowledgeTextUtils.tokenize(normalizedMessage));

        for (PreparedDisease disease : diseases) {
            double score = scoreDiseaseForMessage(disease, normalizedMessage, messageTokens);

            if (score > best.score()) {
                best = MatchScore.disease(score, disease);
            }
        }
        return best;
    }

    /**
     * Mọi bệnh vượt NGƯỠNG RIÊNG của chính nó — không chỉ 1 bệnh điểm cao nhất. Dùng để phát hiện
     * câu mô tả mơ hồ khớp cùng lúc nhiều bệnh (vd "tôm lờ đờ"), để không tự ý chọn đại 1 bệnh.
     */
    private List<PreparedDisease> findQualifyingDiseases(String normalizedMessage, List<PreparedDisease> diseases) {
        Set<String> messageTokens = new LinkedHashSet<>(AiKnowledgeTextUtils.tokenize(normalizedMessage));
        List<PreparedDisease> qualifying = new ArrayList<>();
        for (PreparedDisease disease : diseases) {
            double score = scoreDiseaseForMessage(disease, normalizedMessage, messageTokens);
            if (score >= defaultDouble(disease.entity().getMatchThreshold(), 0.4D)) {
                qualifying.add(disease);
            }
        }
        return qualifying;
    }

    private List<PreparedDisease> buildClarifyCandidateList(
            String normalizedMessage,
            List<PreparedDisease> diseases,
            PreparedDisease primaryDisease) {
        Set<String> messageTokens = new LinkedHashSet<>(AiKnowledgeTextUtils.tokenize(normalizedMessage));
        List<CandidateDiseaseScore> scoredDiseases = diseases.stream()
                .map(disease -> new CandidateDiseaseScore(
                        disease,
                        scoreDiseaseForMessage(disease, normalizedMessage, messageTokens)))
                .sorted(Comparator.comparingDouble(CandidateDiseaseScore::score).reversed())
                .toList();

        List<PreparedDisease> result = new ArrayList<>();
        Set<String> addedCodes = new LinkedHashSet<>();
        addClarifyCandidate(result, addedCodes, primaryDisease);

        for (CandidateDiseaseScore scored : scoredDiseases) {
            if (result.size() >= CHAT_CLARIFY_CANDIDATE_LIMIT) {
                break;
            }
            if (scored.score() <= 0D) {
                continue;
            }

            double threshold = defaultDouble(scored.disease().entity().getMatchThreshold(), 0.4D);
            boolean relatedEnough = scored.score() >= Math.max(RELATED_DISEASE_MIN_SCORE, threshold * NEAR_MISS_THRESHOLD_RATIO);
            boolean needAtLeastTwoCandidates = result.size() < 2 && scored.score() > 0D;
            if (relatedEnough || needAtLeastTwoCandidates) {
                addClarifyCandidate(result, addedCodes, scored.disease());
            }
        }

        if (result.isEmpty()) {
            addClarifyCandidate(result, addedCodes, primaryDisease);
        }
        return result;
    }

    private void addClarifyCandidate(List<PreparedDisease> result, Set<String> addedCodes, PreparedDisease disease) {
        if (disease == null || trimToNull(disease.entity().getCode()) == null) {
            return;
        }
        if (addedCodes.add(disease.entity().getCode())) {
            result.add(disease);
        }
    }

    private double scoreDiseaseForMessage(PreparedDisease disease, String normalizedMessage, Set<String> messageTokens) {
        double score = scoreAgainstKeywords(normalizedMessage, messageTokens, disease.keywords());
        score += disease.entity().getCanonical() != null && disease.entity().getCanonical() ? 0.03D : 0D;
        score += Math.min(defaultInt(disease.entity().getPriority(), 0), 10) * 0.002D;
        return score;
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

            MatchScore fuzzyNameMatch = matchDiseaseFromText(normalizedCandidate, snapshot.diseaseEntries);
            if (fuzzyNameMatch.disease() != null && fuzzyNameMatch.score() >= 0.55D) {
                return fuzzyNameMatch.disease();
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
            String status,
            String geminiImageDescription) {
        List<TreatmentStageResponse> treatmentStages = toTreatmentStageResponses(disease.entity().getTreatmentStagesJson());

        AiDoctorDiagnosisResponse.AiDoctorDiagnosisResponseBuilder responseBuilder = AiDoctorDiagnosisResponse.builder()
                .diagnosisId("diag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .status(status)
                .imageUrl(diagnosisImageUrl)
                .disease(toDiseaseResponse(disease, finalPrediction.getConfidencePercent()))
                .topPredictions(toTopPredictions(predictResponse))
                .causes(defaultList(readJsonList(disease.entity().getCausesJson(), new TypeReference<List<String>>() {
                })))
                .signsSummary(disease.entity().getSignsSummary())
                .aiDescription(buildDiagnosisNarrativeHtml(geminiImageDescription, finalPrediction, disease, false));

        if (treatmentStages.size() > 1) {
            responseBuilder.stageSelection(TreatmentStageSelectionResponse.fromStages(treatmentStages));
        } else if (treatmentStages.size() == 1) {
            List<TreatmentStageResponse> subStages = defaultList(treatmentStages.get(0).getSubStages());
            if (!subStages.isEmpty()) {
                responseBuilder.treatmentStages(numberedSubStages(subStages, 0));
            } else {
                responseBuilder.treatmentStages(treatmentStages);
            }
        }

        return responseBuilder.build();
    }

    private AiDoctorDiagnosisResponse buildLowConfidenceDiagnosisResponse(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl,
            String geminiImageDescription,
            PreparedDisease disease) {
        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId("diag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .status("DISEASE")
                .imageUrl(diagnosisImageUrl)
                .disease(disease != null
                        ? toDiseaseResponse(disease, finalPrediction.getConfidencePercent())
                        : DiseaseResponse.builder()
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
                .aiDescription(buildDiagnosisNarrativeHtml(geminiImageDescription, finalPrediction, null, true))
                .build();
    }

    /**
     * YOLO khong nhan ra benh cu the nao — hoac finalPrediction null hoan toan (goi truc tiep tu
     * AiDoctorDiagnosisService khi predict khong tra ve diseaseCode gi), hoac co disease
     * code/ten nhung khong khop bat ky tri thuc da duyet nao (nhanh NO_KNOWLEDGE_MATCH cua
     * enrichDiagnosis). Ca 2 truong hop deu khong con candidate nao de mo AiDoctorClarifySession
     * hoi them (se escalate ngay lap tuc, ra 2 bubble du thua/mau thuan nhau) — nen thay vao do
     * goi Gemini tu van tu do (nhu free-consult) roi luon tu dong kem disclaimer + lien he ky su
     * mac dinh, tra ve nhu 1 cau tra loi cuoi (khong set needsClarification).
     *
     * KHONG @Transactional — goi Gemini qua HTTP (freeConsult, toi ~45s), giu mot transaction mo
     * suot thoi gian do se giu connection DB khong can thiet, cung ly do da tranh o answerChat/
     * AiDoctorClarifyService.continueClarify.
     */
    public AiDoctorDiagnosisResponse buildUnrecognizedDiagnosisResponse(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl,
            String userSymptoms,
            String sessionId,
            Long userId,
            String geminiImageDescription) {
        return buildUnrecognizedDiagnosisResponse(predictResponse, finalPrediction, diagnosisImageUrl,
                userSymptoms, sessionId, userId, geminiImageDescription, true);
    }

    public AiDoctorDiagnosisResponse buildUnrecognizedDiagnosisResponse(
            AiPredictResponse predictResponse,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl,
            String userSymptoms,
            String sessionId,
            Long userId,
            String geminiImageDescription,
            boolean allowReviewCase) {
        AiKnowledgeChatConfig config = ensureChatConfig();
        String farmerContext = buildFarmerContextForImageSuggestion(userSymptoms, geminiImageDescription);

        String geminiText;
        try {
            // Khong gui lai anh goc lan 2 — geminiImageDescription da duoc Gemini "nhin" qua
            // describeImage() 1 lan roi, dung lai lam ngu canh text de tranh goi Gemini vision
            // 2 lan/request (ton phi + do tre).
            geminiText = geminiClarifyClient.freeConsult(
                    List.of(AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_FARMER).text(farmerContext).build()),
                    null, null);
        } catch (Exception ex) {
            log.warn("[AiUnrecognized] Gemini goi y that bai, dung fallback tinh: {}", ex.getMessage());
            geminiText = null;
        }

        StringBuilder html = new StringBuilder(
                buildImageObservationPrefixHtml(geminiImageDescription, finalPrediction, null));
        html.append(trimToNull(geminiText) != null
                ? buildFreeConsultAnswerHtml(geminiText, config, false, trimToNull(geminiImageDescription) != null)
                : "<p>" + escapeHtml(config.getFallbackMessage()) + "</p>");

        if (allowReviewCase) {
            createReviewCase(
                    userId,
                    sessionId,
                    "AI_DOCTOR_DIAGNOSIS",
                    userSymptoms,
                    userSymptoms,
                    diagnosisImageUrl,
                    finalPrediction != null ? finalPrediction.getDiseaseCode() : null,
                    null,
                    finalPrediction != null && finalPrediction.getConfidencePercent() != null
                            ? finalPrediction.getConfidencePercent() / 100D : 0D,
                    AiReviewCaseReason.NO_KNOWLEDGE_MATCH);
        }

        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId("unrec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .status("UNRECOGNIZED")
                .imageUrl(diagnosisImageUrl)
                .topPredictions(toTopPredictions(predictResponse))
                .aiDescription(html.toString())
                .build();
    }

    /**
     * YOLO bao NON_SHRIMP nhung Gemini Vision co the van thay day la anh tom (model detect fail
     * vi goc chup/nen anh/box khong ro). Khi narrative co dau hieu nhin thay tom, fallback sang
     * free-consult de LLM neu nghi ngo + hoi them nhu luong cu; chi bao "gui lai anh" neu ca Gemini
     * cung khong thay tom.
     */
    public AiDoctorDiagnosisResponse buildNonShrimpImageResponse(
            AiPredictResponse predictResponse,
            String diagnosisImageUrl,
            String userSymptoms,
            String sessionId,
            Long userId,
            String geminiImageDescription,
            boolean allowReviewCase) {
        if (looksLikeShrimpObservation(geminiImageDescription)) {
            log.info("[AiNonShrimpFallback] YOLO NON_SHRIMP nhung Gemini thay dau hieu tom, chuyen sang tu van mo");
            return buildUnrecognizedDiagnosisResponse(
                    predictResponse,
                    null,
                    diagnosisImageUrl,
                    userSymptoms,
                    sessionId,
                    userId,
                    geminiImageDescription,
                    allowReviewCase);
        }

        String observationHtml = trimToNull(geminiImageDescription) != null
                ? buildImageObservationPrefixHtml(geminiImageDescription, null, null)
                : "";
        String html = observationHtml
                + "<p>Ảnh này mình chưa thấy tôm trong đó, bà con gửi lại giúp mình 1 tấm ảnh chụp rõ con tôm để mình xem kỹ hơn nhé!</p>";

        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId("nonshrimp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .status("UNRECOGNIZED")
                .imageUrl(diagnosisImageUrl)
                .topPredictions(toTopPredictions(predictResponse))
                .aiDescription(html)
                .build();
    }

    private boolean looksLikeShrimpObservation(String geminiImageDescription) {
        String normalized = AiKnowledgeTextUtils.normalize(geminiImageDescription);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }

        List<String> negativePhrases = List.of(
                "khong thay tom",
                "chua thay tom",
                "khong co tom",
                "khong phai tom",
                "khong nhan dien duoc tom",
                "khong nhin thay tom");
        if (negativePhrases.stream().anyMatch(normalized::contains)) {
            return false;
        }

        List<String> shrimpObservationKeywords = List.of(
                "tom",
                "mang tom",
                "vo tom",
                "than tom",
                "dau nguc",
                "duoi tom",
                "chan bo",
                "chan boi",
                "phan quat duoi",
                "gan tuy");
        return shrimpObservationKeywords.stream().anyMatch(normalized::contains);
    }

    private String buildFarmerContextForImageSuggestion(String userSymptoms, String geminiImageDescription) {
        StringBuilder text = new StringBuilder();
        if (trimToNull(userSymptoms) != null) {
            text.append(userSymptoms.trim());
        }
        if (trimToNull(geminiImageDescription) != null) {
            if (text.length() > 0) {
                text.append(". ");
            }
            text.append("Quan sát từ ảnh: ").append(geminiImageDescription.trim());
        }
        if (text.length() == 0) {
            text.append("Đây là ảnh tôm của tôi, hệ thống nhận diện tự động chưa xác định được bệnh cụ thể, bạn xem giúp tôi với.");
        }
        return text.toString();
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
        try {
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
        } catch (Exception ex) {
            log.warn("[AiChatLog] Khong luu duoc chat log sessionId={}: {}", sessionId, ex.getMessage());
        }
    }

    private Optional<AiKnowledgeReviewCase> createReviewCaseSafely(
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
        try {
            return Optional.of(createReviewCase(
                    userId,
                    sessionId,
                    sourceChannel,
                    questionText,
                    userSymptoms,
                    imageUrl,
                    aiSuggestedDiseaseCode,
                    matchedKnowledgeCode,
                    matchScore,
                    reason));
        } catch (Exception ex) {
            log.warn("[AiReviewCase] Khong tao duoc case duyet cho sessionId={}: {}", sessionId, ex.getMessage());
            return Optional.empty();
        }
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
        AiKnowledgeReviewCase saved = reviewCaseRepository.save(AiKnowledgeReviewCase.builder()
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
        try {
            notificationService.notifyAgronomistsReviewCaseCreated(saved);
        } catch (Exception ex) {
            log.warn("[AiReviewCase] Da tao case id={} nhung gui notification that bai: {}", saved.getId(), ex.getMessage());
        }
        return saved;
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

    /**
     * Ghep 1 doan HTML tu nhien cho luong chan doan qua anh (YOLO): mo ta cua Gemini (neu goi thanh
     * cong) + 1 cau nhan dien ten benh do CODE tu dung (khong bao gio giao cho LLM), roi
     * moi toi thong tin nhan dien an toan da co san. Phac do chi tra sau khi nguoi dung mo ket qua va
     * chon dung giai doan neu benh co nhieu stage.
     */
    private String buildDiagnosisNarrativeHtml(
            String geminiImageDescription,
            AiPredictionItem finalPrediction,
            PreparedDisease resolvedDisease,
            boolean needsClarification) {
        StringBuilder builder = new StringBuilder(
                buildImageObservationPrefixHtml(geminiImageDescription, finalPrediction, resolvedDisease));

        if (needsClarification) {
            builder.append("<p>Để chắc chắn hơn, bạn mô tả kỹ thêm dấu hiệu giúp mình nhé.</p>");
        } else if (resolvedDisease != null) {
            builder.append(buildDiseaseIdentityHtml(resolvedDisease));
        }

        return builder.toString();
    }

    /**
     * Mo ta cua Gemini (neu goi thanh cong) + 1 cau nhan dien ten benh do CODE tu dung
     * (khong bao gio giao cho LLM) — dung chung cho buildDiagnosisNarrativeHtml va
     * buildUnrecognizedDiagnosisResponse.
     */
    private String buildImageObservationPrefixHtml(
            String geminiImageDescription, AiPredictionItem finalPrediction, PreparedDisease resolvedDisease) {
        StringBuilder builder = new StringBuilder();

        if (trimToNull(geminiImageDescription) != null) {
            builder.append("<p>").append(escapeHtml(geminiImageDescription).replace("\n", "<br>")).append("</p>");
        }

        String citedName = resolvedDisease != null
                ? resolvedDisease.entity().getNameVi()
                : (finalPrediction != null ? finalPrediction.getVietnameseName() : null);
        if (trimToNull(citedName) != null) {
            builder.append("<p>Dựa trên mô hình nhận diện, mình nghi ngờ đây là <strong>")
                    .append(escapeHtml(citedName))
                    .append("</strong>.</p>");
        }

        return builder.toString();
    }

    /**
     * Cau mo dau + ten benh (+ ten EN) + anh minh hoa + signsSummary — phan "nhan dien" dung chung
     * cho ca buildDiseaseAnswerHtml (phac do day du, chi mo khi da co anh) va
     * buildRequestImageAnswerHtml (chat chu, chi xac nhan nghi van + moi gui anh).
     */
    private String buildDiseaseIdentityHtml(PreparedDisease disease) {
        StringBuilder builder = new StringBuilder();
        // "Dựa trên dấu hiệu" chứ không cố định "theo ảnh": hàm này dùng chung cho cả trả lời
        // chat gõ chữ (thường không kèm ảnh) lẫn ngữ cảnh đã có ảnh chẩn đoán trước đó.
        builder.append("<p>Dựa trên các dấu hiệu bạn mô tả, tôi nghi ngờ tôm bạn mắc bệnh:</p>");
        builder.append("<div><strong>")
                .append(escapeHtml(disease.entity().getNameVi()))
                .append("</strong>");

        if (trimToNull(disease.entity().getNameEn()) != null) {
            builder.append(" <em>(").append(escapeHtml(disease.entity().getNameEn())).append(")</em>");
        }
        builder.append("</div>");

        List<String> imageUrls = readImageUrls(disease.entity().getImageUrlsJson());
        if (!imageUrls.isEmpty()) {
            builder.append("<div style=\"display:flex;gap:8px;flex-wrap:wrap;margin:8px 0;\">");
            for (String imageUrl : imageUrls) {
                builder.append("<img src=\"").append(escapeHtml(imageUrl))
                        .append("\" alt=\"Hình ảnh minh họa ")
                        .append(escapeHtml(disease.entity().getNameVi()))
                        .append("\" style=\"max-width:160px;max-height:160px;border-radius:6px;object-fit:cover;\" />");
            }
            builder.append("</div>");
        }

        if (trimToNull(disease.entity().getSignsSummary()) != null) {
            builder.append("<p>").append(escapeHtml(disease.entity().getSignsSummary())).append("</p>");
        }

        return builder.toString();
    }

    /**
     * Dung cho chat chu: xac nhan ten benh nghi ngo + dau hieu de nong dan tu doi chieu, nhung
     * KHONG kem nguyen nhan/phac do/lien he ky su — nhung noi dung do chi mo khi da co anh xac
     * nhan qua enrichDiagnosis (dung YOLO), khong bao gio phat sinh chi tu mo ta chu.
     */
    private String buildRequestImageAnswerHtml(PreparedDisease disease) {
        return buildDiseaseIdentityHtml(disease)
                + "<p>Để đưa ra phác đồ điều trị chính xác, tôi cần xem thêm ảnh thực tế của tôm. "
                + "Bạn vui lòng gửi kèm 1 tấm ảnh rõ nét (thân, mang, đường ruột...) để tôi chẩn đoán "
                + "và tư vấn cách điều trị phù hợp nhé.</p>";
    }

    private String buildDiseaseAnswerHtml(PreparedDisease disease) {
        List<String> causes = defaultList(readJsonList(disease.entity().getCausesJson(), new TypeReference<List<String>>() {
        }));
        List<TreatmentStageResponse> treatmentStages = toTreatmentStageResponses(disease.entity().getTreatmentStagesJson());
        StringBuilder builder = new StringBuilder(buildDiseaseIdentityHtml(disease));

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
                    builder.append(buildTreatmentInstructionsHtml(stage.getInstructions()));
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

        String engineerName = trimToNull(disease.entity().getEngineerName());
        String engineerPhone = trimToNull(disease.entity().getEngineerPhone());
        if (engineerName != null || engineerPhone != null) {
            builder.append("<p><em>Nguồn: ")
                    .append(escapeHtml(engineerName != null ? engineerName : "Đội ngũ kỹ sư AgriShrimp"))
                    .append("</em></p>");
            if (engineerPhone != null) {
                builder.append("<p>Tham khảo phác đồ trên để biết chi tiết. Trường hợp khẩn cấp, vui lòng liên hệ: <strong>")
                        .append(escapeHtml(engineerPhone))
                        .append("</strong></p>");
            }
        }

        return builder.toString();
    }

    private String buildTreatmentInstructionsHtml(List<String> instructions) {
        StringBuilder html = new StringBuilder();
        List<String> plainInstructions = new ArrayList<>();

        for (String instruction : defaultList(instructions)) {
            String trimmed = trimToNull(instruction);
            if (trimmed == null) {
                continue;
            }

            if (AiTextFormatUtils.looksLikeHtml(trimmed)) {
                appendPlainTreatmentInstructions(html, plainInstructions);
                String safeHtml = trimToNull(AiTextFormatUtils.sanitizeRichHtml(trimmed));
                if (safeHtml != null) {
                    html.append("<div>").append(safeHtml).append("</div>");
                }
            } else {
                plainInstructions.add(trimmed);
            }
        }

        appendPlainTreatmentInstructions(html, plainInstructions);
        return html.toString();
    }

    private void appendPlainTreatmentInstructions(StringBuilder html, List<String> plainInstructions) {
        if (plainInstructions.isEmpty()) {
            return;
        }

        html.append("<ul>");
        for (String instruction : plainInstructions) {
            html.append("<li>").append(escapeHtml(instruction)).append("</li>");
        }
        html.append("</ul>");
        plainInstructions.clear();
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

    /**
     * Snapshot rieng cho trang "Chat thu nghiem" — giong het snapshot that (chi APPROVED+enabled),
     * nhung neu previewDiseaseCode duoc chi dinh va chua nam trong do (dang DRAFT/IN_REVIEW/DISABLED)
     * thi chen them dung 1 ban ghi do vao truoc khi tra ve. KHONG bao gio duoc goi tu duong chat/chan
     * doan that cua khach hang — chi dung o overload danh rieng cho test.
     */
    private ApprovedKnowledgeSnapshot getSnapshotForPreview(String previewDiseaseCode) {
        ApprovedKnowledgeSnapshot approved = getApprovedSnapshot();
        if (previewDiseaseCode == null || previewDiseaseCode.isBlank()) return approved;

        boolean alreadyApproved = approved.diseaseEntries().stream()
                .anyMatch(d -> d.entity().getCode().equalsIgnoreCase(previewDiseaseCode));
        if (alreadyApproved) return approved;

        AiDiseaseKnowledge entity = diseaseKnowledgeRepository.findByCode(previewDiseaseCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> notFound("Không tìm thấy phác đồ: " + previewDiseaseCode));
        PreparedDisease preview = new PreparedDisease(entity, entity.getCategory(),
                prepareKeywords(entity.getNameVi(), entity.getAliasesRaw(), entity.getSymptomKeywordsRaw()));
        List<PreparedDisease> merged = new ArrayList<>(approved.diseaseEntries());
        merged.add(preview);
        return new ApprovedKnowledgeSnapshot(approved.keywordEntries(), merged);
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
                .imageUrls(readImageUrls(entity.getImageUrlsJson()))
                .engineerName(entity.getEngineerName())
                .engineerPhone(entity.getEngineerPhone())
                .confidenceThreshold(entity.getConfidenceThreshold())
                .matchThreshold(entity.getMatchThreshold())
                .enabled(entity.getEnabled())
                .priority(entity.getPriority())
                .canonical(entity.getCanonical())
                .status(entity.getStatus())
                .reviewNote(entity.getReviewNote())
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
                .fallbackContactName(config.getFallbackContactName())
                .fallbackContactPhone(config.getFallbackContactPhone())
                .build();
    }

    private List<TreatmentStageResponse> toTreatmentStageResponses(String treatmentStagesJson) {
        return defaultList(readJsonList(treatmentStagesJson, new TypeReference<List<KnowledgeStage>>() {
        })).stream()
                .map(this::toTreatmentStageResponse)
                .toList();
    }

    private List<AiKnowledgeTreatmentStageResponse> toKnowledgeTreatmentStageResponses(String treatmentStagesJson) {
        return defaultList(readJsonList(treatmentStagesJson, new TypeReference<List<KnowledgeStage>>() {
        })).stream()
                .map(this::toKnowledgeTreatmentStageResponse)
                .toList();
    }

    private List<AiKnowledgeTreatmentStageResponse> toTreatmentStageResponses(List<AiKnowledgeTreatmentStageRequest> stages) {
        return defaultList(stages).stream()
                .map(this::toKnowledgeTreatmentStageResponse)
                .toList();
    }

    private List<KnowledgeStage> toKnowledgeStages(List<AiKnowledgeTreatmentStageRequest> stages) {
        return defaultList(stages).stream()
                .map(stage -> KnowledgeStage.builder()
                        .stageTitle(trimToNull(stage.getStageTitle()))
                        .stageSigns(trimToNull(stage.getStageSigns()))
                        .treatmentGoal(trimToNull(stage.getTreatmentGoal()))
                        .instructions(sanitizeStageInstructions(stage.getInstructions()))
                        .productIds(defaultList(stage.getProductIds()))
                        .extraProductNames(sanitizeExtraProductNames(stage.getExtraProductNames()))
                        .subStages(toKnowledgeSubStages(stage))
                        .build())
                .toList();
    }

    private TreatmentStageResponse toTreatmentStageResponse(KnowledgeStage stage) {
        return TreatmentStageResponse.builder()
                .stageTitle(stage.getStageTitle())
                .stageSigns(trimToNull(stage.getStageSigns()))
                .treatmentGoal(trimToNull(stage.getTreatmentGoal()))
                .instructions(sanitizeStageInstructions(stage.getInstructions()))
                .products(resolveSuggestedProducts(stage.getProductIds()))
                .extraProductNames(defaultList(stage.getExtraProductNames()))
                .subStages(toTreatmentSubStageResponses(stage))
                .build();
    }

    private DiseaseResponse toDiseaseResponse(PreparedDisease disease, Double confidencePercent) {
        if (disease == null) {
            return null;
        }
        List<String> imageUrls = readImageUrls(disease.entity().getImageUrlsJson());
        return DiseaseResponse.builder()
                .code(disease.entity().getCode())
                .nameVi(disease.entity().getNameVi())
                .nameEn(disease.entity().getNameEn())
                .confidencePercent(confidencePercent)
                .imageUrls(imageUrls.isEmpty() ? null : imageUrls)
                .build();
    }

    private AiKnowledgeTreatmentStageResponse toKnowledgeTreatmentStageResponse(KnowledgeStage stage) {
        return AiKnowledgeTreatmentStageResponse.builder()
                .stageTitle(stage.getStageTitle())
                .stageSigns(trimToNull(stage.getStageSigns()))
                .treatmentGoal(trimToNull(stage.getTreatmentGoal()))
                .instructions(sanitizeStageInstructions(stage.getInstructions()))
                .productIds(defaultList(stage.getProductIds()))
                .products(resolveSuggestedProducts(stage.getProductIds()))
                .extraProductNames(defaultList(stage.getExtraProductNames()))
                .subStages(toKnowledgeSubStageResponses(stage))
                .build();
    }

    private AiKnowledgeTreatmentStageResponse toKnowledgeTreatmentStageResponse(AiKnowledgeTreatmentStageRequest stage) {
        List<AiKnowledgeTreatmentSubStageResponse> subStages = defaultList(stage.getSubStages()).stream()
                .map(this::toKnowledgeSubStageResponse)
                .toList();
        if (subStages.isEmpty() && hasLegacyStagePayload(stage.getInstructions(), stage.getProductIds(), stage.getExtraProductNames())) {
            subStages = List.of(AiKnowledgeTreatmentSubStageResponse.builder()
                    .subStageTitle(stage.getStageTitle())
                    .instructions(sanitizeStageInstructions(stage.getInstructions()))
                    .productIds(defaultList(stage.getProductIds()))
                    .products(resolveSuggestedProducts(stage.getProductIds()))
                    .extraProductNames(defaultList(stage.getExtraProductNames()))
                    .build());
        }

        return AiKnowledgeTreatmentStageResponse.builder()
                .stageTitle(stage.getStageTitle())
                .stageSigns(trimToNull(stage.getStageSigns()))
                .treatmentGoal(trimToNull(stage.getTreatmentGoal()))
                .instructions(sanitizeStageInstructions(stage.getInstructions()))
                .productIds(defaultList(stage.getProductIds()))
                .products(resolveSuggestedProducts(stage.getProductIds()))
                .extraProductNames(defaultList(stage.getExtraProductNames()))
                .subStages(subStages)
                .build();
    }

    private List<TreatmentStageResponse> toTreatmentSubStageResponses(KnowledgeStage stage) {
        List<TreatmentStageResponse> subStages = defaultList(stage.getSubStages()).stream()
                .map(subStage -> TreatmentStageResponse.builder()
                        .stageTitle(subStage.getSubStageTitle())
                        .instructions(sanitizeStageInstructions(subStage.getInstructions()))
                        .products(resolveSuggestedProducts(subStage.getProductIds()))
                        .extraProductNames(defaultList(subStage.getExtraProductNames()))
                        .build())
                .toList();
        if (!subStages.isEmpty()) {
            return subStages;
        }
        if (!hasLegacyStagePayload(stage.getInstructions(), stage.getProductIds(), stage.getExtraProductNames())) {
            return Collections.emptyList();
        }
        return List.of(TreatmentStageResponse.builder()
                .stageTitle(stage.getStageTitle())
                .instructions(sanitizeStageInstructions(stage.getInstructions()))
                .products(resolveSuggestedProducts(stage.getProductIds()))
                .extraProductNames(defaultList(stage.getExtraProductNames()))
                .build());
    }

    private List<AiKnowledgeTreatmentSubStageResponse> toKnowledgeSubStageResponses(KnowledgeStage stage) {
        List<AiKnowledgeTreatmentSubStageResponse> subStages = defaultList(stage.getSubStages()).stream()
                .map(subStage -> AiKnowledgeTreatmentSubStageResponse.builder()
                        .subStageTitle(subStage.getSubStageTitle())
                        .instructions(sanitizeStageInstructions(subStage.getInstructions()))
                        .productIds(defaultList(subStage.getProductIds()))
                        .products(resolveSuggestedProducts(subStage.getProductIds()))
                        .extraProductNames(defaultList(subStage.getExtraProductNames()))
                        .build())
                .toList();
        if (!subStages.isEmpty()) {
            return subStages;
        }
        if (!hasLegacyStagePayload(stage.getInstructions(), stage.getProductIds(), stage.getExtraProductNames())) {
            return Collections.emptyList();
        }
        return List.of(AiKnowledgeTreatmentSubStageResponse.builder()
                .subStageTitle(stage.getStageTitle())
                .instructions(sanitizeStageInstructions(stage.getInstructions()))
                .productIds(defaultList(stage.getProductIds()))
                .products(resolveSuggestedProducts(stage.getProductIds()))
                .extraProductNames(defaultList(stage.getExtraProductNames()))
                .build());
    }

    private AiKnowledgeTreatmentSubStageResponse toKnowledgeSubStageResponse(AiKnowledgeTreatmentSubStageRequest subStage) {
        return AiKnowledgeTreatmentSubStageResponse.builder()
                .subStageTitle(subStage.getSubStageTitle())
                .instructions(sanitizeStageInstructions(subStage.getInstructions()))
                .productIds(defaultList(subStage.getProductIds()))
                .products(resolveSuggestedProducts(subStage.getProductIds()))
                .extraProductNames(defaultList(subStage.getExtraProductNames()))
                .build();
    }

    private List<KnowledgeSubStage> toKnowledgeSubStages(AiKnowledgeTreatmentStageRequest stage) {
        List<KnowledgeSubStage> subStages = defaultList(stage.getSubStages()).stream()
                .map(subStage -> KnowledgeSubStage.builder()
                        .subStageTitle(trimToNull(subStage.getSubStageTitle()))
                        .instructions(sanitizeStageInstructions(subStage.getInstructions()))
                        .productIds(defaultList(subStage.getProductIds()))
                        .extraProductNames(sanitizeExtraProductNames(subStage.getExtraProductNames()))
                        .build())
                .toList();
        if (!subStages.isEmpty()) {
            return subStages;
        }
        if (!hasLegacyStagePayload(stage.getInstructions(), stage.getProductIds(), stage.getExtraProductNames())) {
            return Collections.emptyList();
        }
        return List.of(KnowledgeSubStage.builder()
                .subStageTitle(trimToNull(stage.getStageTitle()))
                .instructions(sanitizeStageInstructions(stage.getInstructions()))
                .productIds(defaultList(stage.getProductIds()))
                .extraProductNames(sanitizeExtraProductNames(stage.getExtraProductNames()))
                .build());
    }

    private boolean hasLegacyStagePayload(List<String> instructions, List<Long> productIds, List<String> extraProductNames) {
        return !sanitizeStageInstructions(instructions).isEmpty()
                || !defaultList(productIds).isEmpty()
                || !sanitizeExtraProductNames(extraProductNames).isEmpty();
    }

    private List<String> sanitizeExtraProductNames(List<String> extraProductNames) {
        return defaultList(extraProductNames).stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> sanitizeStageInstructions(List<String> instructions) {
        return defaultList(instructions).stream()
                .map(this::sanitizeStageInstruction)
                .filter(Objects::nonNull)
                .toList();
    }

    private String sanitizeStageInstruction(String instruction) {
        String trimmed = trimToNull(instruction);
        if (trimmed == null) {
            return null;
        }
        if (AiTextFormatUtils.looksLikeHtml(trimmed)) {
            return trimToNull(AiTextFormatUtils.sanitizeRichHtml(trimmed));
        }
        return trimmed;
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

    private List<TreatmentStageResponse> numberedSubStages(List<TreatmentStageResponse> subStages, int stageIndex) {
        return java.util.stream.IntStream.range(0, defaultList(subStages).size())
                .mapToObj(subStageIndex -> numberedSubStage(defaultList(subStages).get(subStageIndex), stageIndex, subStageIndex))
                .toList();
    }

    private TreatmentStageResponse numberedSubStage(TreatmentStageResponse subStage, int stageIndex, int subStageIndex) {
        String number = (stageIndex + 1) + "." + (subStageIndex + 1);
        String title = subStage.getStageTitle();
        String numberedTitle = title != null && title.trim().matches("^\\d+(\\.\\d+)?\\s*[-–—].*")
                ? title.trim()
                : number + " — " + (title != null && !title.isBlank() ? title.trim() : "Giai đoạn " + number);
        return TreatmentStageResponse.builder()
                .stageTitle(numberedTitle)
                .stageSigns(subStage.getStageSigns())
                .treatmentGoal(subStage.getTreatmentGoal())
                .instructions(subStage.getInstructions())
                .products(subStage.getProducts())
                .extraProductNames(subStage.getExtraProductNames())
                .subStages(subStage.getSubStages())
                .build();
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

    private List<String> readImageUrls(String imageUrlsJson) {
        return defaultList(readJsonList(imageUrlsJson, new TypeReference<List<String>>() {
        }));
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
            String answerHtml,
            List<PreparedDisease> clarifyCandidates) {
    }

    private record CandidateDiseaseScore(
            PreparedDisease disease,
            double score) {
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
        private String stageSigns;
        private String treatmentGoal;
        private List<String> instructions;
        private List<Long> productIds;
        private List<String> extraProductNames;
        private List<KnowledgeSubStage> subStages;

        public String getStageTitle() {
            return stageTitle;
        }

        public String getStageSigns() {
            return stageSigns;
        }

        public String getTreatmentGoal() {
            return treatmentGoal;
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

        public List<KnowledgeSubStage> getSubStages() {
            return subStages;
        }
    }

    @Builder
    private static class KnowledgeSubStage {
        private String subStageTitle;
        private List<String> instructions;
        private List<Long> productIds;
        private List<String> extraProductNames;

        public String getSubStageTitle() {
            return subStageTitle;
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
