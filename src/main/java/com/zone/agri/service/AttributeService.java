package com.zone.agri.service;

import com.zone.agri.dto.admin.AttributeDTO;
import com.zone.agri.entity.Attribute;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.entity.enums.AttributeType;
import com.zone.agri.repository.AttributeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository repository;

    // Lấy tất cả
    @Transactional(readOnly = true)
    public List<AttributeDTO> getAll() {
        return repository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    // Lấy chi tiết
    @Transactional(readOnly = true)
    public AttributeDTO getById(Long id) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thuộc tính!"));
        return toDTO(attr);
    }

    // Tạo mới
    @Transactional
    public AttributeDTO create(AttributeDTO dto) {
        Attribute attr = new Attribute();
        mapToEntity(attr, dto);
        return toDTO(repository.save(attr));
    }

    // Cập nhật
    @Transactional
    public AttributeDTO update(Long id, AttributeDTO dto) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thuộc tính!"));
        mapToEntity(attr, dto);
        return toDTO(repository.save(attr));
    }

    // Xóa
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private AttributeDTO toDTO(Attribute entity) {
        AttributeDTO dto = new AttributeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setType(entity.getType() != null ? entity.getType().name() : null);
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus() : AttributeStatus.ACTIVE);
        dto.setValues(parseValueList(entity.getValueList()));
        return dto;
    }

    private void mapToEntity(Attribute entity, AttributeDTO dto) {
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus());
        entity.setValueList(joinValues(dto.getValues()));
        if (dto.getType() != null) {
            try {
                entity.setType(AttributeType.valueOf(dto.getType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                entity.setType(AttributeType.TEXT);
            }
        }
    }

    /** "250g, 500g, 1kg" → ["250g", "500g", "1kg"] */
    private List<String> parseValueList(String valueList) {
        if (valueList == null || valueList.isBlank()) return Collections.emptyList();
        return Arrays.stream(valueList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** ["250g", "500g", "1kg"] → "250g,500g,1kg" */
    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
    }
}
