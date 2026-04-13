package com.zone.agri.common;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder:agrishrimp/products}")
    private String defaultFolder;

    /**
     * Upload một file lên Cloudinary và trả về kết quả gồm secureUrl + publicId.
     *
     * @param file   MultipartFile cần upload
     * @param folder Folder con bên trong defaultFolder (VD: "variants")
     * @return {@link UploadResult} chứa secureUrl và publicId
     */
    public UploadResult upload(MultipartFile file, String folder) {
        try {
            String fullFolder = defaultFolder + (folder != null ? "/" + folder : "");
            Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder",        fullFolder,
                    "resource_type", "image"
                )
            );
            String secureUrl = (String) result.get("secure_url");
            String publicId  = (String) result.get("public_id");
            log.info("Uploaded image to Cloudinary: publicId={}", publicId);
            return new UploadResult(secureUrl, publicId);
        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new RuntimeException("Không thể upload ảnh lên Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Xóa ảnh khỏi Cloudinary theo publicId.
     *
     * @param publicId Public ID của ảnh cần xóa
     */
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted image from Cloudinary: publicId={}", publicId);
        } catch (IOException e) {
            log.warn("Cloudinary delete failed for publicId={}: {}", publicId, e.getMessage());
        }
    }

    /**
     * Upload ảnh từ URL hoặc chuỗi base64 lên Cloudinary.
     * Dùng cho CategoryService (legacy – ảnh từ FE gửi dưới dạng data URI hoặc URL).
     *
     * @param imageData URL hoặc chuỗi base64 (data:image/...)
     * @return secure_url của ảnh sau khi upload
     */
    public String uploadImage(String imageData) {
        return uploadImage(imageData, "categories");
    }

    /**
     * Upload ảnh từ URL hoặc chuỗi base64 lên Cloudinary vào folder chỉ định.
     *
     * @param imageData URL hoặc chuỗi base64 (data:image/...)
     * @param folder Folder con bên trong defaultFolder
     * @return secure_url của ảnh sau khi upload
     */
    public String uploadImage(String imageData, String folder) {
        try {
            String fullFolder = defaultFolder + (folder != null ? "/" + folder : "");
            Map<?, ?> result = cloudinary.uploader().upload(
                imageData,
                ObjectUtils.asMap(
                    "folder",        fullFolder,
                    "resource_type", "image"
                )
            );
            String secureUrl = (String) result.get("secure_url");
            log.info("Uploaded image (url/base64) to Cloudinary: {}", result.get("public_id"));
            return secureUrl;
        } catch (IOException e) {
            log.error("Cloudinary uploadImage failed: {}", e.getMessage());
            throw new RuntimeException("Không thể upload ảnh lên Cloudinary: " + e.getMessage(), e);
        }
    }

    public record UploadResult(String secureUrl, String publicId) {}
}
