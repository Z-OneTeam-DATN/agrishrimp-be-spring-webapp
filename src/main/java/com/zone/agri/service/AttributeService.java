package com.zone.agri.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.dto.response.admin.AttributeDTO;
import com.zone.agri.dto.response.product.AttributeValueResponse;
import com.zone.agri.entity.Attribute;
import com.zone.agri.entity.AttributeValue;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.repository.AttributeRepository;
import com.zone.agri.repository.SKUAttributeValueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository repository;
    private final SKUAttributeValueRepository skuAttributeValueRepository;

    @Transactional(readOnly = true)
    public List<AttributeDTO> getAll() {
        return repository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AttributeDTO getById(Long id) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thuộc tính!"));
        return toDTO(attr);
    }

    @Transactional
    public AttributeDTO create(AttributeDTO dto) {
        // KIỂM TRA TRÙNG MÃ CODE
        Optional<Attribute> existing = repository.findByCodeIgnoreCase(dto.getCode());
        if (existing.isPresent()) {
            throw new ConflictException("Mã Code '" + dto.getCode() + "' đã tồn tại! Vui lòng sử dụng mã khác.");
        }

        Attribute attr = new Attribute();
        mapToEntity(attr, dto);
        return toDTO(repository.save(attr));
    }

    @Transactional
    public AttributeDTO update(Long id, AttributeDTO dto) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thuộc tính!"));

        // KIỂM TRA TRÙNG MÃ CODE NHƯNG BỎ QUA CHÍNH NÓ
        Optional<Attribute> existing = repository.findByCodeIgnoreCase(dto.getCode());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new ConflictException("Mã Code '" + dto.getCode() + "' đã được sử dụng cho một thuộc tính khác!");
        }

        mapToEntity(attr, dto);
        try {
            return toDTO(repository.saveAndFlush(attr));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Không thể lưu: Có giá trị thuộc tính bạn vừa xóa đang được gắn cho biến thể sản phẩm!");
        }
    }

    @Transactional
    public void delete(Long id) {
        boolean isUsedInProducts = skuAttributeValueRepository.existsByAttributeId(id);
        if (isUsedInProducts) {
            throw new ConflictException(
                    "Không thể xóa thuộc tính này vì nó đang được gắn cho các biến thể sản phẩm. Vui lòng chuyển trạng thái sang 'Tạm ngừng' thay vì xóa.");
        }
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

    private void mapToEntity(Attribute entity, AttributeDTO dto) {
        entity.setName(dto.getName());
        if (dto.getCode() != null) {
            entity.setCode(dto.getCode().trim().toUpperCase());
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

        // 1. Loại bỏ các giá trị không còn xuất hiện trong danh sách mới truyền lên
        entity.getAttributeValues().removeIf(av -> !newValues.contains(av.getValue()));

        // 2. Lấy danh sách các giá trị đang có (sau khi đã lọc ở bước 1)
        List<String> existingValues = entity.getAttributeValues().stream()
                .map(AttributeValue::getValue)
                .collect(Collectors.toList());

        // 3. Thêm mới những giá trị người dùng vừa gõ thêm
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