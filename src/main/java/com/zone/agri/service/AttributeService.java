package com.zone.agri.service;

import com.zone.agri.dto.admin.AttributeDTO;
import com.zone.agri.entity.Attribute;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.repository.AttributeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository repository;

    // Lấy tất cả
    public List<AttributeDTO> getAll() {
        return repository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    // Lấy chi tiết
    public AttributeDTO getById(Long id) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thuộc tính!"));
        return toDTO(attr);
    }

    // Tạo mới
    public Attribute create(AttributeDTO dto) {
        Attribute attr = new Attribute();
        mapToEntity(attr, dto);
        return repository.save(attr);
    }

    // Cập nhật
    public Attribute update(Long id, AttributeDTO dto) {
        Attribute attr = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thuộc tính!"));
        mapToEntity(attr, dto);
        return repository.save(attr);
    }

    // Xóa
    public void delete(Long id) {
        repository.deleteById(id);
    }

    // HÀM CHUYỂN ĐỔI
    private AttributeDTO toDTO(Attribute entity) {
        AttributeDTO dto = new AttributeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setDescription(entity.getDescription());

        dto.setStatus(entity.getStatus() != null ? entity.getStatus() : AttributeStatus.ACTIVE);
        if (entity.getValueList() != null && !entity.getValueList().trim().isEmpty()) {
            dto.setValues(Arrays.asList(entity.getValueList().split(",")));
        } else {
            dto.setValues(new ArrayList<>());
        }
        return dto;
    }

    private void mapToEntity(Attribute entity, AttributeDTO dto) {
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus());

        //  List ["A", "B"] => "A,B"
        if (dto.getValues() != null && !dto.getValues().isEmpty()) {
            entity.setValueList(String.join(",", dto.getValues()));
        } else {
            entity.setValueList("");
        }
    }
}