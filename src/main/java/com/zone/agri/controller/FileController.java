package com.zone.agri.controller;

import com.zone.agri.common.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "Xử lý tải lên tập tin tạm thời")
public class FileController {

    private final CloudinaryService cloudinaryService;

    @Operation(
        summary = "Tải lên tập tin tạm thời",
        description = "Tải tập tin lên Cloudinary và nhận về URL công khai"
    )
    @PostMapping("/tmpUpload")
    public ResponseEntity<?> tmpUpload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "mediaType", required = false) String mediaType
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Vui lòng chọn tập tin để tải lên.");
        }

        try {
            CloudinaryService.UploadResult result = cloudinaryService.upload(
                file,
                "temp",
                CloudinaryService.UploadMediaType.resolve(mediaType, file.getContentType())
            );
            return ResponseEntity.ok(Map.of(
                "url", result.secureUrl(),
                "publicId", result.publicId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Lỗi khi tải tập tin lên: " + e.getMessage());
        }
    }
}
