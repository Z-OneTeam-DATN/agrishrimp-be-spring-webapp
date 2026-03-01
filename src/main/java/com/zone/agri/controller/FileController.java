package com.zone.agri.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    // Nếu bạn có FileService thì inject vào đây
    // private final FileService fileService;

    @PostMapping("/tmpUpload")
    public ResponseEntity<?> uploadTempFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File tải lên không được để trống!"));
        }

        try {
            // 👉 NẾU BẠN UP LÊN CLOUDINARY
            // CloudinaryService.UploadResult result = fileService.upload(file, "temp");
            // return ResponseEntity.ok(Map.of("tmpPath", result.secureUrl()));

            // 👉 HOẶC NẾU CHỈ MÔ PHỎNG / TEST (Trả về URL tạm)
            // Giả lập lưu file thành công và trả về đường dẫn
            String fakeTempUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg";

            return ResponseEntity.ok(Map.of(
                    "tmpPath", fakeTempUrl,
                    "message", "Upload thành công"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi upload file: " + e.getMessage()));
        }
    }
}