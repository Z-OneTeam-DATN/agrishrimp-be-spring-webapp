package com.zone.agri.controller;

import com.zone.agri.common.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "Xử lý tải lên hình ảnh và tài liệu")
public class FileController {

    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Tải lên ảnh tạm thời", description = "Tải ảnh lên Cloudinary và nhận về URL công khai")
    @PostMapping("/tmpUpload")
    public ResponseEntity<?> tmpUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Vui lòng chọn tập tin để tải lên");
        }

        try {
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "temp");
            return ResponseEntity.ok(Map.of(
                "url", result.secureUrl(),
                "publicId", result.publicId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Lỗi khi tải ảnh lên: " + e.getMessage());
        }
    }
}
