package com.zone.agri.service;

import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.ai.ShrimpPriceBlogDraftSuggestion;
import com.zone.agri.entity.BlogCategory;
import com.zone.agri.entity.BlogPost;
import com.zone.agri.entity.BlogTag;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.BlogCategoryStatus;
import com.zone.agri.entity.enums.BlogPostStatus;
import com.zone.agri.repository.BlogCategoryRepository;
import com.zone.agri.repository.BlogPostRepository;
import com.zone.agri.repository.BlogTagRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShrimpPriceBlogAutomationService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", VI_LOCALE);
    private static final DateTimeFormatter SLUG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_SOURCE_URL = "https://tepbac.com/gia-thuy-san/gia/tom";
    private static final String CATEGORY_NAME = "Giá tôm hôm nay";
    private static final String CATEGORY_SLUG = "gia-tom-hom-nay";
    private static final String DEFAULT_THUMBNAIL_URL =
            "https://res.cloudinary.com/demevvyp4/image/upload/v1783706092/logo_arishrimp.jpg";
    private static final Pattern SOURCE_DATE_PATTERN = Pattern.compile("Cập nhật ngày\\s*(\\d{1,2}/\\d{1,2}/\\d{4})");
    private static final Pattern PRICE_DIGIT_PATTERN = Pattern.compile("\\d+");

    private final BlogPostRepository blogPostRepository;
    private final BlogCategoryRepository blogCategoryRepository;
    private final BlogTagRepository blogTagRepository;
    private final UserRepository userRepository;
    private final GeminiClarifyClient geminiClarifyClient;
    private final EmailService emailService;

    @Value("${app.shrimp-price-blog.enabled:true}")
    private boolean enabled;

    @Value("${app.shrimp-price-blog.source-url:" + DEFAULT_SOURCE_URL + "}")
    private String sourceUrl;

    @Value("${app.shrimp-price-blog.review-emails:}")
    private String configuredReviewEmails;

    @Value("${app.shrimp-price-blog.author-email:}")
    private String configuredAuthorEmail;

    @Value("${app.shrimp-price-blog.thumbnail-url:" + DEFAULT_THUMBNAIL_URL + "}")
    private String configuredThumbnailUrl;

    @Value("${app.web-base-url:https://agrishrimp.io.vn}")
    private String webBaseUrl;

    @Scheduled(cron = "${app.shrimp-price-blog.cron:0 10 6 * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void createDailyShrimpPriceBlogDraftOnSchedule() {
        if (!enabled) {
            return;
        }

        try {
            BlogPost post = createDailyShrimpPriceBlogDraft(LocalDate.now(VIETNAM_ZONE));
            log.info("Daily shrimp price blog draft ready: id={}, slug={}, status={}",
                    post.getId(), post.getSlug(), post.getStatus());
        } catch (Exception ex) {
            log.error("Failed to create daily shrimp price blog draft", ex);
        }
    }

    @Transactional
    public BlogPost createDailyShrimpPriceBlogDraftNow() {
        return createDailyShrimpPriceBlogDraft(LocalDate.now(VIETNAM_ZONE));
    }

    @Transactional
    public BlogPost createDailyShrimpPriceBlogDraft(LocalDate reportDate) {
        LocalDate safeReportDate = reportDate != null ? reportDate : LocalDate.now(VIETNAM_ZONE);
        ScrapedShrimpPriceData priceData = fetchShrimpPriceData();

        if (priceData.rows().isEmpty()) {
            throw new IllegalStateException("Không tìm thấy dòng giá tôm thương phẩm từ nguồn dữ liệu");
        }

        String displayDate = priceData.sourceDateLabel() != null && !priceData.sourceDateLabel().isBlank()
                ? priceData.sourceDateLabel()
                : DISPLAY_DATE_FORMAT.format(safeReportDate);
        String priceRangeLabel = buildPriceRangeLabel(priceData.rows());
        ShrimpPriceBlogDraftSuggestion suggestion = suggestDraft(displayDate, priceData.sourceDateLabel(), priceRangeLabel, priceData.rows());
        BlogPost post = upsertDraftPost(safeReportDate, displayDate, priceData, suggestion, priceRangeLabel);

        if (post.getStatus() != BlogPostStatus.PUBLISHED) {
            notifyReviewers(post, priceData, priceRangeLabel);
        }
        return post;
    }

    private ScrapedShrimpPriceData fetchShrimpPriceData() {
        try {
            Document doc = Jsoup.connect(sourceUrl)
                    .userAgent("AgriShrimpBot/1.0 (+https://agrishrimp.io.vn)")
                    .timeout(15000)
                    .get();

            String sourceDateLabel = extractSourceDateLabel(doc);
            List<ShrimpPriceRow> parsedRows = parsePriceRows(doc);
            List<ShrimpPriceRow> rows = parsedRows.stream()
                    .filter(this::isCommercialShrimpPrice)
                    .toList();

            log.info("Shrimp price source parsed: sourceDateLabel={}, parsedRows={}, filteredRows={}",
                    sourceDateLabel, parsedRows.size(), rows.size());
            if (rows.isEmpty()) {
                log.warn("Shrimp price source produced no filtered rows: title={}, tables={}",
                        doc.title(), doc.select("table").size());
            }

            return new ScrapedShrimpPriceData(sourceDateLabel, rows);
        } catch (IOException ex) {
            throw new IllegalStateException("Không đọc được dữ liệu giá tôm tự động", ex);
        }
    }

    private List<ShrimpPriceRow> parsePriceRows(Document doc) {
        List<ShrimpPriceRow> rows = new ArrayList<>();
        String currentGroup = "";

        for (Element element : doc.select("h1,h2,h3,table")) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if (tag.startsWith("h")) {
                String heading = element.text().trim();
                if (normalizeVietnamese(heading).contains("tom")) {
                    currentGroup = heading;
                }
                continue;
            }

            if (!"table".equals(tag)) {
                continue;
            }

            String groupForRows = currentGroup.isBlank() ? "Tôm" : currentGroup;
            for (Element row : element.select("tr")) {
                Elements cells = row.select("td");
                if (cells.size() < 2) {
                    continue;
                }

                String rawName = cells.get(0).text().trim();
                String sizeText = resolveSizeText(cells);
                String priceText = resolvePriceText(cells);
                String changeText = resolveChangeText(cells);
                String updatedText = resolveUpdatedText(cells);
                Long priceValue = parsePriceValue(priceText);
                String itemName = stripLeadingCode(rawName);

                if (!rawName.isBlank() && !priceText.isBlank()) {
                    rows.add(new ShrimpPriceRow(
                            normalizeGroupLabel(groupForRows, itemName),
                            extractCode(rawName),
                            rawName,
                            itemName,
                            sizeText,
                            priceText,
                            priceValue,
                            changeText,
                            updatedText
                    ));
                }
            }
        }

        return rows;
    }

    private String resolveSizeText(Elements cells) {
        if (cells.size() >= 2 && !looksLikeKgPrice(cells.get(1).text())) {
            return cells.get(1).text().trim();
        }
        return "";
    }

    private String resolvePriceText(Elements cells) {
        if (cells.size() >= 3 && looksLikeKgPrice(cells.get(2).text())) {
            return cells.get(2).text().trim();
        }
        if (cells.size() >= 2 && looksLikeKgPrice(cells.get(1).text())) {
            return cells.get(1).text().trim();
        }
        for (Element cell : cells) {
            String text = cell.text();
            if (looksLikeKgPrice(text)) {
                return text.trim();
            }
        }
        return cells.size() >= 2 ? cells.get(1).text().trim() : "";
    }

    private String resolveChangeText(Elements cells) {
        if (cells.size() >= 4) {
            return cells.get(3).text().trim();
        }
        if (cells.size() >= 3 && !looksLikeKgPrice(cells.get(2).text())) {
            return cells.get(2).text().trim();
        }
        return "";
    }

    private String resolveUpdatedText(Elements cells) {
        return cells.size() >= 5 ? cells.get(4).text().trim() : "";
    }

    private boolean looksLikeKgPrice(String value) {
        String normalized = normalizeVietnamese(value);
        return normalized.contains("/kg") || normalized.contains("d/kg") || normalized.contains("dong/kg");
    }

    private boolean isCommercialShrimpPrice(ShrimpPriceRow row) {
        String normalized = normalizeVietnamese(row.rawName() + " " + row.itemName() + " " + row.groupLabel());
        if (!normalized.contains("tom")) {
            return false;
        }
        if (normalized.contains("post") || normalized.contains("giong")) {
            return false;
        }
        if (row.priceValue() == null || row.priceValue() < 1_000) {
            return false;
        }
        if (normalized.contains("tom hum")) {
            return false;
        }
        return looksLikeKgPrice(row.priceText());
    }

    private ShrimpPriceBlogDraftSuggestion suggestDraft(
            String displayDate,
            String sourceDateLabel,
            String priceRangeLabel,
            List<ShrimpPriceRow> rows) {
        String rowsText = rows.stream()
                .map(row -> "- %s | %s | size %s | %s | %s | cập nhật %s".formatted(
                        row.groupLabel(),
                        row.itemName(),
                        blankToDefault(row.sizeText(), "không ghi rõ"),
                        formatPriceLabel(row),
                        blankToDefault(row.changeText(), "Không ghi nhận"),
                        blankToDefault(row.updatedText(), "Đang cập nhật")))
                .collect(Collectors.joining("\n"));

        try {
            ShrimpPriceBlogDraftSuggestion suggestion = geminiClarifyClient.suggestShrimpPriceBlogDraft(
                    displayDate,
                    blankToDefault(sourceDateLabel, displayDate),
                    priceRangeLabel,
                    rowsText);
            return normalizeSuggestion(suggestion, displayDate, priceRangeLabel, rows);
        } catch (Exception ex) {
            log.warn("Gemini shrimp price blog suggestion failed, using deterministic draft: {}", ex.getMessage());
            return buildFallbackSuggestion(displayDate, priceRangeLabel, rows);
        }
    }

    private BlogPost upsertDraftPost(
            LocalDate reportDate,
            String displayDate,
            ScrapedShrimpPriceData priceData,
            ShrimpPriceBlogDraftSuggestion suggestion,
            String priceRangeLabel) {
        String slug = "gia-tom-hom-nay-" + SLUG_DATE_FORMAT.format(reportDate);
        Optional<BlogPost> existingPost = blogPostRepository.findBySlug(slug);

        if (existingPost.isPresent() && existingPost.get().getStatus() == BlogPostStatus.PUBLISHED) {
            log.info("Shrimp price blog for {} was already published, skip updating slug={}", displayDate, slug);
            return existingPost.get();
        }

        BlogCategory category = resolveCategory();
        Set<BlogTag> tags = resolveTags(List.of("Giá tôm", "Giá tôm hôm nay", "Thị trường tôm"));
        User author = resolveAuthor().orElse(null);
        String content = buildArticleContent(displayDate, priceData, suggestion, priceRangeLabel);

        BlogPost post = existingPost.orElseGet(BlogPost::new);
        post.setTitle(limitLength(suggestion.getTitle(), 255));
        post.setSlug(slug);
        post.setExcerpt(limitLength(suggestion.getExcerpt(), 500));
        post.setContent(content);
        post.setStatus(BlogPostStatus.IN_REVIEW);
        post.setReviewNote(null);
        post.setPublishedAt(null);
        post.setCategory(category);
        post.setTags(tags);
        post.setThumbnailUrl(resolveThumbnailUrl());
        post.setThumbnailPublicId(null);
        if (post.getAuthor() == null) {
            post.setAuthor(author);
        }

        return blogPostRepository.save(post);
    }

    private BlogCategory resolveCategory() {
        return blogCategoryRepository.findBySlugIgnoreCase(CATEGORY_SLUG)
                .orElseGet(() -> blogCategoryRepository.save(BlogCategory.builder()
                        .name(CATEGORY_NAME)
                        .slug(CATEGORY_SLUG)
                        .description("Bảng giá tôm thương phẩm được cập nhật tự động hằng ngày.")
                        .status(BlogCategoryStatus.ACTIVE)
                        .build()));
    }

    private Set<BlogTag> resolveTags(Collection<String> tagNames) {
        Set<BlogTag> tags = new LinkedHashSet<>();
        for (String tagName : tagNames) {
            String normalizedName = normalizeSpaces(tagName);
            if (normalizedName.isBlank()) {
                continue;
            }

            BlogTag tag = blogTagRepository.findByNameIgnoreCase(normalizedName)
                    .or(() -> blogTagRepository.findBySlugIgnoreCase(toSlug(normalizedName)))
                    .orElseGet(() -> blogTagRepository.save(BlogTag.builder()
                            .name(normalizedName)
                            .slug(toSlug(normalizedName))
                            .build()));
            tags.add(tag);
        }
        return tags;
    }

    private Optional<User> resolveAuthor() {
        if (configuredAuthorEmail != null && !configuredAuthorEmail.isBlank()) {
            Optional<User> configuredAuthor = userRepository.findByEmail(configuredAuthorEmail.trim());
            if (configuredAuthor.isPresent()) {
                return configuredAuthor;
            }
        }
        return userRepository.findFirstByRole_SlugOrderByIdAsc("SUPER_ADMIN")
                .or(() -> userRepository.findFirstByRole_SlugOrderByIdAsc("ADMIN"));
    }

    private void notifyReviewers(BlogPost post, ScrapedShrimpPriceData priceData, String priceRangeLabel) {
        List<EmailRecipient> recipients = resolveReviewRecipients();
        if (recipients.isEmpty()) {
            log.warn("Shrimp price blog draft created but no review email recipient was found: postId={}", post.getId());
            return;
        }

        String reviewUrl = joinWebUrl(webBaseUrl, "/admin/blog/posts/" + post.getId() + "/edit");
        for (EmailRecipient recipient : recipients) {
            try {
                emailService.sendShrimpPriceBlogReadyEmail(
                        recipient.email(),
                        recipient.name(),
                        post,
                        reviewUrl,
                        priceData.rows().size(),
                        priceRangeLabel,
                        blankToDefault(priceData.sourceDateLabel(), DISPLAY_DATE_FORMAT.format(LocalDate.now(VIETNAM_ZONE))));
            } catch (Exception ex) {
                log.warn("Failed to send shrimp price blog review email to {}: {}", recipient.email(), ex.getMessage());
            }
        }
    }

    private List<EmailRecipient> resolveReviewRecipients() {
        Map<String, EmailRecipient> recipientsByEmail = new LinkedHashMap<>();

        for (String email : splitEmails(configuredReviewEmails)) {
            recipientsByEmail.put(email.toLowerCase(Locale.ROOT), new EmailRecipient(email, "Admin"));
        }

        List<User> reviewerUsers = userRepository.findActiveUsersByRoleSlugOrPermissionCode("SUPER_ADMIN", "BLOG_APPROVE");
        for (User user : reviewerUsers) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            String email = user.getEmail().trim();
            recipientsByEmail.putIfAbsent(
                    email.toLowerCase(Locale.ROOT),
                    new EmailRecipient(email, blankToDefault(user.getFullName(), "Admin")));
        }

        return List.copyOf(recipientsByEmail.values());
    }

    private List<String> splitEmails(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return Pattern.compile("[,;\\s]+")
                .splitAsStream(rawValue)
                .map(String::trim)
                .filter(value -> !value.isBlank() && value.contains("@"))
                .distinct()
                .toList();
    }

    private String buildArticleContent(
            String displayDate,
            ScrapedShrimpPriceData priceData,
            ShrimpPriceBlogDraftSuggestion suggestion,
            String priceRangeLabel) {
        List<ShrimpPriceRow> rows = priceData.rows();
        Map<String, List<ShrimpPriceRow>> groupedRows = rows.stream()
                .collect(Collectors.groupingBy(
                        ShrimpPriceRow::groupLabel,
                        LinkedHashMap::new,
                        Collectors.toList()));

        StringBuilder html = new StringBuilder();
        html.append("<h2>Giá tôm hôm nay ").append(escapeHtml(displayDate)).append("</h2>\n");
        html.append("<p>").append(escapeHtml(suggestion.getMarketSummary())).append("</p>\n");
        html.append("<p>Bài viết tổng hợp các dòng giá tôm thương phẩm theo kg, đã loại bỏ tôm giống/Post và các dòng ngoài nhóm tôm thương phẩm.</p>\n");

        html.append("<h2>Bảng giá tôm hôm nay</h2>\n");

        for (Map.Entry<String, List<ShrimpPriceRow>> entry : groupedRows.entrySet()) {
            html.append("<h3>").append(escapeHtml(entry.getKey()))
                    .append(" (").append(entry.getValue().size()).append(" loại)")
                    .append("</h3>\n");
            html.append("<table><thead><tr>")
                    .append("<th>Mã / Loài</th>")
                    .append("<th>Size</th>")
                    .append("<th>Giá</th>")
                    .append("<th>Thay đổi</th>")
                    .append("<th>Cập nhật</th>")
                    .append("</tr></thead><tbody>\n");
            for (ShrimpPriceRow row : entry.getValue()) {
                html.append("<tr>")
                        .append("<td>")
                        .append(renderCodeAndName(row))
                        .append("</td>")
                        .append("<td>").append(escapeHtml(blankToDefault(row.sizeText(), "—"))).append("</td>")
                        .append("<td><strong>").append(escapeHtml(formatPriceLabel(row))).append("</strong></td>")
                        .append("<td>").append(escapeHtml(blankToDefault(row.changeText(), "Đang cập nhật"))).append("</td>")
                        .append("<td>").append(escapeHtml(blankToDefault(row.updatedText(), "Đang cập nhật"))).append("</td>")
                        .append("</tr>\n");
            }
            html.append("</tbody></table>\n");
        }

        html.append("<h2>Nhận định nhanh</h2>\n");
        html.append("<ul>");
        html.append("<li>Biên độ giá ghi nhận hôm nay: <strong>").append(escapeHtml(priceRangeLabel)).append("</strong>.</li>");
        html.append("<li>Dữ liệu tập trung vào các dòng tôm thương phẩm theo kg để người đọc dễ so sánh giữa từng nhóm và size.</li>");
        html.append("<li>Admin nên đối chiếu lại nguồn trước khi duyệt nếu thị trường biến động mạnh trong ngày.</li>");
        html.append("</ul>\n");
        html.append("<p>").append(escapeHtml(suggestion.getSeoClosing())).append("</p>\n");
        html.append("<p><em>Giá chỉ mang tính tham khảo và có thể thay đổi theo khu vực, size tôm, chất lượng và thời điểm giao dịch.</em></p>");

        return html.toString();
    }

    private ShrimpPriceBlogDraftSuggestion normalizeSuggestion(
            ShrimpPriceBlogDraftSuggestion suggestion,
            String displayDate,
            String priceRangeLabel,
            List<ShrimpPriceRow> rows) {
        ShrimpPriceBlogDraftSuggestion fallback = buildFallbackSuggestion(displayDate, priceRangeLabel, rows);
        if (suggestion == null) {
            return fallback;
        }

        String title = blankToDefault(suggestion.getTitle(), fallback.getTitle());
        if (!normalizeVietnamese(title).contains("gia tom hom nay")) {
            title = fallback.getTitle();
        }

        return ShrimpPriceBlogDraftSuggestion.builder()
                .title(title)
                .excerpt(blankToDefault(suggestion.getExcerpt(), fallback.getExcerpt()))
                .marketSummary(blankToDefault(suggestion.getMarketSummary(), fallback.getMarketSummary()))
                .seoClosing(blankToDefault(suggestion.getSeoClosing(), fallback.getSeoClosing()))
                .build();
    }

    private ShrimpPriceBlogDraftSuggestion buildFallbackSuggestion(
            String displayDate,
            String priceRangeLabel,
            List<ShrimpPriceRow> rows) {
        String prominentGroups = rows.stream()
                .map(ShrimpPriceRow::groupLabel)
                .distinct()
                .limit(3)
                .collect(Collectors.joining(", "));
        if (prominentGroups.isBlank()) {
            prominentGroups = "tôm thẻ, tôm sú";
        }

        return ShrimpPriceBlogDraftSuggestion.builder()
                .title("Giá tôm hôm nay " + displayDate + ": cập nhật tôm sú, tôm thẻ và tôm khác")
                .excerpt("Cập nhật giá tôm hôm nay " + displayDate
                        + " với bảng giá tôm thương phẩm theo kg, " + priceRangeLabel + ".")
                .marketSummary("Giá tôm hôm nay " + displayDate + " ghi nhận " + priceRangeLabel
                        + ". Các nhóm được tổng hợp gồm " + prominentGroups
                        + ", tập trung vào giá thương phẩm để admin dễ kiểm tra và duyệt bài.")
                .seoClosing("AgriShrimp sẽ tiếp tục cập nhật giá tôm hằng ngày để người nuôi theo dõi biến động thị trường kịp thời.")
                .build();
    }

    private String extractSourceDateLabel(Document doc) {
        Matcher matcher = SOURCE_DATE_PATTERN.matcher(doc.text());
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeGroupLabel(String rawGroup, String itemName) {
        String normalized = normalizeVietnamese(rawGroup + " " + itemName);
        if (normalized.contains("tom the")) {
            return "Tôm thẻ";
        }
        if (normalized.contains("tom su")) {
            return "Tôm sú";
        }
        return "Tôm khác";
    }

    private String renderCodeAndName(ShrimpPriceRow row) {
        String code = row.code();
        String itemName = blankToDefault(row.itemName(), row.rawName());
        if (code == null || code.isBlank()) {
            return escapeHtml(itemName);
        }
        return "<strong>" + escapeHtml(code) + "</strong><br>" + escapeHtml(itemName);
    }

    private String extractCode(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern.compile("^[^A-Za-z0-9]*([A-Z0-9]{2,})\\b").matcher(rawName.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    private Long parsePriceValue(String priceText) {
        if (priceText == null || priceText.isBlank()) {
            return null;
        }

        String digits = PRICE_DIGIT_PATTERN.matcher(priceText).results()
                .map(java.util.regex.MatchResult::group)
                .collect(Collectors.joining());
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildPriceRangeLabel(List<ShrimpPriceRow> rows) {
        List<Long> prices = rows.stream()
                .map(ShrimpPriceRow::priceValue)
                .filter(value -> value != null && value > 0)
                .sorted()
                .toList();
        if (prices.isEmpty()) {
            return "đang cập nhật";
        }
        return "dao động từ " + formatVnd(prices.getFirst()) + " đến " + formatVnd(prices.getLast()) + " đồng/kg";
    }

    private String formatPriceLabel(ShrimpPriceRow row) {
        if (row.priceValue() == null) {
            return blankToDefault(row.priceText(), "Đang cập nhật");
        }
        return formatVnd(row.priceValue()) + " đồng/kg";
    }

    private String formatVnd(long value) {
        return NumberFormat.getNumberInstance(VI_LOCALE).format(value);
    }

    private String stripLeadingCode(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().replaceFirst("^[^A-Za-z0-9]*[A-Z0-9]{2,}\\s+", "").trim();
    }

    private String resolveThumbnailUrl() {
        String thumbnail = configuredThumbnailUrl == null ? "" : configuredThumbnailUrl.trim();
        return thumbnail.isBlank() ? DEFAULT_THUMBNAIL_URL : thumbnail;
    }

    private String joinWebUrl(String baseUrl, String path) {
        String base = baseUrl == null || baseUrl.isBlank() ? "https://agrishrimp.io.vn" : baseUrl.trim();
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private String limitLength(String value, int maxLength) {
        String normalized = normalizeSpaces(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeVietnamese(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D");
        return normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String toSlug(String text) {
        String normalized = normalizeVietnamese(text);
        return normalized
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record ScrapedShrimpPriceData(String sourceDateLabel, List<ShrimpPriceRow> rows) {
    }

    private record ShrimpPriceRow(
            String groupLabel,
            String code,
            String rawName,
            String itemName,
            String sizeText,
            String priceText,
            Long priceValue,
            String changeText,
            String updatedText) {
    }

    private record EmailRecipient(String email, String name) {
    }

}
