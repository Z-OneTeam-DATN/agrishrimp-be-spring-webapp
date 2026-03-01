package com.zone.agri.service;

import com.zone.agri.dto.admin.AttributeDTO;
import com.zone.agri.dto.product.AttributeValueResponse;
import com.zone.agri.entity.Attribute;
import com.zone.agri.entity.AttributeValue;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.repository.AttributeRepository;
import com.zone.agri.repository.SKUAttributeValueRepository;
import com.zone.agri.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        return toDTO(repository.save(attr));
    }

    @Transactional
    public void delete(Long id) {
        boolean isUsedInProducts = skuAttributeValueRepository.existsByAttributeId(id);
        if (isUsedInProducts) {
            throw new ConflictException("Không thể xóa thuộc tính này vì nó đang được gắn cho các biến thể sản phẩm. Vui lòng chuyển trạng thái sang 'Tạm ngừng' thay vì xóa.");
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

        if (entity.getAttributeValues() != null) {
            // Vẫn trả về List<String> cho form Admin cũ
            List<String> values = entity.getAttributeValues().stream()
                    .map(AttributeValue::getValue)
                    .collect(Collectors.toList());
            dto.setValues(values);

            // 👉 BƠM DỮ LIỆU CÓ CHỨA ID VÀO DTO
            List<AttributeValueResponse> details = entity.getAttributeValues().stream()
                    .map(av -> AttributeValueResponse.builder()
                            .attributeId(entity.getId())
                            .attributeName(entity.getName())
                            .attributeCode(entity.getCode())
                            .valueId(av.getId()) // ĐÂY LÀ CÁI ID QUAN TRỌNG NHẤT FE CẦN!
                            .value(av.getValue())
                            .build())
                    .collect(Collectors.toList());
            dto.setValueDetails(details);
        } else {
            dto.setValues(Collections.emptyList());
            dto.setValueDetails(Collections.emptyList());
        }

        return dto;
    }

    private void mapToEntity(Attribute entity, AttributeDTO dto) {
        entity.setName(dto.getName());
        entity.setCode(dto.getCode().toUpperCase()); // Luôn ép hoa mã code
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : AttributeStatus.ACTIVE);

        if (entity.getAttributeValues() == null) {
            entity.setAttributeValues(new ArrayList<>());
        }

        entity.getAttributeValues().clear();

        if (dto.getValues() != null && !dto.getValues().isEmpty()) {
            List<AttributeValue> newValues = dto.getValues().stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(val -> AttributeValue.builder()
                            .attribute(entity)
                            .value(val)
                            .build())
                    .collect(Collectors.toList());

            entity.getAttributeValues().addAll(newValues);
        }
    }
}