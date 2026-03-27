package com.zone.agri.service;

import com.zone.agri.dto.response.ImageSearchResult;
import com.zone.agri.entity.ProductVector;
import com.zone.agri.repository.ProductVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageSearchService {

    @Value("${image-search.base-url:http://localhost:5001}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ProductVectorRepository productVectorRepository;

    /**
     * Index một sản phẩm vào AI service (fire-and-forget, không block CRUD).
     * Nếu thành công → upsert bản ghi trong product_vectors để track URL đã index.
     */
    @Async
    public void indexProduct(Long productId, String imageUrl) {
        try {
            Map<String, Object> body = Map.of("productId", productId, "imageUrl", imageUrl);
            restTemplate.postForObject(baseUrl + "/index", body, Object.class);

            // Upsert product_vectors
            ProductVector record = productVectorRepository.findByProductId(productId)
                    .orElseGet(() -> ProductVector.builder().productId(productId).build());
            record.setImageUrl(imageUrl);
            record.setIndexedAt(LocalDateTime.now());
            productVectorRepository.save(record);

            log.info("[ImageSearch] Indexed product {}, imageUrl={}", productId, imageUrl);
        } catch (Exception e) {
            log.warn("[ImageSearch] Failed to index product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Index hàng loạt sản phẩm (dùng cho index-all, chạy đồng bộ).
     * Upsert toàn bộ bản ghi product_vectors tương ứng sau khi Python xác nhận thành công.
     */
    public int indexBatch(List<Map<String, Object>> products) {
        Map<String, Object> body = Map.of("products", products);
        restTemplate.postForObject(baseUrl + "/index/batch", body, Object.class);

        // Upsert tất cả product_vectors
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> p : products) {
            Long productId = ((Number) p.get("productId")).longValue();
            String imageUrl = (String) p.get("imageUrl");

            ProductVector record = productVectorRepository.findByProductId(productId)
                    .orElseGet(() -> ProductVector.builder().productId(productId).build());
            record.setImageUrl(imageUrl);
            record.setIndexedAt(now);
            productVectorRepository.save(record);
        }

        log.info("[ImageSearch] Batch indexed {} products", products.size());
        return products.size();
    }

    /**
     * Xóa index của sản phẩm khỏi AI service (fire-and-forget, không block CRUD).
     * Nếu thành công → xóa bản ghi trong product_vectors.
     */
    @Async
    public void deleteIndex(Long productId) {
        try {
            restTemplate.delete(baseUrl + "/index/" + productId);
            productVectorRepository.deleteByProductId(productId);
            log.info("[ImageSearch] Deleted index for product {}", productId);
        } catch (Exception e) {
            log.warn("[ImageSearch] Failed to delete index for product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Tìm kiếm sản phẩm bằng hình ảnh, trả về danh sách kết quả có điểm tương đồng.
     */
    public List<ImageSearchResult> searchByImage(MultipartFile imageFile) {
        try {
            byte[] bytes = imageFile.getBytes();
            String filename = imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "image.jpg";

            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", fileResource);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<List<ImageSearchResult>> response = restTemplate.exchange(
                    baseUrl + "/search",
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("[ImageSearch] Search failed: {}", e.getMessage());
            throw new RuntimeException("Dịch vụ tìm kiếm bằng ảnh hiện không khả dụng. Vui lòng thử lại sau.");
        }
    }
}
