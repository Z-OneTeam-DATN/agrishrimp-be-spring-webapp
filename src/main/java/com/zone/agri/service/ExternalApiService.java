package com.zone.agri.service;

import com.zone.agri.dto.response.supplier.BankAccountResponse;
import com.zone.agri.dto.response.supplier.VietQrResponse;
import com.zone.agri.entity.Supplier;
import com.zone.agri.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final RestTemplate restTemplate;
    private final SupplierRepository supplierRepository;

    private static final String VIETQR_API_URL = "https://api.vietqr.io/v2/business/";
    private static final String ESGOO_TAX_API_URL = "https://esgoo.net/api-mst/";

    private static final String FIELD_NAME = "name";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_OWNER = "owner";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_TAX_CODE = "taxCode";

    public VietQrResponse.BusinessData getBusinessByTaxCode(String taxCode) {
        String normalizedTaxCode = taxCode == null ? "" : taxCode.trim();
        if (normalizedTaxCode.isEmpty()) {
            throw new RuntimeException("Mã số thuế không hợp lệ.");
        }

        List<String> queriedSources = new ArrayList<>();
        Map<String, String> rawByField = new LinkedHashMap<>();
        Map<String, String> fieldSources = new LinkedHashMap<>();

        Optional<VietQrResponse.BusinessData> vietQrData = fetchFromVietQr(normalizedTaxCode);
        queriedSources.add("VIETQR");
        vietQrData.ifPresent(data -> absorbData(data, "VIETQR", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> esgooData = fetchFromEsgoo(normalizedTaxCode);
        queriedSources.add("ESGOO");
        esgooData.ifPresent(data -> absorbData(data, "ESGOO", rawByField, fieldSources));

        Optional<VietQrResponse.BusinessData> internalData = fetchFromInternalSupplier(normalizedTaxCode);
        queriedSources.add("INTERNAL_SUPPLIER");
        internalData.ifPresent(data -> absorbData(data, "INTERNAL_SUPPLIER", rawByField, fieldSources));

        VietQrResponse.BusinessData merged = new VietQrResponse.BusinessData();
        merged.setTaxCode(firstNonBlank(rawByField.get(FIELD_TAX_CODE), normalizedTaxCode));
        merged.setName(rawByField.get(FIELD_NAME));
        merged.setAddress(rawByField.get(FIELD_ADDRESS));
        merged.setOwner(rawByField.get(FIELD_OWNER));
        merged.setPhone(rawByField.get(FIELD_PHONE));
        merged.setEmail(rawByField.get(FIELD_EMAIL));

        Map<String, String> fieldStatuses = new LinkedHashMap<>();
        for (String field : Arrays.asList(FIELD_NAME, FIELD_ADDRESS, FIELD_OWNER, FIELD_PHONE, FIELD_EMAIL)) {
            String value = rawByField.get(field);
            if (isNotBlank(value)) {
                fieldStatuses.put(field, "FOUND");
            } else {
                fieldStatuses.put(field, "SOURCE_MISSING");
            }
        }
        fieldStatuses.put(FIELD_TAX_CODE, "FOUND");

        merged.setFieldStatuses(fieldStatuses);
        merged.setFieldSources(fieldSources);
        merged.setQueriedSources(queriedSources);
        merged.setPrimarySource(determinePrimarySource(fieldSources));
        merged.setLookupMessage(
                buildLookupMessage(vietQrData.isPresent(), esgooData.isPresent(), internalData.isPresent()));

        return merged;
    }

    private Optional<VietQrResponse.BusinessData> fetchFromVietQr(String taxCode) {
        try {
            ResponseEntity<VietQrResponse> response = restTemplate.getForEntity(VIETQR_API_URL + taxCode,
                    VietQrResponse.class);
            VietQrResponse body = response.getBody();
            if (body != null && "00".equals(body.getCode()) && body.getData() != null) {
                return Optional.of(body.getData());
            }
        } catch (Exception ignored) {
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
            mapped.setAddress(firstNonBlank(asString(data.get("diachi")), asString(data.get("address"))));
            mapped.setOwner(firstNonBlank(asString(data.get("daidien")), asString(data.get("owner"))));
            mapped.setPhone(firstNonBlank(asString(data.get("sodienthoai")), asString(data.get("phone"))));
            mapped.setEmail(asString(data.get("email")));
            return Optional.of(mapped);
        } catch (Exception ignored) {
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
            return Optional.of(mapped);
        } catch (Exception ignored) {
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
    }

    private void absorbField(
            String field,
            String value,
            String source,
            Map<String, String> rawByField,
            Map<String, String> fieldSources) {
        if (!isNotBlank(value))
            return;
        if (!rawByField.containsKey(field) || !isNotBlank(rawByField.get(field))) {
            rawByField.put(field, value.trim());
            fieldSources.put(field, source);
        }
    }

    private String determinePrimarySource(Map<String, String> fieldSources) {
        if (fieldSources.isEmpty())
            return "NONE";
        return fieldSources.values().stream().findFirst().orElse("NONE");
    }

    private String buildLookupMessage(boolean hasVietQr, boolean hasEsgoo, boolean hasInternal) {
        if (hasVietQr || hasEsgoo || hasInternal) {
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
        return value == null ? null : String.valueOf(value);
    }

    public String lookupBankAccount(String bankBin, String accountNumber) {
        String url = "https://api.vietqr.io/v2/lookup";

        // Header yêu cầu từ VietQR (Bạn cần đăng ký Client ID/API Key trên vietqr.io)
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", "36f10827-25b3-43ef-900f-ba5af24de8d6");
        headers.set("x-api-key", "08f37a19-7a38-44f6-bcfc-531dde5ce656");

        Map<String, String> body = new HashMap<>();
        body.put("bin", bankBin);
        body.put("accountNumber", accountNumber);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<BankAccountResponse> response = restTemplate.postForEntity(url, entity,
                    BankAccountResponse.class);
            BankAccountResponse bodyResponse = response.getBody();
            System.out.println("PHẢN HỒI GỐC TỪ VIETQR: " + bodyResponse);
            if (bodyResponse != null && "00".equals(bodyResponse.getCode()) && bodyResponse.getData() != null) {
                return bodyResponse.getData().getAccountName();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}