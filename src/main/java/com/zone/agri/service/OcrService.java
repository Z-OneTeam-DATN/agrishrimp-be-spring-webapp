package com.zone.agri.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.zone.agri.dto.response.employee.OcrCccdResponse;
import com.zone.agri.exception.BadRequestException;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
@Slf4j
public class OcrService {

    private static final Pattern DATE_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2}[\\s./-]\\d{1,2}[\\s./-]\\d{4})(?!\\d)");
    private static final Pattern TWELVE_DIGIT_PATTERN = Pattern.compile("(?<!\\d)(\\d{12})(?!\\d)");
        private static final Set<String> NAME_LABELS = Set.of("HO VA TEN", "HO TEN", "FULL NAME", "TEN");
        private static final Set<String> DOB_LABELS = Set.of("NGAY THANG NAM SINH", "NGAY SINH", "SINH NGAY", "DOB", "DATE OF BIRTH");
        private static final Set<String> GENDER_LABELS = Set.of("GIOI TINH", "SEX", "NAM", "NU");
        private static final Set<String> ADDRESS_LABELS = Set.of("NOI THUONG TRU", "QUE QUAN", "DIA CHI", "NOI O", "NOI O HIEN TAI", "HKTT", "NOI DANG KY HKTT");
    private static final Set<String> STOP_LABELS = Set.of(
            "CAN CUOC", "CONG HOA", "QUOC TICH", "SO", "GIOI TINH", "NGAY SINH",
            "NGAY THANG NAM SINH", "QUE QUAN", "NOI THUONG TRU", "CO GIA TRI", "DEN", "SIGNATURE",
            "NGAY CAP", "NOI CAP", "DAN TOC", "TON GIAO");

    static {
        ImageIO.setUseCache(false);
    }

    @Value("${ocr.tesseract.language:vie}")
    private String ocrLanguage = "vie";

    @Value("${ocr.tesseract.data-path:}")
    private String configuredTessdataPath = "";

    @Value("${ocr.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes = 5L * 1024 * 1024;

    @Value("${ocr.max-image-width:1800}")
    private int maxImageWidth = 1800;

    private volatile String resolvedTessdataPath;

    public OcrCccdResponse extractCccdInfo(MultipartFile image) {
        validateUpload(image);

        BufferedImage original = readImage(image);
        List<BufferedImage> variants = buildVariants(original);
        int[] pageSegModes = { 6, 11, 4 };
        List<OcrAttempt> attempts = new ArrayList<>();

        for (int i = 0; i < variants.size(); i++) {
            for (int pageSegMode : pageSegModes) {
                String extractedText = performOcr(variants.get(i), pageSegMode);
                if (extractedText == null || extractedText.isBlank()) {
                    continue;
                }

                OcrCccdResponse parsed = parseExtractedText(extractedText);
                attempts.add(new OcrAttempt(extractedText, parsed, i * 10 + pageSegMode));
            }
        }

        if (attempts.isEmpty()) {
            throw new BadRequestException("Không trích xuất được văn bản từ ảnh CCCD. Vui lòng dùng ảnh rõ hơn.");
        }

        OcrCccdResponse mergedResult = mergeAttempts(attempts);
        OcrAttempt bestAttempt = attempts.stream()
                .max(Comparator.comparingDouble((OcrAttempt attempt) -> attempt.response().getConfidence() != null
                        ? attempt.response().getConfidence()
                        : 0D).thenComparingInt(attempt -> attempt.rawText().length()))
                .orElseThrow();

        OcrCccdResponse result = mergedResult;
        if (isBlank(result.getCitizenId()) && isBlank(result.getFullName()) && isBlank(result.getDateOfBirth())) {
            throw new BadRequestException(
                    "Không nhận diện được số CCCD, họ tên hoặc ngày sinh. Vui lòng chụp rõ mặt trước CCCD, đủ sáng và không bị lóa.");
        }

        log.info("OCR CCCD thành công với biến thể ảnh {}, confidence={}", bestAttempt.variantIndex(),
                result.getConfidence());
        return result;
    }

    OcrCccdResponse parseExtractedText(String text) {
        if (text == null || text.isBlank()) {
            return emptyResponse(0D);
        }

        log.info("=== RAW OCR TEXT ===\n{}", text);
        
        String normalizedText = normalizeRawText(text);
        List<TextLine> lines = buildLines(normalizedText);

        String citizenId = extractCitizenId(lines, normalizedText);
        LocalDate dateOfBirth = extractDateOfBirth(lines, normalizedText);
        String fullName = extractFullName(lines);
        String gender = extractGender(lines);
        String address = extractAddress(lines);
        
        log.info("=== EXTRACTED FIELDS ===");
        log.info("Citizen ID: {}", citizenId);
        log.info("Full Name: {}", fullName);
        log.info("DOB: {}", dateOfBirth);
        log.info("Gender: {}", gender);
        log.info("Address: {}", address);
        
        double confidence = estimateConfidence(citizenId, fullName, dateOfBirth, gender, address);

        return OcrCccdResponse.builder()
                .fullName(defaultString(fullName))
                .dateOfBirth(dateOfBirth != null ? dateOfBirth.toString() : null)
                .gender(defaultString(gender))
                .address(defaultString(address))
                .citizenId(defaultString(citizenId))
                .confidence(confidence)
                .build();
    }

    private OcrCccdResponse mergeAttempts(List<OcrAttempt> attempts) {
        String citizenId = pickBestValue(attempts, attempt -> attempt.response().getCitizenId(), this::scoreCitizenIdCandidate);
        String fullName = pickBestValue(attempts, attempt -> cleanupName(attempt.response().getFullName()),
                this::scoreNameCandidate);
        String dateOfBirth = pickBestValue(attempts, attempt -> attempt.response().getDateOfBirth(),
                this::scoreDateCandidate);
        String gender = pickBestValue(attempts, attempt -> attempt.response().getGender(), this::scoreGenderCandidate);
        String address = pickBestValue(attempts, attempt -> cleanupAddress(attempt.response().getAddress()),
                this::scoreAddressCandidate);

        double confidence = estimateConfidence(citizenId, fullName,
                !isBlank(dateOfBirth) ? LocalDate.parse(dateOfBirth) : null,
                gender, address);

        return OcrCccdResponse.builder()
                .fullName(defaultString(fullName))
                .dateOfBirth(isBlank(dateOfBirth) ? null : dateOfBirth)
                .gender(defaultString(gender))
                .address(defaultString(address))
                .citizenId(defaultString(citizenId))
                .confidence(confidence)
                .build();
    }

    private String pickBestValue(List<OcrAttempt> attempts, java.util.function.Function<OcrAttempt, String> extractor,
            java.util.function.ToIntFunction<String> qualityScorer) {
        Map<String, CandidateStat> stats = new HashMap<>();

        for (OcrAttempt attempt : attempts) {
            String rawValue = extractor.apply(attempt);
            if (isBlank(rawValue)) {
                continue;
            }

            String value = rawValue.trim();
            CandidateStat stat = stats.computeIfAbsent(value, ignored -> new CandidateStat());
            stat.count++;
            stat.bestQuality = Math.max(stat.bestQuality, qualityScorer.applyAsInt(value));
        }

        return stats.entrySet().stream()
                .max(Comparator
                        .comparingInt((Map.Entry<String, CandidateStat> entry) -> entry.getValue().count)
                        .thenComparingInt(entry -> entry.getValue().bestQuality)
                        .thenComparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private int scoreCitizenIdCandidate(String value) {
        int score = 0;
        if (value.matches("^\\d{12}$")) {
            score += 100;
        }
        if (value.startsWith("0")) {
            score += 5;
        }
        return score;
    }

    private int scoreNameCandidate(String value) {
        String cleaned = cleanupName(value);
        if (cleaned.isBlank()) {
            return 0;
        }

        int words = cleaned.split("\\s+").length;
        int score = Math.min(40, words * 10);
        if (!cleaned.chars().anyMatch(Character::isLowerCase)) {
            score += 10;
        }
        return score;
    }

    private int scoreDateCandidate(String value) {
        return value.matches("^\\d{4}-\\d{2}-\\d{2}$") ? 40 : 0;
    }

    private int scoreGenderCandidate(String value) {
        return "MALE".equals(value) || "FEMALE".equals(value) ? 30 : 0;
    }

    private int scoreAddressCandidate(String value) {
        if (isBlank(value)) {
            return 0;
        }

        int score = Math.min(40, value.length());
        int commaBonus = (int) value.chars().filter(ch -> ch == ',').count() * 5;
        score += commaBonus;
        
        // Penalize addresses with clear noise patterns
        score -= detectNoisePatterns(value);
        
        return Math.max(0, score);
    }
    
    private int detectNoisePatterns(String value) {
        int penalty = 0;
        
        // Double commas = formatting corruption
        if (value.contains(",,")) {
            penalty += 30;
        }
        
        // Single letters followed by other text (like "R tư", "Z7") = corruption  
        if (value.matches(".*\\b[A-Z]\\s+[A-Za-z0-9]{1,3}\\b.*")) {
            penalty += 20;
        }
        
        // Contains English noise words that shouldn't be in Vietnamese addresses
        if (value.matches("(?i).*\\b(va\\s+vn|is|or|by|of)\\b.*")) {
            penalty += 25;
        }
        
        return penalty;
    }

    private void validateUpload(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ảnh CCCD để tải lên.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("File tải lên phải là ảnh hợp lệ.");
        }

        if (image.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("Ảnh CCCD vượt quá giới hạn 5MB.");
        }
    }

    private BufferedImage readImage(MultipartFile image) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
            if (bufferedImage == null) {
                throw new BadRequestException("Không đọc được ảnh CCCD. Vui lòng kiểm tra lại file tải lên.");
            }
            return bufferedImage;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Không thể đọc ảnh CCCD: {}", ex.getMessage());
            throw new BadRequestException("Ảnh CCCD không hợp lệ hoặc đã bị hỏng.");
        }
    }

    private List<BufferedImage> buildVariants(BufferedImage source) {
        BufferedImage resized = resizeForOcr(source);
        BufferedImage grayscale = toGrayscale(resized);
        BufferedImage enhanced = adjustContrast(grayscale, 1.18f, -12f);
        BufferedImage blurred = applyBoxBlur(enhanced);
        BufferedImage thresholded = applyOtsuThreshold(blurred);
        return List.of(grayscale, enhanced, thresholded);
    }

    private BufferedImage resizeForOcr(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();

        int targetWidth = width;
        if (width < 1400) {
            targetWidth = Math.min(maxImageWidth, 1400);
        } else if (width > maxImageWidth) {
            targetWidth = maxImageWidth;
        }

        if (targetWidth == width) {
            return source;
        }

        int targetHeight = Math.max(1, (int) Math.round((double) height * targetWidth / width));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private BufferedImage toGrayscale(BufferedImage source) {
        BufferedImage grayscale = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(source, grayscale);
        return grayscale;
    }

    private BufferedImage adjustContrast(BufferedImage source, float scale, float offset) {
        BufferedImage adjusted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int gray = source.getRaster().getSample(x, y, 0);
                int value = Math.round(gray * scale + offset);
                adjusted.getRaster().setSample(x, y, 0, clamp(value));
            }
        }
        return adjusted;
    }

    private BufferedImage applyBoxBlur(BufferedImage source) {
        BufferedImage blurred = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int sum = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int py = y + dy;
                    if (py < 0 || py >= source.getHeight()) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        int px = x + dx;
                        if (px < 0 || px >= source.getWidth()) {
                            continue;
                        }
                        sum += source.getRaster().getSample(px, py, 0);
                        count++;
                    }
                }
                blurred.getRaster().setSample(x, y, 0, sum / Math.max(count, 1));
            }
        }
        return blurred;
    }

    private BufferedImage applyOtsuThreshold(BufferedImage source) {
        int[] histogram = new int[256];
        int totalPixels = source.getWidth() * source.getHeight();

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                histogram[source.getRaster().getSample(x, y, 0)]++;
            }
        }

        long sum = 0;
        for (int i = 0; i < histogram.length; i++) {
            sum += (long) i * histogram[i];
        }

        long backgroundWeight = 0;
        long backgroundSum = 0;
        double maxVariance = -1;
        int threshold = 0;

        for (int i = 0; i < histogram.length; i++) {
            backgroundWeight += histogram[i];
            if (backgroundWeight == 0) {
                continue;
            }

            long foregroundWeight = totalPixels - backgroundWeight;
            if (foregroundWeight == 0) {
                break;
            }

            backgroundSum += (long) i * histogram[i];
            double backgroundMean = (double) backgroundSum / backgroundWeight;
            double foregroundMean = (double) (sum - backgroundSum) / foregroundWeight;
            double variance = backgroundWeight * (double) foregroundWeight
                    * Math.pow(backgroundMean - foregroundMean, 2);

            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = i;
            }
        }

        BufferedImage binary = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int gray = source.getRaster().getSample(x, y, 0);
                binary.getRaster().setSample(x, y, 0, gray >= threshold ? 1 : 0);
            }
        }
        return binary;
    }

    private String performOcr(BufferedImage image, int pageSegMode) {
        try {
            String tessdataPath = resolveTessdataPath().orElseThrow(() -> new IllegalStateException(
                "Thiếu bộ ngôn ngữ tiếng Việt cho Tesseract. Hãy cài gói tesseract-ocr-vie hoặc cấu hình OCR_TESSERACT_DATA_PATH."));

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage(ocrLanguage);
            tesseract.setPageSegMode(pageSegMode);
            tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
            tesseract.setTessVariable("user_defined_dpi", "300");
            tesseract.setTessVariable("preserve_interword_spaces", "1");
            return tesseract.doOCR(image);
        } catch (TesseractException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "";
            if (message.contains("traineddata") || message.contains("Error opening data file")) {
                throw new IllegalStateException(
                        "Thiếu bộ ngôn ngữ tiếng Việt cho Tesseract. Hãy cài gói tesseract-ocr-vie hoặc cấu hình OCR_TESSERACT_DATA_PATH.",
                        ex);
            }
            log.error("OCR CCCD thất bại", ex);
            throw new BadRequestException("Không thể xử lý ảnh CCCD bằng OCR. Vui lòng thử lại với ảnh rõ hơn.");
        }
    }

    private Optional<String> resolveTessdataPath() {
        if (resolvedTessdataPath != null) {
            return Optional.of(resolvedTessdataPath);
        }

        List<String> candidates = new ArrayList<>();
        if (!isBlank(configuredTessdataPath)) {
            candidates.add(configuredTessdataPath);
        }

        String envPath = System.getenv("TESSDATA_PREFIX");
        if (!isBlank(envPath)) {
            candidates.add(envPath);
        }

        candidates.add("/usr/share/tesseract-ocr/5");
        candidates.add("/usr/share/tesseract-ocr/4.00");
        candidates.add("/usr/share");
        candidates.add("/usr/local/share");
        candidates.add("/opt/homebrew/share");

        for (String candidate : new LinkedHashSet<>(candidates)) {
            Path resolved = normalizeTessdataPath(candidate);
            if (resolved != null) {
                resolvedTessdataPath = resolved.toString();
                log.info("Sử dụng tessdata path: {}", resolvedTessdataPath);
                return Optional.of(resolvedTessdataPath);
            }
        }

        log.warn("Không tìm thấy tessdata path khả dụng cho ngôn ngữ {}", ocrLanguage);
        return Optional.empty();
    }

    private Path normalizeTessdataPath(String candidate) {
        try {
            Path path = Path.of(candidate);
            if (!Files.exists(path)) {
                return null;
            }

            if (isValidTessdataDirectory(path)) {
                return path;
            }

            Path tessdataDir = path.resolve("tessdata");
            if (isValidTessdataDirectory(tessdataDir)) {
                return tessdataDir;
            }

            Path directTrainedData = path.resolve(ocrLanguage + ".traineddata");
            if (Files.exists(directTrainedData)) {
                return path;
            }

            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isValidTessdataDirectory(Path directory) {
        return Files.exists(directory.resolve(ocrLanguage + ".traineddata"));
    }

    private String extractCitizenId(List<TextLine> lines, String fullText) {
        List<String> priorityCandidates = new ArrayList<>();
        for (TextLine line : lines) {
            if (line.normalized().contains("SO")) {
                priorityCandidates.add(line.original());
            }
        }
        priorityCandidates.add(fullText);

        for (String candidate : priorityCandidates) {
            String normalizedDigits = normalizeDigitLikeText(candidate);
            Matcher matcher = TWELVE_DIGIT_PATTERN.matcher(normalizedDigits);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private LocalDate extractDateOfBirth(List<TextLine> lines, String fullText) {
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            if (!containsAny(line.normalized(), DOB_LABELS)) {
                continue;
            }

            String candidate = line.original();
            if (i + 1 < lines.size()) {
                candidate = candidate + " " + lines.get(i + 1).original();
            }

            LocalDate parsed = extractDateFromText(candidate);
            if (parsed != null) {
                return parsed;
            }
        }

        return extractDateFromText(fullText);
    }

    private LocalDate extractDateFromText(String input) {
        String normalizedDigits = normalizeDigitLikeText(input);
        Matcher matcher = DATE_PATTERN.matcher(normalizedDigits);
        while (matcher.find()) {
            String[] parts = matcher.group(1).replace('.', '/').replace('-', '/').replace(" ", "").split("/");
            if (parts.length != 3) {
                continue;
            }

            try {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                return LocalDate.of(year, month, day);
            } catch (NumberFormatException | DateTimeException ignored) {
            }
        }
        return null;
    }

    private String extractFullName(List<TextLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            if (!containsAny(line.normalized(), NAME_LABELS)) {
                continue;
            }

            String inlineCandidate = cleanupName(removeLabelValue(line.original(), NAME_LABELS));
            if (!inlineCandidate.isBlank()) {
                String normalizedCandidate = normalizeForLookup(inlineCandidate);
                if (!containsAny(normalizedCandidate, NAME_LABELS) && !isLabelLikeNameCandidate(normalizedCandidate)) {
                    return inlineCandidate;
                }
            }

            for (int next = i + 1; next < Math.min(lines.size(), i + 4); next++) {
                String candidate = cleanupName(lines.get(next).original());
                String normalizedCandidate = normalizeForLookup(candidate);
                if (isLikelyName(candidate, normalizedCandidate)) {
                    return candidate;
                }
            }
        }

        return lines.stream()
                .map(line -> cleanupName(line.original()))
                .filter(candidate -> isLikelyName(candidate, normalizeForLookup(candidate)))
                .filter(candidate -> !isLabelLikeNameCandidate(normalizeForLookup(candidate)))
                .findFirst()
                .orElse("");
    }

    private String extractGender(List<TextLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            if (!containsAny(line.normalized(), GENDER_LABELS)) {
                continue;
            }

            String candidate = normalizeForLookup(removeLabelValue(line.original(), GENDER_LABELS));
            String mapped = mapGender(candidate);
            if (!mapped.isBlank()) {
                return mapped;
            }

            if (i + 1 < lines.size()) {
                mapped = mapGender(lines.get(i + 1).normalized());
                if (!mapped.isBlank()) {
                    return mapped;
                }
            }
        }

        return "";
    }

    private String extractAddress(List<TextLine> lines) {
        String bestAddress = "";
        int bestScore = -1;

        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            if (!containsAny(line.normalized(), ADDRESS_LABELS)) {
                continue;
            }
            
            log.info("Found address label line: {} (normalized: {})", line.original(), line.normalized());

            List<String> parts = new ArrayList<>();
            String inline = cleanupAddress(removeLabelValue(line.original(), ADDRESS_LABELS));
            log.info("  After label removal + cleanup: {}", inline);
            if (!inline.isBlank()) {
                parts.add(inline);
            }

            for (int next = i + 1; next < Math.min(lines.size(), i + 5); next++) {
                TextLine nextLine = lines.get(next);
                if (containsAny(nextLine.normalized(), STOP_LABELS)) {
                    break;
                }
                String cleaned = cleanupAddress(nextLine.original());
                // Filter out garbage lines: single letters, noisy English words, formatting noise
                if (isGarbageLine(cleaned)) {
                    log.info("  Skipping garbage line: {}", cleaned);
                    continue;
                }
                log.info("  Address continuation: {}", cleaned);
                if (!cleaned.isBlank()) {
                    parts.add(cleaned);
                }
            }

            if (!parts.isEmpty()) {
                String candidate = cleanupAddress(String.join(", ", parts));
                int candidateScore = scoreAddressCandidateByLabel(line.normalized(), candidate);
                log.info("  Candidate: {} (score: {})", candidate, candidateScore);
                if (candidateScore > bestScore) {
                    bestScore = candidateScore;
                    bestAddress = candidate;
                }
            }
        }
        log.info("Best address selected: {} (score: {})", bestAddress, bestScore);
        return bestAddress;
    }

    private int scoreAddressCandidateByLabel(String normalizedLabelLine, String value) {
        int score = scoreAddressCandidate(value);

        if (normalizedLabelLine.contains("NOI THUONG TRU")
                || normalizedLabelLine.contains("NOI O")
                || normalizedLabelLine.contains("HKTT")) {
            score += 80;
        } else if (normalizedLabelLine.contains("QUE QUAN")) {
            score += 20;
        }

        return score;
    }

    private boolean isGarbageLine(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }
        
        // Single letter or very short noise
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length == 1) {
            String token = tokens[0].replaceAll("[,./-]", "");
            // Single letters, numbers only, or things like "va VN", "Z7", "4"
            if (token.length() <= 2) {
                return true;
            }
        }
        
        // Pure English words that aren't location related (va, is, etc.)
        String normalized = line.replaceAll("[^a-zA-Z\\s]", "").trim().toUpperCase();
        if (normalized.matches("^(VA|VN|IS|IT|OR|AND|THE)\\s*$") || 
            normalized.matches("^[A-Z]{1,3}$")) {
            return true;
        }
        
        // Too short to be an address (less than 3 chars of actual Vietnamese)
        String vietnamese = line.replaceAll("[^\\p{L}\\p{N}]", "");
        if (vietnamese.length() < 3) {
            return true;
        }
        
        return false;
    }

    private String mapGender(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return "";
        }
        if (normalizedValue.contains("NU")) {
            return "FEMALE";
        }
        if (normalizedValue.contains("NAM")) {
            return "MALE";
        }
        return "";
    }

    private boolean isLikelyName(String candidate, String normalizedCandidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }

        if (candidate.chars().anyMatch(Character::isDigit)) {
            return false;
        }

        if (containsAny(normalizedCandidate, STOP_LABELS)) {
            return false;
        }

        String[] parts = candidate.trim().split("\\s+");
        return parts.length >= 2 && candidate.length() >= 6;
    }

    private String normalizeRawText(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private List<TextLine> buildLines(String text) {
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> new TextLine(line, normalizeForLookup(line)))
                .toList();
    }

    private String normalizeForLookup(String value) {
        String noDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noDiacritics.toUpperCase(Locale.ROOT)
                .replace('Đ', 'D')
                .replaceAll("[^A-Z0-9:/\\-\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeDigitLikeText(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (char ch : input.toCharArray()) {
            switch (Character.toUpperCase(ch)) {
                case 'O', 'D', 'Q' -> builder.append('0');
                case 'I', 'L', '|', '!' -> builder.append('1');
                case 'Z' -> builder.append('2');
                case 'S' -> builder.append('5');
                case 'G' -> builder.append('6');
                case 'B' -> builder.append('8');
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String removeLabelValue(String raw, Set<String> labels) {
        String normalized = normalizeForLookup(raw);
        boolean containsLabel = labels.stream().anyMatch(normalized::contains);
        if (!containsLabel) {
            return raw.trim();
        }

        int separatorIndex = -1;
        for (char separator : new char[] { ':', '-', '.' }) {
            int currentIndex = raw.indexOf(separator);
            if (currentIndex >= 0 && (separatorIndex < 0 || currentIndex < separatorIndex)) {
                separatorIndex = currentIndex;
            }
        }

        if (separatorIndex >= 0 && separatorIndex + 1 < raw.length()) {
            return raw.substring(separatorIndex + 1).trim();
        }

        String[] rawWords = raw.trim().split("\\s+");
        for (String label : labels) {
            String[] labelWords = label.split("\\s+");
            if (normalized.startsWith(label) && rawWords.length > labelWords.length) {
                return String.join(" ", List.of(rawWords).subList(labelWords.length, rawWords.length)).trim();
            }
        }

        return raw.trim();
    }

    private String cleanupName(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.replaceAll("[^\\p{L}\\s-]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        String[] tokens = cleaned.split("\\s+");
        if (tokens.length >= 2 && tokens[tokens.length - 1].length() == 1) {
            cleaned = String.join(" ", List.of(tokens).subList(0, tokens.length - 1));
        }
        return cleaned.trim();
    }

    private String cleanupAddress(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.replaceAll("[^\\p{L}\\p{N}\\s,./-]", " ")
                // Remove all "Place of X" variants (with typos): resideree, residenos, resiklence, oigin, etc
                .replaceAll("(?iu)\\b[Rr](?:e?/)?.*?place\\s+of\\s+\\w+\\b", " ")
                // Remove Vietnamese + English mixed patterns
                .replaceAll("(?iu)place\\s+of\\s+resid", " ")
                // Remove lonely "Place of" prefixes
                .replaceAll("(?iu)place\\s+of", " ")
                // Remove patterns like "Nơi thường trú / Place..." where we only want Vietnamese part
                .replaceAll("(?iu)/\\s*[Pp](?:lace|face).*?\\b", " ")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\s+,", ",")
                .replaceAll("^[/,\\-\\s]+", "")
                .trim();

        cleaned = collapseRepeatedAdjacentPhrase(cleaned);
        cleaned = removeIsolatedOneLetterTokens(cleaned);
        // Remove garbage tokens like "R tu", "va VN" etc
        cleaned = removeNoisyTokens(cleaned);
        cleaned = trimTrailingUppercaseNoise(cleaned);
        return cleaned.replaceAll("\\s{2,}", " ")
                .replaceAll("[/,\\-\\s]+$", "")
                .trim();
    }

    private String removeNoisyTokens(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] tokens = value.split("\\s+");
        List<String> kept = new ArrayList<>();
        
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String normalized = token.replaceAll("[,./-]", "");
            
            // Remove pure English noise: "va", "VN", "R", "tu", "by", "of", "or", etc
            if (normalized.matches("(?i)^(va|vn|is|it|or|and|the|by|of|in|to|at|for|on|a|an)$")) {
                continue;
            }
            
            // Single letters followed by short text are likely OCR corruption (R tư, Z 7, etc)
            if (normalized.length() == 1 && Character.isLetter(normalized.charAt(0))) {
                // If next token is very short and non-standard, skip both
                if (i + 1 < tokens.length) {
                    String nextNorm = tokens[i + 1].replaceAll("[,./-]", "");
                    if (nextNorm.length() <= 3 && (nextNorm.matches("[A-Za-z0-9]+") || nextNorm.length() == 2)) {
                        // Skip this single letter AND mark to skip next token
                        i++; // Will be skipped
                        continue;
                    }
                }
                continue;
            }
            
            // Skip if it's just punctuation
            if (normalized.isBlank()) {
                continue;
            }
            
            // Remove ultra-short tokens (1-2 chars) that don't look like location abbreviations
            // Valid abbreviations: Tổ, Ấp, etc. But random chars like "tư", "ki", etc. get removed
            if (normalized.length() <= 2 && Character.isLowerCase(normalized.charAt(0))) {
                // Lowercase 2-char tokens like "tư", "ki", "xá" appearing alone are likely corruption
                // Unless they appear in specific contexts (Skip for now as they're likely noise)
                if (!containsAny(value.toLowerCase(), Set.of("tổ", "ấp", "xã"))) {
                    continue;
                }
            }
            
            kept.add(token);
        }
        
        return String.join(" ", kept);
    }

    private String collapseRepeatedAdjacentPhrase(String value) {
        return value.replaceAll("(?iu)\\b([\\p{L}]+\\s+[\\p{L}]+)\\s+\\1\\b", "$1");
    }

    private String removeIsolatedOneLetterTokens(String value) {
        String[] tokens = value.split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String token : tokens) {
            String normalized = token.replaceAll("[,./-]", "");
            // Remove ANY single letter (both lowercase and uppercase)
            if (normalized.length() == 1 && Character.isLetter(normalized.charAt(0))) {
                continue;
            }
            kept.add(token);
        }
        return String.join(" ", kept);
    }

    private String trimTrailingUppercaseNoise(String value) {
        String result = value.trim();
        while (result.matches(".*\\s\\p{Lu}{1,2}$")) {
            result = result.replaceFirst("\\s\\p{Lu}{1,2}$", "").trim();
        }
        return result;
    }

    private boolean isLabelLikeNameCandidate(String normalizedCandidate) {
        if (normalizedCandidate == null || normalizedCandidate.isBlank()) {
            return true;
        }

        return normalizedCandidate.contains("FULL NAME")
                || normalizedCandidate.contains("NAME")
                || normalizedCandidate.equals("HO VA TEN")
                || normalizedCandidate.equals("HO TEN")
                || normalizedCandidate.equals("TEN");
    }

    private boolean containsAny(String value, Set<String> tokens) {
        return tokens.stream().anyMatch(value::contains);
    }

    private double estimateConfidence(String citizenId, String fullName, LocalDate dateOfBirth, String gender,
            String address) {
        double score = 0;
        if (!isBlank(citizenId)) {
            score += 0.4;
        }
        if (!isBlank(fullName)) {
            score += 0.3;
        }
        if (dateOfBirth != null) {
            score += 0.2;
        }
        if (!isBlank(gender)) {
            score += 0.05;
        }
        if (!isBlank(address)) {
            score += 0.05;
        }
        return Math.min(score, 1D);
    }

    private OcrCccdResponse emptyResponse(double confidence) {
        return OcrCccdResponse.builder()
                .fullName("")
                .dateOfBirth(null)
                .gender("")
                .address("")
                .citizenId("")
                .confidence(confidence)
                .build();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TextLine(String original, String normalized) {
    }

    private record OcrAttempt(String rawText, OcrCccdResponse response, int variantIndex) {
    }

    private static class CandidateStat {
        private int count;
        private int bestQuality;
    }
}
