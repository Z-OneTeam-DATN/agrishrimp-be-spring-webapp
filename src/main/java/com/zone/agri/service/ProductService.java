package com.zone.agri.service;

import com.zone.agri.dto.product.ProductRequest;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    // Repositories cho Thuộc tính động
    private final AttributeRepository attributeRepository;
    private final VariantAttributeRepository variantAttributeRepository;

    @Transactional
    public Product createProduct(ProductRequest request) {
        if (request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new RuntimeException("Sản phẩm phải có ít nhất 1 biến thể!");
        }

        Product product = new Product();
        mapBasicInfo(product, request);
        product.setCreatedAt(LocalDateTime.now());

        // Tự động tạo Slug
        product.setSlug(toSlug(request.getName()) + "-" + System.currentTimeMillis());

        Product savedProduct = productRepository.save(product);

        saveImages(savedProduct, request.getImages());
        saveVariants(savedProduct, request.getVariants());

        return savedProduct;
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

        if (request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new RuntimeException("Sản phẩm phải có ít nhất 1 biến thể!");
        }

        mapBasicInfo(product, request);
        Product updatedProduct = productRepository.save(product);

        // Xóa dữ liệu con cũ theo thứ tự (Cháu -> Con -> Cha) để tránh lỗi khóa ngoại
        productRepository.deleteVariantAttributesByProduct(updatedProduct);
        productRepository.deleteVariantsByProduct(updatedProduct);
        productRepository.deleteImagesByProduct(updatedProduct);

        // Lưu dữ liệu mới
        saveImages(updatedProduct, request.getImages());
        saveVariants(updatedProduct, request.getVariants());

        return updatedProduct;
    }

    public List<Product> getAll() {
        return productRepository.findAll();
    }

    public Product getById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));
    }

    @Transactional
    public void delete(Long id) {
        Product product = getById(id);

        // Xóa con trước, xóa cha sau
        productRepository.deleteVariantAttributesByProduct(product);
        productRepository.deleteVariantsByProduct(product);
        productRepository.deleteImagesByProduct(product);

        productRepository.delete(product);
    }

    // ================= HELPER METHODS =================

    private void mapBasicInfo(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setOrigin(request.getOrigin());
        product.setBaseSku(request.getBaseSku());
        product.setDescription(request.getDescription());
        product.setStatus("active".equalsIgnoreCase(request.getStatus()) ? ProductStatus.PUBLISHED : ProductStatus.HIDDEN);

        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId()).ifPresent(product::setCategory);
        }

        if (request.getBrand() != null && !request.getBrand().trim().isEmpty()) {
            String brandName = request.getBrand().trim();
            Brand brand = productRepository.findBrandByName(brandName)
                    .orElseGet(() -> brandRepository.save(Brand.builder()
                            .name(brandName)
                            .status(BrandStatus.ACTIVE).build()));
            product.setBrand(brand);
        }
    }

    private void saveImages(Product product, List<String> imageUrls) {
        if (imageUrls != null) {
            imageUrls.forEach(url -> imageRepository.save(ProductImage.builder()
                    .imageUrl(url).product(product).build()));
        }
    }

    private void saveVariants(Product product, List<ProductRequest.VariantDto> variants) {
        for (ProductRequest.VariantDto vDto : variants) {

            // 1. Lưu Biến thể (Variant)
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(vDto.getSku())
                    .barcode(vDto.getBarcode())
                    .price(vDto.getPrice())
                    .importPrice(vDto.getCostPrice())
                    .wholesalePrice(vDto.getWholesalePrice())
                    .quantity(vDto.getInitialStock())
                    .weightValue(vDto.getNetWeight())
                    .netWeightUnit(vDto.getNetWeightUnit())
                    .shippingWeight(vDto.getShippingWeight())
                    .imageUrl(vDto.getImage())
                    .status(VariantStatus.ACTIVE)
                    .build();

            ProductVariant savedVariant = variantRepository.save(variant);

            // 2. Lưu Thuộc tính động (Dynamic Attributes)
            if (vDto.getAttributes() != null && !vDto.getAttributes().isEmpty()) {
                for (ProductRequest.AttributeDto attrDto : vDto.getAttributes()) {
                    if (attrDto.getName() == null || attrDto.getName().trim().isEmpty()) continue;

                    String attrName = attrDto.getName().trim();

                    // Tìm thuộc tính trong Từ điển, chưa có thì tự động sinh mới
                    Attribute attribute = attributeRepository.findByNameIgnoreCase(attrName)
                            .orElseGet(() -> attributeRepository.save(Attribute.builder()
                                    .name(attrName)
                                    .code(toSlug(attrName)) // Tự sinh code từ tên
                                    .status(AttributeStatus.ACTIVE)
                                    .build()));

                    // Liên kết Biến thể - Thuộc tính - Giá trị
                    VariantAttribute variantAttribute = VariantAttribute.builder()
                            .variant(savedVariant)
                            .attribute(attribute)
                            .value(attrDto.getValue())
                            .build();

                    variantAttributeRepository.save(variantAttribute);
                }
            }
        }
    }

    private String toSlug(String input) {
        if (input == null) return "";
        String nowhitespace = Pattern.compile("\\s+").matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = Pattern.compile("[^\\w-]").matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}