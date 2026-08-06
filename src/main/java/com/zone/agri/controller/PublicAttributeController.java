package com.zone.agri.controller;

import com.zone.agri.dto.response.admin.AttributeDTO;
import com.zone.agri.service.AttributeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/attributes")
@RequiredArgsConstructor
@Tag(name = "Public Attribute APIs", description = "Các API công khai để lấy thuộc tính sản phẩm")
public class PublicAttributeController {

    private final AttributeService attributeService;

    @Operation(summary = "Lấy danh sách thuộc tính đang hiển thị",
            description = "Trả về các thuộc tính ACTIVE và giá trị con để frontend public dựng bộ lọc sản phẩm.")
    @GetMapping
    public ResponseEntity<List<AttributeDTO>> getPublicAttributes() {
        return ResponseEntity.ok(attributeService.getPublicAttributes());
    }
}
