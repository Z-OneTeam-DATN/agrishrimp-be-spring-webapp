package com.zone.agri.common;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private static final int VIDEO_CHUNK_SIZE_BYTES = 6 * 1024 * 1024;

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder:agrishrimp/products}")
    private String defaultFolder;

    public UploadResult upload(MultipartFile file, String folder) {
        return upload(file, folder, UploadMediaType.resolve(null, file.getContentType()));
    }

    public UploadResult upload(MultipartFile file, String folder, UploadMediaType mediaType) {
        try {
            String fullFolder = defaultFolder + (folder != null ? "/" + folder : "");
            UploadMediaType resolvedType = mediaType != null
                ? mediaType
                : UploadMediaType.resolve(null, file.getContentType());

            Map<?, ?> result = resolvedType == UploadMediaType.VIDEO
                ? uploadLargeVideo(file, fullFolder)
                : uploadStandard(file, fullFolder, resolvedType);

            String secureUrl = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");
            log.info("Uploaded file to Cloudinary: publicId={}, mediaType={}", publicId, resolvedType);
            return new UploadResult(secureUrl, publicId);
        } catch (IOException | IllegalStateException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new RuntimeException("Không thể tải tập tin lên Cloudinary: " + e.getMessage(), e);
        }
    }

    private Map<?, ?> uploadStandard(
        MultipartFile file,
        String fullFolder,
        UploadMediaType mediaType
    ) throws IOException {
        String resourceType = mediaType == UploadMediaType.IMAGE ? "image" : "auto";
        return cloudinary.uploader().upload(
            file.getBytes(),
            ObjectUtils.asMap(
                "folder", fullFolder,
                "resource_type", resourceType
            )
        );
    }

    private Map<?, ?> uploadLargeVideo(MultipartFile file, String fullFolder) throws IOException {
        Path tempFile = Files.createTempFile(
            "cloudinary-video-",
            resolveTempFileSuffix(file.getOriginalFilename())
        );
        try {
            file.transferTo(tempFile.toFile());
            Map<String, Object> options = new HashMap<>();
            options.put("folder", fullFolder);
            options.put("resource_type", "video");
            return cloudinary.uploader().uploadLarge(
                tempFile.toFile(),
                options,
                VIDEO_CHUNK_SIZE_BYTES
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String resolveTempFileSuffix(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".tmp";
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return ".tmp";
        }
        return originalFilename.substring(dotIndex);
    }

    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted image from Cloudinary: publicId={}", publicId);
        } catch (IOException e) {
            log.warn("Cloudinary delete failed for publicId={}: {}", publicId, e.getMessage());
        }
    }

    public String uploadImage(String imageData) {
        return uploadImage(imageData, "categories");
    }

    public String uploadImage(String imageData, String folder) {
        return uploadImage(imageData, folder, null);
    }

    public String uploadImage(String imageData, String folder, String publicId) {
        try {
            String fullFolder = defaultFolder + (folder != null ? "/" + folder : "");
            Map<String, Object> options = new HashMap<>();
            options.put("folder", fullFolder);
            options.put("resource_type", "image");
            if (publicId != null && !publicId.isBlank()) {
                options.put("public_id", publicId);
            }
            Map<?, ?> result = cloudinary.uploader().upload(imageData, options);
            String secureUrl = (String) result.get("secure_url");
            log.info("Uploaded image (url/base64) to Cloudinary: {}", result.get("public_id"));
            return secureUrl;
        } catch (IOException e) {
            log.error("Cloudinary uploadImage failed: {}", e.getMessage());
            throw new RuntimeException("Không thể tải ảnh lên Cloudinary: " + e.getMessage(), e);
        }
    }

    public record UploadResult(String secureUrl, String publicId) {}

    public enum UploadMediaType {
        AUTO,
        IMAGE,
        VIDEO;

        public static UploadMediaType resolve(String requestValue, String contentType) {
            if (requestValue != null && !requestValue.isBlank()) {
                try {
                    return UploadMediaType.valueOf(requestValue.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Fall back to content type detection for backward compatibility.
                }
            }

            if (contentType != null) {
                String normalized = contentType.toLowerCase(Locale.ROOT);
                if (normalized.startsWith("video/")) {
                    return VIDEO;
                }
                if (normalized.startsWith("image/")) {
                    return IMAGE;
                }
            }

            return AUTO;
        }
    }
}
