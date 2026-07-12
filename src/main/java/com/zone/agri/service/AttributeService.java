package com.zone.agri.service;

import com.zone.agri.dto.response.admin.AttributeDTO;
import com.zone.agri.dto.response.product.AttributeValueResponse;
import com.zone.agri.entity.Attribute;
import com.zone.agri.entity.AttributeValue;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.repository.AttributeRepository;
import com.zone.agri.repository.SKUAttributeValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private static final int ATTRIBUTE_CODE_MAX_LENGTH = 50;

    private final AttributeRepository repository;
    private final SKUAttributeValueRepository skuAttributeValueRepository;

    @Transactional(readOnly = true)
    public List<AttributeDTO> getAll() {
        return repository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttributeDTO> getPublicAttributes() {
        return repository.findAll().stream()
                .filter(attribute -> attribute.getStatus() == null || attribute.getStatus() == AttributeStatus.ACTIVE)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AttributeDTO getById(Long id) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay thuoc tinh!"));
        return toDTO(attr);
    }

    @Transactional
    public AttributeDTO create(AttributeDTO dto) {
        dto.setCode(resolveAttributeCode(dto.getCode(), dto.getName(), null, null));

        Attribute attr = new Attribute();
        mapToEntity(attr, dto);
        return toDTO(repository.save(attr));
    }

    @Transactional
    public AttributeDTO update(Long id, AttributeDTO dto) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay thuoc tinh!"));

        dto.setCode(resolveAttributeCode(dto.getCode(), dto.getName(), id, attr.getCode()));

        List<String> newValues = dto.getValues() != null ? dto.getValues().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList())
                : new ArrayList<>();

        if (newValues.isEmpty()) {
            throw new ConflictException("Thuoc tinh phai co it nhat 1 gia tri!", true);
        }

        List<AttributeValue> valuesToRemove = attr.getAttributeValues() != null
                ? attr.getAttributeValues().stream()
                .filter(av -> !newValues.contains(av.getValue()))
                .collect(Collectors.toList())
                : Collections.emptyList();

        for (AttributeValue value : valuesToRemove) {
            if (skuAttributeValueRepository.existsByAttributeValueId(value.getId())) {
                throw new ConflictException(
                        "Khong the xoa gia tri '" + value.getValue()
                                + "' vi dang duoc su dung boi bien the san pham.",
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
                    "Khong the luu vi co gia tri thuoc tinh vua xoa dang duoc gan cho bien the san pham!",
                    true);
        }
    }

    @Transactional
    public void delete(Long id) {
        boolean isUsedInProducts = skuAttributeValueRepository.existsByAttributeId(id);
        if (isUsedInProducts) {
            throw new ConflictException(
                    "Khong the xoa thuoc tinh nay vi no dang duoc gan cho cac bien the san pham.",
                    true);
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
        entity.setName(dto.getName());
        if (dto.getCode() != null) {
            entity.setCode(dto.getCode().trim().toUpperCase(Locale.ROOT));
        }
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : AttributeStatus.ACTIVE);

        if (entity.getAttributeValues() == null) {
            entity.setAttributeValues(new ArrayList<>());
        }

        List<String> newValues = dto.getValues() != null ? dto.getValues().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList())
                : new ArrayList<>();

        List<String> existingValues = entity.getAttributeValues().stream()
                .map(AttributeValue::getValue)
                .collect(Collectors.toList());

        for (String val : newValues) {
            if (!existingValues.contains(val)) {
                entity.getAttributeValues().add(AttributeValue.builder()
                        .attribute(entity)
                        .value(val)
                        .build());
            }
        }
    }
}
