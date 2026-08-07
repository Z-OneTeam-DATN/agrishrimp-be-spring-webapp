package com.zone.agri.service;

import com.zone.agri.dto.response.supplier.BankAccountResponse;
import com.zone.agri.dto.response.supplier.VietQrResponse;
import com.zone.agri.entity.Supplier;
import com.zone.agri.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final RestTemplate restTemplate;
    private final SupplierRepository supplierRepository;

    @Value("${vietqr.client-id}")
    private String vietQrClientId;

    @Value("${vietqr.api-key}")
    private String vietQrApiKey;

    @Value("${xinvoice.client-id:}")
    private String xInvoiceClientId;

    @Value("${xinvoice.api-key:}")
    private String xInvoiceApiKey;

    private static final String VIETQR_API_URL = "https://api.vietqr.io/v2/business/";
    private static final String ESGOO_TAX_API_URL = "https://esgoo.net/api-mst/";
    private static final String TTDN_API_URL = "https://thongtindoanhnghiep.co/api/company/";
    private static final String XINVOICE_API_URL = "https://api.xinvoice.vn/gdt-api/tax-payer/";

    private static final String FIELD_NAME = "name";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_OWNER = "owner";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_TAX_CODE = "taxCode";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ISSUE_DATE = "issueDate";
    private static final String FIELD_TAX_AUTHORITY = "taxAuthority";
    private static final String FIELD_MAIN_BUSINESS_SECTOR = "mainBusinessSector";

    public VietQrResponse.BusinessData getBusinessByTaxCode(String taxCode) {
        String normalizedTaxCode = taxCode == null ? "" : taxCode.trim();
        if (normalizedTaxCode.isEmpty()) {
            throw new RuntimeException("Mã số thuế không hợp lệ.");
        }

        List<String> queriedSources = new ArrayList<>();
        Map<String, String> rawByField = new LinkedHashMap<>();
        Map<String, String> fieldSources = new LinkedHashMap<>();

        // Start all fetches in parallel
        CompletableFuture<Optional<VietQrResponse.BusinessData>> vietQrFuture = CompletableFuture.supplyAsync(() -> fetchFromVietQr(normalizedTaxCode));
        CompletableFuture<Optional<VietQrResponse.BusinessData>> esgooFuture = CompletableFuture.supplyAsync(() -> fetchFromEsgoo(normalizedTaxCode));
        CompletableFuture<Optional<VietQrResponse.BusinessData>> ttdnFuture = CompletableFuture.supplyAsync(() -> fetchFromThongTinDoanhNghiep(normalizedTaxCode));
        CompletableFuture<Optional<VietQrResponse.BusinessData>> xInvoiceFuture = CompletableFuture.supplyAsync(() -> fetchFromXInvoice(normalizedTaxCode));
        CompletableFuture<Optional<VietQrResponse.BusinessData>> doanhNghiepFuture = CompletableFuture.supplyAsync(() -> fetchFromDoanhNghiepBiz(normalizedTaxCode));
        CompletableFuture<Optional<VietQrResponse.BusinessData>> masothueFuture = CompletableFuture.supplyAsync(() -> fetchFromMasoThueCom(normalizedTaxCode));
        CompletableFuture<Optional<VietQrResponse.BusinessData>> hosoCongTyFuture = CompletableFuture.supplyAsync(() -> fetchFromHosoCongTy(normalizedTaxCode));
        CompletableFuture<Optional<VietQrResponse.BusinessData>> internalFuture = CompletableFuture.supplyAsync(() -> fetchFromInternalSupplier(normalizedTaxCode));

        // Wait for all fetches to complete, or time out after 10 seconds
        try {
            CompletableFuture.allOf(vietQrFuture, esgooFuture, ttdnFuture, xInvoiceFuture, doanhNghiepFuture, masothueFuture, hosoCongTyFuture, internalFuture)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("One or more tax code APIs timed out or failed to complete in 10s: {}", e.getMessage());
        }

        // Gather completed results in priority order
        Optional<VietQrResponse.BusinessData> doanhNghiepData = joinFuture(doanhNghiepFuture);
        queriedSources.add("DOANH_NGHIEP_BIZ");
        doanhNghiepData.ifPresent(data -> absorbData(data, "DOANH_NGHIEP_BIZ", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> masothueData = joinFuture(masothueFuture);
        queriedSources.add("MA_SO_THUE");
        masothueData.ifPresent(data -> absorbData(data, "MA_SO_THUE", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> hosoCongTyData = joinFuture(hosoCongTyFuture);
        queriedSources.add("HOSO_CONG_TY");
        hosoCongTyData.ifPresent(data -> absorbData(data, "HOSO_CONG_TY", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> xInvoiceData = joinFuture(xInvoiceFuture);
        queriedSources.add("XINVOICE");
        xInvoiceData.ifPresent(data -> absorbData(data, "XINVOICE", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> vietQrData = joinFuture(vietQrFuture);
        queriedSources.add("VIETQR");
        vietQrData.ifPresent(data -> absorbData(data, "VIETQR", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> esgooData = joinFuture(esgooFuture);
        queriedSources.add("ESGOO");
        esgooData.ifPresent(data -> absorbData(data, "ESGOO", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> ttdnData = joinFuture(ttdnFuture);
        queriedSources.add("THONG_TIN_DOANH_NGHIEP");
        ttdnData.ifPresent(data -> absorbData(data, "THONG_TIN_DOANH_NGHIEP", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> internalData = joinFuture(internalFuture);
        queriedSources.add("INTERNAL_SUPPLIER");
        internalData.ifPresent(data -> absorbData(data, "INTERNAL_SUPPLIER", rawByField, fieldSources));

        VietQrResponse.BusinessData merged = new VietQrResponse.BusinessData();
        merged.setTaxCode(firstNonBlank(rawByField.get(FIELD_TAX_CODE), normalizedTaxCode));
        merged.setName(cleanCompanyName(rawByField.get(FIELD_NAME), normalizedTaxCode));
        merged.setAddress(rawByField.get(FIELD_ADDRESS));
        merged.setOwner(rawByField.get(FIELD_OWNER));
        merged.setPhone(normalizePhone(rawByField.get(FIELD_PHONE)));
        merged.setEmail(rawByField.get(FIELD_EMAIL));
        merged.setStatus(rawByField.get(FIELD_STATUS));
        merged.setIssueDate(normalizeDate(rawByField.get(FIELD_ISSUE_DATE)));
        merged.setTaxAuthority(firstNonBlank(rawByField.get(FIELD_TAX_AUTHORITY), inferTaxAuthorityFromAddress(merged.getAddress())));
        merged.setMainBusinessSector(rawByField.get(FIELD_MAIN_BUSINESS_SECTOR));

        boolean multiSourceAgreement = vietQrData.isPresent() && (doanhNghiepData.isPresent() || masothueData.isPresent() || esgooData.isPresent());

        Map<String, String> fieldStatuses = new LinkedHashMap<>();
        fieldStatuses.put(FIELD_TAX_CODE, "VERIFIED");
        fieldStatuses.put(FIELD_NAME, isNotBlank(merged.getName()) ? (multiSourceAgreement ? "VERIFIED" : "FOUND") : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_ADDRESS, isNotBlank(merged.getAddress()) ? (multiSourceAgreement ? "VERIFIED" : "FOUND") : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_OWNER, isNotBlank(merged.getOwner()) ? "FOUND" : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_PHONE, isNotBlank(merged.getPhone()) ? "FOUND" : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_EMAIL, isNotBlank(merged.getEmail()) ? "FOUND" : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_STATUS, isNotBlank(merged.getStatus()) ? "FOUND" : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_ISSUE_DATE, isNotBlank(merged.getIssueDate()) ? "FOUND" : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_TAX_AUTHORITY, isNotBlank(merged.getTaxAuthority()) ? (rawByField.containsKey(FIELD_TAX_AUTHORITY) ? "FOUND" : "INFERRED") : "SOURCE_MISSING");
        fieldStatuses.put(FIELD_MAIN_BUSINESS_SECTOR, isNotBlank(merged.getMainBusinessSector()) ? "FOUND" : "SOURCE_MISSING");

        merged.setFieldStatuses(fieldStatuses);
        merged.setFieldSources(fieldSources);
        merged.setQueriedSources(queriedSources);
        merged.setPrimarySource(determinePrimarySource(fieldSources));
        merged.setLookupMessage(
                buildLookupMessage(
                        vietQrData.isPresent(),
                        esgooData.isPresent(),
                        ttdnData.isPresent() || xInvoiceData.isPresent() || doanhNghiepData.isPresent() || masothueData.isPresent(),
                        internalData.isPresent()
                )
        );

        return merged;
    }

    private Optional<VietQrResponse.BusinessData> joinFuture(CompletableFuture<Optional<VietQrResponse.BusinessData>> future) {
        try {
            return future.get(4, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Error joining future: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<VietQrResponse.BusinessData> fetchFromVietQr(String taxCode) {
        try {
            ResponseEntity<VietQrResponse> response = restTemplate.getForEntity(VIETQR_API_URL + taxCode,
                    VietQrResponse.class);
            VietQrResponse body = response.getBody();
            if (body != null && "00".equals(body.getCode()) && body.getData() != null) {
                return Optional.of(body.getData());
            }
        } catch (Exception e) {
            log.warn("Error fetching from VietQR for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<VietQrResponse.BusinessData> fetchFromEsgoo(String taxCode) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    ESGOO_TAX_API_URL + taxCode + ".htm",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> body = response.getBody();
            if (body == null)
                return Optional.empty();

            Object error = body.get("error");
            Object dataObj = body.get("data");
            if (!(error instanceof Number) || ((Number) error).intValue() != 0 || !(dataObj instanceof Map<?, ?>)) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) dataObj;
            VietQrResponse.BusinessData mapped = new VietQrResponse.BusinessData();
            mapped.setTaxCode(firstNonBlank(asString(data.get("mst")), taxCode));
            mapped.setName(firstNonBlank(asString(data.get("ten")), asString(data.get("name"))));
            mapped.setAddress(firstNonBlank(asString(data.get("dc")), asString(data.get("diachi")), asString(data.get("address"))));
            mapped.setOwner(firstNonBlank(asString(data.get("daidien")), asString(data.get("owner"))));
            mapped.setPhone(firstNonBlank(asString(data.get("dt")), asString(data.get("sodienthoai")), asString(data.get("phone"))));
            mapped.setEmail(asString(data.get("email")));
            mapped.setStatus(firstNonBlank(asString(data.get("tinhtrang")), asString(data.get("trang_thai")), asString(data.get("status"))));
            mapped.setIssueDate(firstNonBlank(asString(data.get("hoatdong")), asString(data.get("ngay_thanh_lap")), asString(data.get("issueDate"))));
            mapped.setTaxAuthority(firstNonBlank(asString(data.get("co_quan_ql")), asString(data.get("taxAuthority"))));
            mapped.setMainBusinessSector(
                    firstNonBlank(asString(data.get("nganh_kinh_te")), asString(data.get("mainBusinessSector"))));
            return Optional.of(mapped);
        } catch (Exception e) {
            log.warn("Error fetching from Esgoo for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<VietQrResponse.BusinessData> fetchFromInternalSupplier(String taxCode) {
        try {
            Optional<Supplier> supplierOpt = supplierRepository.findByTaxCode(taxCode);
            if (supplierOpt.isEmpty()) {
                return Optional.empty();
            }

            Supplier supplier = supplierOpt.get();
            VietQrResponse.BusinessData mapped = new VietQrResponse.BusinessData();
            mapped.setTaxCode(supplier.getTaxCode());
            mapped.setName(supplier.getName());
            mapped.setAddress(supplier.getAddressDetail());
            mapped.setOwner(supplier.getContactName());
            mapped.setPhone(supplier.getPhone());
            mapped.setEmail(supplier.getEmail());
            if (supplier.getStatus() != null) {
                mapped.setStatus(supplier.getStatus().name());
            }
            if (supplier.getIssueDate() != null) {
                mapped.setIssueDate(supplier.getIssueDate().toString());
            }
            mapped.setTaxAuthority(supplier.getTaxAuthority());
            mapped.setMainBusinessSector(supplier.getMainBusinessSector());
            return Optional.of(mapped);
        } catch (Exception e) {
            log.warn("Error fetching from Internal Supplier for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<VietQrResponse.BusinessData> fetchFromThongTinDoanhNghiep(String taxCode) {
        try {
            // Bắt buộc phải set User-Agent nếu không hệ thống của họ sẽ chặn (403
            // Forbidden)
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    TTDN_API_URL + taxCode,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> body = response.getBody();
            if (body == null || body.isEmpty())
                return Optional.empty();

            VietQrResponse.BusinessData mapped = new VietQrResponse.BusinessData();
            mapped.setTaxCode(firstNonBlank(asString(body.get("ID")), taxCode));
            mapped.setName(asString(body.get("Title")));
            mapped.setAddress(asString(body.get("DiaChi")));
            mapped.setOwner(asString(body.get("ChuSoHuu")));
            mapped.setPhone(asString(body.get("DienThoai")));
            mapped.setEmail(asString(body.get("Email")));
            mapped.setStatus(asString(body.get("TrangThai")));
            mapped.setIssueDate(asString(body.get("NgayThanhLap")));
            mapped.setTaxAuthority(asString(body.get("CoQuanQuanLy")));
            mapped.setMainBusinessSector(asString(body.get("NganhKinhTe")));

            return Optional.of(mapped);
        } catch (Exception e) {
            log.warn("Error fetching from Thong Tin Doanh Nghiep for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private void absorbData(
            VietQrResponse.BusinessData data,
            String source,
            Map<String, String> rawByField,
            Map<String, String> fieldSources) {
        absorbField(FIELD_TAX_CODE, data.getTaxCode(), source, rawByField, fieldSources);
        absorbField(FIELD_NAME, data.getName(), source, rawByField, fieldSources);
        absorbField(FIELD_ADDRESS, data.getAddress(), source, rawByField, fieldSources);
        absorbField(FIELD_OWNER, data.getOwner(), source, rawByField, fieldSources);
        absorbField(FIELD_PHONE, data.getPhone(), source, rawByField, fieldSources);
        absorbField(FIELD_EMAIL, data.getEmail(), source, rawByField, fieldSources);
        absorbField(FIELD_STATUS, data.getStatus(), source, rawByField, fieldSources);
        absorbField(FIELD_ISSUE_DATE, data.getIssueDate(), source, rawByField, fieldSources);
        absorbField(FIELD_TAX_AUTHORITY, data.getTaxAuthority(), source, rawByField, fieldSources);
        absorbField(FIELD_MAIN_BUSINESS_SECTOR, data.getMainBusinessSector(), source, rawByField, fieldSources);
    }

    private void absorbField(
            String field,
            String value,
            String source,
            Map<String, String> rawByField,
            Map<String, String> fieldSources) {
        if (!isNotBlank(value))
            return;
        if (FIELD_ISSUE_DATE.equals(field) && "1970-01-01".equals(value.trim())) {
            return;
        }
        if (!rawByField.containsKey(field) || !isNotBlank(rawByField.get(field))) {
            String sanitizedValue = value.trim();
            if (FIELD_MAIN_BUSINESS_SECTOR.equals(field)) {
                sanitizedValue = sanitizeMainBusinessSector(sanitizedValue);
            }
            rawByField.put(field, sanitizedValue);
            fieldSources.put(field, source);
        }
    }

    private String sanitizeMainBusinessSector(String sector) {
        if (sector == null || sector.trim().isEmpty()) {
            return sector;
        }
        String trimmed = sector.trim();

        // Sửa các cụm từ lỗi font phổ biến từ các trang tra cứu
        trimmed = trimmed.replaceAll("(?i)bỏn\\s+buụn", "Bán buôn");
        trimmed = trimmed.replaceAll("(?i)thiỏt\\s+b\\?", "thiết bị");
        trimmed = trimmed.replaceAll("(?i)linh\\s+kiỏn", "linh kiện");
        trimmed = trimmed.replaceAll("(?i)xu\\?t\\s+b\\?n", "Xuất bản");
        trimmed = trimmed.replaceAll("(?i)ph\\?n\\s+m\\?m", "phần mềm");
        trimmed = trimmed.replaceAll("(?i)mỏy\\s+tớnh", "máy tính");
        trimmed = trimmed.replaceAll("(?i)nuụi\\s+tr\\?ng", "nuôi trồng");
        trimmed = trimmed.replaceAll("(?i)thu\\?\\s+s\\?n", "thuỷ sản");
        trimmed = trimmed.replaceAll("(?i)nhõn\\s+gi\\?ng", "nhân giống");
        trimmed = trimmed.replaceAll("(?i)t\\?\\s+v\\?n", "tư vấn");
        trimmed = trimmed.replaceAll("(?i)d\\?ch\\s+v\\?", "dịch vụ");
        trimmed = trimmed.replaceAll("(?i)viỏn\\s+thụng", "viễn thông");
        trimmed = trimmed.replaceAll("(?i)cụng\\s+nghỏ", "công nghệ");
        trimmed = trimmed.replaceAll("(?i)thụng\\s+tin", "thông tin");
        trimmed = trimmed.replaceAll("(?i)khỏc", "khác");
        trimmed = trimmed.replaceAll("(?i)KHỎC", "KHÁC");

        return trimmed;
    }

    private String cleanCompanyName(String name, String taxCode) {
        if (name == null || name.trim().isEmpty()) return name;
        String trimmed = name.trim();
        if (trimmed.contains(" - ")) {
            String[] parts = trimmed.split(" - ", 2);
            String prefixDigits = parts[0].replaceAll("\\D+", "");
            if (!prefixDigits.isEmpty() && taxCode != null && prefixDigits.equals(taxCode.replaceAll("\\D+", ""))) {
                return parts[1].trim();
            }
        }
        return trimmed;
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        String cleaned = dateStr.trim();
        if ("1970-01-01".equals(cleaned) || "0000-00-00".equals(cleaned)) return null;

        if (cleaned.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return cleaned;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})[\\/\\-](\\d{1,2})[\\/\\-](\\d{4})").matcher(cleaned);
        if (m.find()) {
            String day = String.format("%02d", Integer.parseInt(m.group(1)));
            String month = String.format("%02d", Integer.parseInt(m.group(2)));
            String year = m.group(3);
            return year + "-" + month + "-" + day;
        }
        return cleaned;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return null;
        String digits = phone.replaceAll("[^0-9+]", "").trim();
        if (digits.length() < 7 || digits.length() > 15) return null;
        return digits;
    }


    private String determinePrimarySource(Map<String, String> fieldSources) {
        if (fieldSources.isEmpty())
            return "NONE";
        return fieldSources.values().stream().findFirst().orElse("NONE");
    }

    private String buildLookupMessage(boolean hasVietQr, boolean hasEsgoo, boolean hasTtdn, boolean hasInternal) {
        if (hasVietQr || hasEsgoo || hasTtdn || hasInternal) {
            return "Đã tổng hợp dữ liệu từ nhiều nguồn để tăng độ đầy đủ.";
        }
        return "Không tìm thấy dữ liệu từ các nguồn tra cứu.";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        if (values == null)
            return null;
        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        // Check if the value is empty or the literal string "null" (case-insensitive)
        if (stringValue.isEmpty() || "null".equalsIgnoreCase(stringValue)) {
            return null;
        }
        return stringValue;
    }

    public String lookupBankAccount(String bankBin, String accountNumber) {
        String url = "https://api.vietqr.io/v2/lookup";

        // Use injected credentials from properties instead of hardcoded values
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", vietQrClientId);
        headers.set("x-api-key", vietQrApiKey);

        Map<String, String> body = new HashMap<>();
        body.put("bin", bankBin);
        body.put("accountNumber", accountNumber);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<BankAccountResponse> response = restTemplate.postForEntity(url, entity,
                    BankAccountResponse.class);
            BankAccountResponse bodyResponse = response.getBody();
            log.debug("Response from VietQR: {}", bodyResponse);
            if (bodyResponse != null && "00".equals(bodyResponse.getCode()) && bodyResponse.getData() != null) {
                return bodyResponse.getData().getAccountName();
            }
        } catch (Exception e) {
            log.error("Error looking up bank account for BIN {} and accountNumber {}: {}", bankBin, accountNumber,
                    e.getMessage());
        }
        return null;
    }

    private Optional<VietQrResponse.BusinessData> fetchFromXInvoice(String taxCode) {
        if (xInvoiceClientId == null || xInvoiceClientId.trim().isEmpty() ||
            xInvoiceApiKey == null || xInvoiceApiKey.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("client-id", xInvoiceClientId.trim());
            headers.set("api-key", xInvoiceApiKey.trim());
            headers.set("Accept", "application/json");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    XINVOICE_API_URL + taxCode,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> body = response.getBody();
            if (body == null || body.isEmpty())
                return Optional.empty();

            VietQrResponse.BusinessData mapped = new VietQrResponse.BusinessData();
            mapped.setTaxCode(firstNonBlank(asString(body.get("taxID")), taxCode));
            mapped.setName(asString(body.get("name")));
            mapped.setAddress(asString(body.get("address")));
            mapped.setTaxAuthority(asString(body.get("taxDepartment")));
            mapped.setStatus(asString(body.get("status")));
            
            return Optional.of(mapped);
        } catch (Exception e) {
            log.warn("Error fetching from XInvoice for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<VietQrResponse.BusinessData> fetchFromDoanhNghiepBiz(String taxCode) {
        try {
            String url = "https://doanhnghiep.biz/" + taxCode;
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .referrer("https://doanhnghiep.biz/")
                    .timeout(8000)
                    .get();

            org.jsoup.nodes.Element table = doc.selectFirst("table.company-table");
            if (table == null) {
                return Optional.empty();
            }

            // Verify tax code matches
            String scrapedTaxCode = null;
            org.jsoup.nodes.Element mstEl = table.selectFirst("td[itemprop=taxID], *[itemprop=taxID]");
            if (mstEl != null) {
                scrapedTaxCode = mstEl.text().trim();
            } else {
                for (org.jsoup.nodes.Element tr : table.select("tr")) {
                    org.jsoup.select.Elements tds = tr.select("td, th");
                    if (tds.size() >= 2) {
                        String label = tds.get(0).text().toLowerCase();
                        if (label.contains("mst") || label.contains("dkkd") || label.contains("mã số thuế")) {
                            scrapedTaxCode = tds.get(1).text().trim();
                            break;
                        }
                    }
                }
            }

            String cleanScraped = scrapedTaxCode == null ? "" : scrapedTaxCode.replaceAll("\\s+", "").replace("-", "");
            String cleanInput = taxCode.replaceAll("\\s+", "").replace("-", "");

            if (!cleanScraped.isEmpty() && !cleanScraped.contains(cleanInput)) {
                log.warn("DoanhNghiepBiz tax code mismatch. Expected: {}, Scraped: {}", taxCode, scrapedTaxCode);
                return Optional.empty();
            }

            VietQrResponse.BusinessData mapped = new VietQrResponse.BusinessData();
            mapped.setTaxCode(taxCode);

            // Name
            org.jsoup.nodes.Element nameEl = table.selectFirst("th[itemprop=name]");
            if (nameEl == null) {
                nameEl = doc.selectFirst("h5, h1");
            }
            if (nameEl != null) {
                String nameText = nameEl.text().trim();
                if (nameText.contains(" - ")) {
                    String[] parts = nameText.split(" - ", 2);
                    if (parts[0].replaceAll("\\D+", "").equals(cleanInput)) {
                        nameText = parts[1].trim();
                    }
                }
                mapped.setName(nameText);
            }

            // Incorporated Date (Ngày cấp)
            org.jsoup.nodes.Element incDateEl = table.selectFirst("td[itemprop=IncorporatedDate], *[itemprop=IncorporatedDate]");
            if (incDateEl != null && !incDateEl.text().trim().isEmpty()) {
                mapped.setIssueDate(incDateEl.text().trim());
            }

            // Start Date (Ngày hoạt động) if Incorporated Date is empty
            if (mapped.getIssueDate() == null || mapped.getIssueDate().isEmpty()) {
                org.jsoup.nodes.Element startDateEl = table.selectFirst("td[itemprop=StartDate], *[itemprop=StartDate]");
                if (startDateEl != null && !startDateEl.text().trim().isEmpty()) {
                    mapped.setIssueDate(startDateEl.text().trim());
                }
            }

            // Fallback issue date search
            if (mapped.getIssueDate() == null || mapped.getIssueDate().isEmpty()) {
                for (org.jsoup.nodes.Element tr : table.select("tr")) {
                    org.jsoup.select.Elements tds = tr.select("td");
                    if (tds.size() >= 2) {
                        String label = tds.get(0).text().toLowerCase();
                        if (label.contains("ngày cấp") || label.contains("ngày thành lập") || label.contains("ngày hoạt động")) {
                            mapped.setIssueDate(tds.get(1).text().trim());
                            break;
                        }
                    }
                }
            }

            // Status
            org.jsoup.nodes.Element statusEl = table.selectFirst("td[itemprop=Status], *[itemprop=Status]");
            if (statusEl != null) {
                mapped.setStatus(statusEl.text().trim());
            }

            // Address
            org.jsoup.nodes.Element addressEl = table.selectFirst("td[itemprop=address], *[itemprop=address]");
            if (addressEl != null) {
                mapped.setAddress(addressEl.text().trim());
            }

            // Owner (Người đại diện)
            org.jsoup.nodes.Element ownerEl = table.selectFirst("span[itemprop=Owner] a, *[itemprop=Owner] a, tr[itemprop=Owner] a, span[itemprop=Owner]");
            if (ownerEl != null && !ownerEl.text().trim().isEmpty()) {
                mapped.setOwner(ownerEl.text().trim());
            } else {
                for (org.jsoup.nodes.Element tr : table.select("tr")) {
                    org.jsoup.select.Elements tds = tr.select("td");
                    if (tds.size() >= 2) {
                        String label = tds.get(0).text().toLowerCase();
                        if (label.contains("đại diện") || label.contains("chủ sở hữu") || label.contains("giám đốc")) {
                            mapped.setOwner(tds.get(1).text().trim());
                            break;
                        }
                    }
                }
            }

            // Phone
            org.jsoup.nodes.Element phoneEl = table.selectFirst("td[itemprop=Phone], *[itemprop=Phone]");
            if (phoneEl != null && !phoneEl.text().trim().isEmpty()) {
                mapped.setPhone(phoneEl.text().trim());
            } else {
                for (org.jsoup.nodes.Element tr : table.select("tr")) {
                    org.jsoup.select.Elements tds = tr.select("td");
                    if (tds.size() >= 2) {
                        String label = tds.get(0).text().toLowerCase();
                        if (label.contains("điện thoại") || label.contains("sđt") || label.contains("phone")) {
                            mapped.setPhone(tds.get(1).text().trim());
                            break;
                        }
                    }
                }
            }

            // Business Line (Ngành nghề)
            org.jsoup.nodes.Element businessLineEl = table.selectFirst("td[itemprop=BusinessLine], *[itemprop=BusinessLine]");
            if (businessLineEl != null && !businessLineEl.text().trim().isEmpty()) {
                mapped.setMainBusinessSector(businessLineEl.text().trim());
            } else {
                for (org.jsoup.nodes.Element tr : table.select("tr")) {
                    org.jsoup.select.Elements tds = tr.select("td");
                    if (tds.size() >= 2) {
                        String label = tds.get(0).text().toLowerCase();
                        if (label.contains("ngành nghề") || label.contains("lĩnh vực")) {
                            mapped.setMainBusinessSector(tds.get(1).text().trim());
                            break;
                        }
                    }
                }
            }

            // Email (Cloudflare protected email)
            org.jsoup.nodes.Element emailCf = table.selectFirst("a[href^=/cdn-cgi/l/email-protection]");
            if (emailCf != null) {
                String cfemail = emailCf.attr("data-cfemail");
                if (cfemail != null && !cfemail.isEmpty()) {
                    mapped.setEmail(decodeCloudflareEmail(cfemail));
                }
            } else {
                org.jsoup.nodes.Element emailCfSpan = table.selectFirst("span.__cf_email__");
                if (emailCfSpan != null) {
                    String cfemail = emailCfSpan.attr("data-cfemail");
                    if (cfemail != null && !cfemail.isEmpty()) {
                        mapped.setEmail(decodeCloudflareEmail(cfemail));
                    }
                }
            }

            return Optional.of(mapped);
        } catch (Exception e) {
            log.warn("Error scraping from DoanhNghiepBiz for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<VietQrResponse.BusinessData> fetchFromMasoThueCom(String taxCode) {
        try {
            String url = "https://masothue.com/" + taxCode;
            org.jsoup.nodes.Document doc;
            try {
                doc = org.jsoup.Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                        .referrer("https://masothue.com/")
                        .followRedirects(true)
                        .timeout(8000)
                        .get();
            } catch (Exception e) {
                url = "https://masothue.com/Search/?q=" + taxCode + "&type=auto";
                doc = org.jsoup.Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                        .referrer("https://masothue.com/")
                        .followRedirects(true)
                        .timeout(8000)
                        .get();
            }

            org.jsoup.nodes.Element table = doc.selectFirst("table.table-taxinfo");
            if (table == null) {
                return Optional.empty();
            }

            // Verify tax code matches
            String scrapedTaxCode = null;
            org.jsoup.nodes.Element mstEl = table.selectFirst("td[itemprop=taxID], *[itemprop=taxID]");
            if (mstEl != null) {
                scrapedTaxCode = mstEl.text().trim();
            } else {
                for (org.jsoup.nodes.Element tr : table.select("tr")) {
                    org.jsoup.select.Elements tds = tr.select("td, th");
                    if (tds.size() >= 2) {
                        String label = tds.get(0).text().toLowerCase();
                        if (label.contains("mã số thuế") || label.contains("mst")) {
                            scrapedTaxCode = tds.get(1).text().trim();
                            break;
                        }
                    }
                }
            }

            String cleanScraped = scrapedTaxCode == null ? "" : scrapedTaxCode.replaceAll("\\s+", "").replace("-", "");
            String cleanInput = taxCode.replaceAll("\\s+", "").replace("-", "");

            if (!cleanScraped.isEmpty() && !cleanScraped.contains(cleanInput)) {
                log.warn("MasoThue tax code mismatch. Expected: {}, Scraped: {}", taxCode, scrapedTaxCode);
                return Optional.empty();
            }

            VietQrResponse.BusinessData mapped = new VietQrResponse.BusinessData();
            mapped.setTaxCode(taxCode);

            // Name
            org.jsoup.nodes.Element nameEl = table.selectFirst("th[itemprop=name]");
            if (nameEl != null) {
                mapped.setName(nameEl.text().trim());
            }

            // Address
            org.jsoup.nodes.Element addressEl = table.selectFirst("td[itemprop=address]");
            if (addressEl != null) {
                mapped.setAddress(addressEl.text().trim());
            }

            // Owner (Người đại diện)
            org.jsoup.nodes.Element ownerEl = table.selectFirst("tr[itemprop=alumni] span[itemprop=name] a, tr[itemprop=alumni] td:eq(1) a, tr[itemprop=alumni] td:eq(1)");
            if (ownerEl == null) {
                for (org.jsoup.nodes.Element tr : table.select("tr")) {
                    org.jsoup.select.Elements tds = tr.select("td");
                    if (tds.size() >= 2) {
                        String label = tds.get(0).text().toLowerCase();
                        if (label.contains("người đại diện") || label.contains("đại diện pháp luật") || label.contains("chủ sở hữu")) {
                            ownerEl = tds.get(1);
                            break;
                        }
                    }
                }
            }
            if (ownerEl != null && !ownerEl.text().trim().isEmpty()) {
                mapped.setOwner(ownerEl.text().trim());
            }

            // Phone
            org.jsoup.nodes.Element phoneEl = table.selectFirst("td[itemprop=telephone], span#tel-full");
            if (phoneEl != null) {
                org.jsoup.nodes.Element btn = phoneEl.selectFirst("button");
                if (btn != null) {
                    btn.remove();
                }
                mapped.setPhone(phoneEl.text().trim());
            }

            // Issue Date (Ngày hoạt động)
            for (org.jsoup.nodes.Element tr : table.select("tr")) {
                org.jsoup.select.Elements tds = tr.select("td");
                if (tds.size() >= 2) {
                    String label = tds.get(0).text().toLowerCase();
                    if (label.contains("ngày hoạt động") || label.contains("ngày cấp") || label.contains("ngày thành lập")) {
                        mapped.setIssueDate(tds.get(1).text().trim());
                        break;
                    }
                }
            }

            // Tax Authority (Quản lý bởi)
            for (org.jsoup.nodes.Element tr : table.select("tr")) {
                org.jsoup.select.Elements tds = tr.select("td");
                if (tds.size() >= 2) {
                    String label = tds.get(0).text().toLowerCase();
                    if (label.contains("quản lý bởi") || label.contains("cơ quan thuế")) {
                        mapped.setTaxAuthority(tds.get(1).text().trim());
                        break;
                    }
                }
            }

            // Main Business Sector (Ngành nghề chính)
            for (org.jsoup.nodes.Element tr : table.select("tr")) {
                org.jsoup.select.Elements tds = tr.select("td");
                if (tds.size() >= 2) {
                    String label = tds.get(0).text().toLowerCase();
                    if (label.contains("ngành nghề chính") || label.contains("ngành nghề kinh doanh")) {
                        mapped.setMainBusinessSector(tds.get(1).text().trim());
                        break;
                    }
                }
            }

            return Optional.of(mapped);
        } catch (Exception e) {
            log.warn("Error scraping from MasoThueCom for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<VietQrResponse.BusinessData> fetchFromHosoCongTy(String taxCode) {
        try {
            String cleanTaxCode = taxCode.replaceAll("\\s+", "").replace("-", "");
            String url = "https://hosocongty.vn/search?q=" + cleanTaxCode;
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .referrer("https://hosocongty.vn/")
                    .timeout(6000)
                    .get();

            org.jsoup.nodes.Element detailBlock = doc.selectFirst("ul.hoso-ct, div.hoso-detail, table");
            if (detailBlock == null) {
                org.jsoup.nodes.Element link = doc.selectFirst("ul.list-hoso a[href]");
                if (link != null) {
                    String href = link.attr("abs:href");
                    doc = org.jsoup.Jsoup.connect(href)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .timeout(6000)
                            .get();
                }
            }

            VietQrResponse.BusinessData mapped = new VietQrResponse.BusinessData();
            mapped.setTaxCode(taxCode);

            for (org.jsoup.nodes.Element el : doc.select("ul.hoso-ct li, tr, div.info-row, p")) {
                String text = el.text();
                String lower = text.toLowerCase();
                if ((lower.contains("tên công ty") || lower.contains("tên chính thức")) && mapped.getName() == null) {
                    String[] parts = text.split(":", 2);
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        mapped.setName(parts[1].trim());
                    }
                }
                if ((lower.contains("đại diện") || lower.contains("chủ sở hữu") || lower.contains("giám đốc")) && mapped.getOwner() == null) {
                    String[] parts = text.split(":", 2);
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        String rawOwner = parts[1].trim().replaceAll("(?i)\\s*\\(.*?\\)", "").replaceAll("(?i)\\s*\\[.*?\\]", "").trim();
                        mapped.setOwner(rawOwner);
                    }
                }
                if ((lower.contains("ngày cấp") || lower.contains("ngày thành lập") || lower.contains("ngày hoạt động") || lower.contains("bắt đầu")) && mapped.getIssueDate() == null) {
                    String[] parts = text.split(":", 2);
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        mapped.setIssueDate(parts[1].trim());
                    }
                }
                if ((lower.contains("quản lý bởi") || lower.contains("cơ quan thuế")) && mapped.getTaxAuthority() == null) {
                    String[] parts = text.split(":", 2);
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        mapped.setTaxAuthority(parts[1].trim());
                    }
                }
                if ((lower.contains("ngành nghề") || lower.contains("lĩnh vực")) && mapped.getMainBusinessSector() == null) {
                    String[] parts = text.split(":", 2);
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        mapped.setMainBusinessSector(parts[1].trim());
                    }
                }
                if ((lower.contains("địa chỉ") || lower.contains("trụ sở")) && mapped.getAddress() == null) {
                    String[] parts = text.split(":", 2);
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        mapped.setAddress(parts[1].trim());
                    }
                }
            }

            if (mapped.getName() != null || mapped.getOwner() != null || mapped.getIssueDate() != null) {
                return Optional.of(mapped);
            }
        } catch (Exception e) {
            log.debug("Error scraping from HosoCongTy for taxCode {}: {}", taxCode, e.getMessage());
        }
        return Optional.empty();
    }

    private String inferTaxAuthorityFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        String cleanedAddress = address.replaceAll("(?i),\\s*(việt nam|viet nam|vn)\\s*$", "").trim();
        String asciiAddress = removeAccents(cleanedAddress).toLowerCase();
        
        // Cần Thơ
        if (asciiAddress.contains("can tho")) {
            if (asciiAddress.contains("ninh kieu") ||
                asciiAddress.contains("cai khe") ||
                asciiAddress.contains("an khanh") ||
                asciiAddress.contains("hung loi") ||
                asciiAddress.contains("xuan khanh") ||
                asciiAddress.contains("an nghiep") ||
                asciiAddress.contains("an cu") ||
                asciiAddress.contains("an hoa") ||
                asciiAddress.contains("thoi binh") ||
                asciiAddress.contains("tan an") ||
                asciiAddress.contains("an binh")) {
                return "Thuế cơ sở 1 thành phố Cần Thơ";
            }
            if (asciiAddress.contains("binh thuy") ||
                asciiAddress.contains("an thoi") ||
                asciiAddress.contains("long hoa") ||
                asciiAddress.contains("long tuyen") ||
                asciiAddress.contains("thoi an dong") ||
                asciiAddress.contains("tra an") ||
                asciiAddress.contains("tra noc")) {
                return "Thuế cơ sở 2 thành phố Cần Thơ";
            }
            if (asciiAddress.contains("cai rang")) {
                return "Chi cục Thuế Quận Cái Răng";
            }
            if (asciiAddress.contains("o mon")) {
                return "Chi cục Thuế Quận Ô Môn";
            }
            if (asciiAddress.contains("thot not")) {
                return "Chi cục Thuế Quận Thốt Nốt";
            }
            return "Cục Thuế Thành phố Cần Thơ";
        }
        
        // Hậu Giang
        if (asciiAddress.contains("hau giang")) {
            if (asciiAddress.contains("vi thanh")) {
                return "Chi cục Thuế Thành phố Vị Thanh";
            }
            if (asciiAddress.contains("nga bay")) {
                return "Chi cục Thuế Thành phố Ngã Bảy";
            }
            return "Cục Thuế Tỉnh Hậu Giang";
        }
        
        // Vĩnh Long
        if (asciiAddress.contains("vinh long")) {
            return "Cục Thuế Tỉnh Vĩnh Long";
        }
        
        // Sóc Trăng
        if (asciiAddress.contains("soc trang")) {
            return "Cục Thuế Tỉnh Sóc Trăng";
        }
        
        // Đồng Tháp
        if (asciiAddress.contains("dong thap")) {
            return "Cục Thuế Tỉnh Đồng Tháp";
        }
        
        // An Giang
        if (asciiAddress.contains("an giang")) {
            return "Cục Thuế Tỉnh An Giang";
        }
        
        // Kiên Giang
        if (asciiAddress.contains("kien giang")) {
            return "Cục Thuế Tỉnh Kiên Giang";
        }
        
        // Thực hiện suy luận chung cho tất cả các tỉnh thành khác
        return generalInferTaxAuthority(cleanedAddress);
    }

    private String generalInferTaxAuthority(String address) {
        String[] parts = address.split(",");
        if (parts.length == 0) {
            return null;
        }
        
        String provincePart = parts[parts.length - 1].trim();
        String provinceName = cleanProvinceName(provincePart);
        if (provinceName.isEmpty()) {
            return null;
        }
        
        String districtPart = parts.length >= 2 ? parts[parts.length - 2].trim() : "";
        if (districtPart.isEmpty() || !isDistrictKeyword(districtPart)) {
            districtPart = findDistrictInAddress(address);
        }
        
        if (!districtPart.isEmpty()) {
            String districtName = cleanDistrictName(districtPart);
            return "Chi cục Thuế " + districtName;
        }
        
        if (provinceName.startsWith("Thành phố")) {
            return "Cục Thuế " + provinceName;
        } else {
            return "Cục Thuế Tỉnh " + provinceName;
        }
    }

    private String cleanProvinceName(String part) {
        String clean = part.replaceAll("(?i)^(tỉnh|thành phố|tp\\.?|t\\.p\\.?)\\s+", "").trim();
        String asciiClean = removeAccents(clean).toLowerCase();
        if (asciiClean.equals("hcm") || asciiClean.equals("ho chi minh")) {
            return "Thành phố Hồ Chí Minh";
        }
        if (asciiClean.equals("ha noi") || asciiClean.equals("hn")) {
            return "Thành phố Hà Nội";
        }
        if (asciiClean.equals("da nang") || asciiClean.equals("dn")) {
            return "Thành phố Đà Nẵng";
        }
        if (asciiClean.equals("hai phong") || asciiClean.equals("hp")) {
            return "Thành phố Hải Phòng";
        }
        if (asciiClean.equals("can tho") || asciiClean.equals("ct")) {
            return "Thành phố Cần Thơ";
        }
        return capitalizeWords(clean);
    }

    private boolean isDistrictKeyword(String part) {
        String asciiLower = removeAccents(part).trim().toLowerCase();
        return asciiLower.startsWith("quan") || asciiLower.startsWith("q.") || 
               asciiLower.startsWith("huyen") || asciiLower.startsWith("h.") || 
               asciiLower.startsWith("thi xa") || asciiLower.startsWith("tx.") || 
               asciiLower.startsWith("thanh pho") || asciiLower.startsWith("tp.");
    }

    private String cleanDistrictName(String part) {
        String clean = part.trim();
        String asciiLower = removeAccents(clean).toLowerCase();
        if (asciiLower.startsWith("q.")) {
            clean = "Quận " + clean.substring(2).trim();
        } else if (asciiLower.startsWith("h.")) {
            clean = "Huyện " + clean.substring(2).trim();
        } else if (asciiLower.startsWith("tx.")) {
            clean = "Thị xã " + clean.substring(3).trim();
        } else if (asciiLower.startsWith("tp.")) {
            clean = "Thành phố " + clean.substring(3).trim();
        } else {
            if (asciiLower.startsWith("quan ")) {
                clean = "Quận " + clean.substring(5).trim();
            } else if (asciiLower.startsWith("huyen ")) {
                clean = "Huyện " + clean.substring(6).trim();
            } else if (asciiLower.startsWith("thi xa ")) {
                clean = "Thị xã " + clean.substring(7).trim();
            } else if (asciiLower.startsWith("thanh pho ")) {
                clean = "Thành phố " + clean.substring(10).trim();
            }
        }
        return capitalizeWords(clean);
    }

    private String findDistrictInAddress(String address) {
        String[] parts = address.split(",");
        for (int i = parts.length - 2; i >= 0; i--) {
            String p = parts[i].trim();
            if (isDistrictKeyword(p)) {
                return p;
            }
        }
        return "";
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return "";
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)))
              .append(w.substring(1).toLowerCase())
              .append(" ");
        }
        return sb.toString().trim();
    }

    private String removeAccents(String src) {
        if (src == null) return "";
        String temp = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    private String decodeCloudflareEmail(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            StringBuilder email = new StringBuilder();
            int r = Integer.parseInt(encoded.substring(0, 2), 16);
            for (int n = 2; n < encoded.length(); n += 2) {
                int i = Integer.parseInt(encoded.substring(n, n + 2), 16) ^ r;
                email.append((char) i);
            }
            return email.toString();
        } catch (Exception e) {
            return null;
        }
    }
}