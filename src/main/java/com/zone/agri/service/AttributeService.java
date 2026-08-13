package com.zone.agri.service;

import com.zone.agri.dto.response.admin.AttributeDTO;
import com.zone.agri.dto.response.product.AttributeValueResponse;
import com.zone.agri.entity.Attribute;
import com.zone.agri.entity.AttributeValue;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.AttributeRepository;
import com.zone.agri.repository.AttributeValueRepository;
import com.zone.agri.repository.SKUAttributeValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private static final int ATTRIBUTE_CODE_MAX_LENGTH = 50;

    private final AttributeRepository repository;
    private final AttributeValueRepository attributeValueRepository;
    private final SKUAttributeValueRepository skuAttributeValueRepository;

    @Transactional(readOnly = true)
    public List<AttributeDTO> getAll() {
        return getAll(null, null);
    }

    @Transactional(readOnly = true)
    public List<AttributeDTO> getAll(String keyword, AttributeStatus status) {
        String normalizedKeyword = normalizeText(keyword);
        return repository.findAll().stream()
                .filter(attribute -> normalizedKeyword == null
                        || normalizeText(attribute.getName()).toLowerCase(Locale.forLanguageTag("vi-VN"))
                                .contains(normalizedKeyword.toLowerCase(Locale.forLanguageTag("vi-VN"))))
                .filter(attribute -> status == null
                        || Objects.equals(
                                attribute.getStatus() != null ? attribute.getStatus() : AttributeStatus.ACTIVE,
                                status))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttributeDTO> getPublicAttributes() {
        return repository.findPublicAttributesWithValues().stream()
                .map(this::toPublicDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AttributeDTO getById(Long id) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thuộc tính"));
        return toDTO(attr);
    }

    @Transactional
    public AttributeDTO create(AttributeDTO dto) {
        String normalizedName = requireAttributeName(dto.getName());
        if (repository.existsByNameIgnoreCase(normalizedName)) {
            throw new ConflictException("Tên thuộc tính đã tồn tại: " + normalizedName, true);
        }

        dto.setCode(resolveAttributeCode(dto.getCode(), dto.getName(), null, null));
        dto.setName(normalizedName);

        List<String> sanitizedValues = sanitizeDistinctValues(dto.getValues());
        if (sanitizedValues.isEmpty()) {
            throw new ConflictException("Thuộc tính phải có ít nhất 1 giá trị!", true);
        }
        validateNoCrossAttributeValueConflict(sanitizedValues, null);
        dto.setValues(sanitizedValues);

        Attribute attr = new Attribute();
        mapToEntity(attr, dto);
        return toDTO(repository.save(attr));
    }

    @Transactional
    public AttributeDTO update(Long id, AttributeDTO dto) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thuộc tính"));

        String normalizedName = requireAttributeName(dto.getName());
        if (repository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new ConflictException("Tên thuộc tính đã tồn tại: " + normalizedName, true);
        }

        dto.setName(normalizedName);

        dto.setCode(resolveAttributeCode(dto.getCode(), dto.getName(), id, attr.getCode()));

        List<String> newValues = sanitizeDistinctValues(dto.getValues());

        if (newValues.isEmpty()) {
            throw new ConflictException("Thuộc tính phải có ít nhất 1 giá trị!", true);
        }

        Set<String> existingValueKeys = attr.getAttributeValues() == null
                ? Set.of()
                : attr.getAttributeValues().stream()
                        .map(AttributeValue::getValue)
                        .map(this::normalizeAttributeValueForCompare)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toSet());
        boolean hasNewValues = newValues.stream()
                .map(this::normalizeAttributeValueForCompare)
                .anyMatch(compareKey -> !existingValueKeys.contains(compareKey));
        AttributeStatus targetStatus = dto.getStatus() != null ? dto.getStatus() : attr.getStatus();
        if (Boolean.TRUE.equals(hasNewValues)
                && (attr.getStatus() == AttributeStatus.INACTIVE || targetStatus == AttributeStatus.INACTIVE)) {
            throw new ConflictException("Không thể thêm giá trị mới vào thuộc tính đang ẩn.", true);
        }

        validateNoCrossAttributeValueConflict(newValues, id);
        dto.setValues(newValues);

        Set<String> newValueKeys = newValues.stream()
                .map(this::normalizeAttributeValueForCompare)
                .collect(Collectors.toSet());

        List<AttributeValue> valuesToRemove = attr.getAttributeValues() != null
                ? attr.getAttributeValues().stream()
                .filter(av -> !newValueKeys.contains(normalizeAttributeValueForCompare(av.getValue())))
                .collect(Collectors.toList())
                : Collections.emptyList();

        for (AttributeValue value : valuesToRemove) {
            if (skuAttributeValueRepository.existsByAttributeValueId(value.getId())) {
                throw new ConflictException(
                        "Không thể xóa giá trị '" + value.getValue()
                                + "' vì đang được sử dụng bởi biến thể sản phẩm.",
                        true);
            }
        }

        if (!valuesToRemove.isEmpty()) {
            attr.getAttributeValues().removeAll(valuesToRemove);
        }

        mapToEntity(attr, dto);
        try {
            return toDTO(repository.saveAndFlush(attr));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Không thể lưu vì có giá trị thuộc tính vừa xóa đang được gán cho biến thể sản phẩm!",
                    true);
        }
    }

    @Transactional
    public void delete(Long id) {
        boolean isUsedInProducts = skuAttributeValueRepository.existsByAttributeId(id);
        if (isUsedInProducts) {
            throw new ConflictException(
                    "Không thể xóa thuộc tính này vì nó đang được gán cho các biến thể sản phẩm.",
                    true);
        }
        if (!repository.existsById(id)) {
            throw new NotFoundException("Không tìm thấy thuộc tính");
        }
        repository.deleteById(id);
    }

    private AttributeDTO toDTO(Attribute entity) {
        AttributeDTO dto = new AttributeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus() : AttributeStatus.ACTIVE);

        List<AttributeValue> attributeValues = entity.getAttributeValues();
        if (attributeValues != null && !attributeValues.isEmpty()) {
            List<AttributeValueResponse> details = new ArrayList<>();
            List<String> values = new ArrayList<>();

            for (AttributeValue av : attributeValues) {
                values.add(av.getValue());
                details.add(AttributeValueResponse.builder()
                        .attributeId(entity.getId())
                        .attributeName(entity.getName())
                        .attributeCode(entity.getCode())
                        .valueId(av.getId())
                        .value(av.getValue())
                        .usedInVariant(skuAttributeValueRepository.existsByAttributeValueId(av.getId()))
                        .build());
            }
            dto.setValues(values);
            dto.setValueDetails(details);
        } else {
            dto.setValues(Collections.emptyList());
            dto.setValueDetails(Collections.emptyList());
        }

        return dto;
    }

    private AttributeDTO toPublicDTO(Attribute entity) {
        AttributeDTO dto = new AttributeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus() : AttributeStatus.ACTIVE);

        List<AttributeValue> attributeValues = entity.getAttributeValues();
        if (attributeValues == null || attributeValues.isEmpty()) {
            dto.setValues(Collections.emptyList());
            dto.setValueDetails(Collections.emptyList());
            return dto;
        }

        List<String> values = new ArrayList<>();
        List<AttributeValueResponse> details = new ArrayList<>();
        for (AttributeValue av : attributeValues) {
            values.add(av.getValue());
            details.add(AttributeValueResponse.builder()
                    .attributeId(entity.getId())
                    .attributeName(entity.getName())
                    .attributeCode(entity.getCode())
                    .valueId(av.getId())
                    .value(av.getValue())
                    .usedInVariant(null)
                    .build());
        }
        dto.setValues(values);
        dto.setValueDetails(details);
        return dto;
    }

    private String resolveAttributeCode(String requestedCode, String name, Long currentId, String existingCode) {
        if (hasText(requestedCode)) {
            return buildUniqueCode(buildCodeBase(requestedCode), currentId);
        }

        if (hasText(existingCode)) {
            return existingCode.trim().toUpperCase(Locale.ROOT);
        }

        return buildUniqueCode(buildCodeBase(name), currentId);
    }

    private String buildCodeBase(String value) {
        String source = hasText(value) ? value : "ATTRIBUTE";
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        String code = normalized
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (code.isBlank()) {
            code = "ATTRIBUTE";
        }

        return code.length() > ATTRIBUTE_CODE_MAX_LENGTH
                ? code.substring(0, ATTRIBUTE_CODE_MAX_LENGTH).replaceAll("_+$", "")
                : code;
    }

    private boolean isCodeTaken(String code, Long currentId) {
        return currentId == null
                ? repository.existsByCodeIgnoreCase(code)
                : repository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
    }

    private String buildUniqueCode(String baseCode, Long currentId) {
        String candidate = baseCode;
        int suffix = 1;

        while (isCodeTaken(candidate, currentId)) {
            String suffixValue = "_" + suffix++;
            int maxBaseLength = Math.max(1, ATTRIBUTE_CODE_MAX_LENGTH - suffixValue.length());
            String truncatedBase = baseCode.length() > maxBaseLength
                    ? baseCode.substring(0, maxBaseLength).replaceAll("_+$", "")
                    : baseCode;
            candidate = truncatedBase + suffixValue;
        }

        return candidate;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void mapToEntity(Attribute entity, AttributeDTO dto) {
        entity.setName(requireAttributeName(dto.getName()));
        if (dto.getCode() != null) {
            entity.setCode(dto.getCode().trim().toUpperCase(Locale.ROOT));
        }
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : AttributeStatus.ACTIVE);

        if (entity.getAttributeValues() == null) {
            entity.setAttributeValues(new ArrayList<>());
        }

        List<String> newValues = sanitizeDistinctValues(dto.getValues());
        Map<String, AttributeValue> existingValuesByKey = entity.getAttributeValues().stream()
                .filter(attributeValue -> hasText(attributeValue.getValue()))
                .collect(Collectors.toMap(
                        attributeValue -> normalizeAttributeValueForCompare(attributeValue.getValue()),
                        attributeValue -> attributeValue,
                        (current, ignored) -> current,
                        LinkedHashMap::new
                ));

        Set<String> requestedValueKeys = new HashSet<>();

        for (String val : newValues) {
            String compareKey = normalizeAttributeValueForCompare(val);
            requestedValueKeys.add(compareKey);

            AttributeValue existingValue = existingValuesByKey.get(compareKey);
            if (existingValue != null) {
                existingValue.setValue(val);
                continue;
            }

            entity.getAttributeValues().add(AttributeValue.builder()
                    .attribute(entity)
                    .value(val)
                    .build());
        }

        entity.getAttributeValues().removeIf(attributeValue -> {
            String compareKey = normalizeAttributeValueForCompare(attributeValue.getValue());
            return compareKey.isEmpty() || !requestedValueKeys.contains(compareKey);
        });
    }

    private List<String> sanitizeDistinctValues(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }

        Map<String, String> normalizedValueMap = new LinkedHashMap<>();

        for (String rawValue : values) {
            if (rawValue == null) {
                continue;
            }

            String sanitizedValue = rawValue.trim().replaceAll("\\s+", " ");
            String compareKey = normalizeAttributeValueForCompare(sanitizedValue);
            if (compareKey.isEmpty() || normalizedValueMap.containsKey(compareKey)) {
                continue;
            }

            normalizedValueMap.put(compareKey, sanitizedValue);
        }

        return new ArrayList<>(normalizedValueMap.values());
    }

    private void validateNoCrossAttributeValueConflict(List<String> values, Long currentAttributeId) {
        if (values == null || values.isEmpty()) {
            return;
        }

        Map<String, String> requestedValuesByKey = values.stream()
                .collect(Collectors.toMap(
                        this::normalizeAttributeValueForCompare,
                        value -> value,
                        (current, ignored) -> current,
                        LinkedHashMap::new
                ));

        List<AttributeValue> existingValues = attributeValueRepository
                .findAllWithAttributeExcludingAttributeId(currentAttributeId);

        for (AttributeValue attributeValue : existingValues) {
            String compareKey = normalizeAttributeValueForCompare(attributeValue.getValue());
            if (!requestedValuesByKey.containsKey(compareKey)) {
                continue;
            }

            Attribute parentAttribute = attributeValue.getAttribute();
            String attributeName = parentAttribute != null && hasText(parentAttribute.getName())
                    ? parentAttribute.getName().trim()
                    : "thuộc tính khác";

            throw new ConflictException(
                    "Giá trị '" + requestedValuesByKey.get(compareKey)
                            + "' đã tồn tại ở thuộc tính '" + attributeName
                            + "' (không phân biệt hoa thường).",
                    true
            );
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String requireAttributeName(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new BadRequestException("Tên thuộc tính không được để trống");
        }
        return normalized;
    }

    private String normalizeAttributeValueForCompare(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.forLanguageTag("vi-VN"));
    }
}
