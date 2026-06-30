package com.zone.agri.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.request.blog.BlogCategoryRequest;
import com.zone.agri.dto.request.blog.BlogPostRequest;
import com.zone.agri.dto.request.blog.BlogTagRequest;
import com.zone.agri.dto.response.blog.BlogCategoryResponse;
import com.zone.agri.dto.response.blog.BlogPostResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.BlogPostStatus;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogService {

    private final BlogPostRepository postRepo;
    private final BlogCategoryRepository categoryRepo;
    private final BlogTagRepository tagRepo;
    private final BlogPostProductRepository postProductRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;

    // ─── CATEGORY ──────────────────────────────────────────────────────────────

    public List<BlogCategoryResponse> getAllCategories() {
        return categoryRepo.findAll().stream().map(c -> BlogCategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .description(c.getDescription())
                .postCount(c.getPosts() == null ? 0L : (long) c.getPosts().size())
                .build()).collect(Collectors.toList());
    }

    public List<BlogPostResponse.TagInfo> getAllTags() {
        return tagRepo.findAll().stream()
                .sorted(Comparator.comparing(BlogTag::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toTagInfo)
                .collect(Collectors.toList());
    }

    @Transactional
    public BlogPostResponse.TagInfo createTag(BlogTagRequest req) {
        String normalizedName = normalizeTagName(req.getName());
        if (normalizedName.isBlank()) {
            throw new RuntimeException("Tên tag không được để trống");
        }

        Optional<BlogTag> existingByName = tagRepo.findByNameIgnoreCase(normalizedName);
        if (existingByName.isPresent()) {
            return toTagInfo(existingByName.get());
        }

        String slug = resolveTagSlug(req.getSlug(), normalizedName);

        Optional<BlogTag> existingBySlug = tagRepo.findBySlugIgnoreCase(slug);
        if (existingBySlug.isPresent()) {
            return toTagInfo(existingBySlug.get());
        }

        BlogTag tag = BlogTag.builder()
                .name(normalizedName)
                .slug(slug)
                .build();
        return toTagInfo(tagRepo.save(tag));
    }

    @Transactional
    public BlogCategoryResponse createCategory(BlogCategoryRequest req) {
        BlogCategory cat = BlogCategory.builder()
                .name(req.getName())
                .slug(req.getSlug() != null ? req.getSlug() : toSlug(req.getName()))
                .description(req.getDescription())
                .build();
        return toCategoryResponse(categoryRepo.save(cat));
    }

    @Transactional
    public BlogCategoryResponse updateCategory(Long id, BlogCategoryRequest req) {
        BlogCategory cat = categoryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Danh mục không tồn tại: " + id));
        if (req.getName() != null) cat.setName(req.getName());
        if (req.getSlug() != null) cat.setSlug(req.getSlug());
        if (req.getDescription() != null) cat.setDescription(req.getDescription());
        return toCategoryResponse(categoryRepo.save(cat));
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepo.deleteById(id);
    }

    // ─── POST ──────────────────────────────────────────────────────────────────

    public Page<BlogPostResponse> getAll(String keyword, String status, Long categoryId, Pageable pageable) {
        BlogPostStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = BlogPostStatus.valueOf(status.toUpperCase()); } catch (Exception ignored) {}
        }
        return postRepo.findAllFiltered(
                keyword != null && keyword.isBlank() ? null : keyword,
                statusEnum, categoryId, pageable
        ).map(p -> toResponse(p, false));
    }

    public Page<BlogPostResponse> getPublished(String keyword, Long categoryId, Pageable pageable) {
        return postRepo.findPublished(
                BlogPostStatus.PUBLISHED,
                keyword != null && keyword.isBlank() ? null : keyword,
                categoryId, pageable
        ).map(p -> toResponse(p, false));
    }

    @Transactional
    public BlogPostResponse getBySlug(String slug, boolean incrementView) {
        BlogPost post = postRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + slug));
        if (incrementView) {
            postRepo.incrementViewCount(post.getId());
            post.setViewCount((post.getViewCount() == null ? 0L : post.getViewCount()) + 1);
        }
        return toResponse(post, true);
    }

    public BlogPostResponse getById(Long id) {
        BlogPost post = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + id));
        return toResponse(post, true);
    }

    @Transactional
    public BlogPostResponse create(BlogPostRequest req, MultipartFile thumbnail) {
        String slug = resolveSlug(req.getSlug(), req.getTitle(), null);
        BlogPost post = BlogPost.builder()
                .title(req.getTitle())
                .slug(slug)
                .excerpt(req.getExcerpt())
                .content(req.getContent())
                .status(parseStatus(req.getStatus()))
                .build();

        if (post.getStatus() == BlogPostStatus.PUBLISHED) post.setPublishedAt(LocalDateTime.now());

        applyAuthor(post);
        applyCategory(post, req.getCategoryId());
        applyTags(post, req.getTagIds());
        applyThumbnail(post, req, thumbnail);

        BlogPost saved = postRepo.save(post);
        applyRelatedProducts(saved, req.getProductIds());
        return toResponse(saved, true);
    }

    @Transactional
    public BlogPostResponse update(Long id, BlogPostRequest req, MultipartFile thumbnail) {
        BlogPost post = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + id));

        if (req.getTitle() != null) post.setTitle(req.getTitle());
        if (req.getExcerpt() != null) post.setExcerpt(req.getExcerpt());
        if (req.getContent() != null) post.setContent(req.getContent());

        // Slug: chỉ đổi khi FE gửi slug mới khác slug cũ
        if (req.getSlug() != null && !req.getSlug().equals(post.getSlug())) {
            post.setSlug(resolveSlug(req.getSlug(), req.getTitle(), post.getId()));
        }

        BlogPostStatus newStatus = parseStatus(req.getStatus());
        if (newStatus != post.getStatus()) {
            post.setStatus(newStatus);
            if (newStatus == BlogPostStatus.PUBLISHED && post.getPublishedAt() == null)
                post.setPublishedAt(LocalDateTime.now());
        }

        applyCategory(post, req.getCategoryId());
        applyTags(post, req.getTagIds());
        applyThumbnail(post, req, thumbnail);

        BlogPost saved = postRepo.save(post);

        if (req.getProductIds() != null) {
            postProductRepo.deleteAllByPostId(saved.getId());
            applyRelatedProducts(saved, req.getProductIds());
        }

        return toResponse(saved, true);
    }

    @Transactional
    public void changeStatus(Long id, String status) {
        BlogPost post = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + id));
        BlogPostStatus newStatus = parseStatus(status);
        post.setStatus(newStatus);
        if (newStatus == BlogPostStatus.PUBLISHED && post.getPublishedAt() == null)
            post.setPublishedAt(LocalDateTime.now());
        postRepo.save(post);
    }

    @Transactional
    public void delete(Long id) {
        BlogPost post = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + id));
        if (post.getThumbnailPublicId() != null) cloudinaryService.delete(post.getThumbnailPublicId());
        postRepo.delete(post);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void applyAuthor(BlogPost post) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            userRepo.findByEmail(email).ifPresent(post::setAuthor);
        } catch (Exception ignored) {}
    }

    private void applyCategory(BlogPost post, Long categoryId) {
        if (categoryId == null) { post.setCategory(null); return; }
        categoryRepo.findById(categoryId).ifPresent(post::setCategory);
    }

    private void applyTags(BlogPost post, List<Long> tagIds) {
        if (tagIds == null) return;
        Set<BlogTag> tags = new HashSet<>(tagRepo.findAllByIdIn(tagIds));
        post.setTags(tags);
    }

    private void applyThumbnail(BlogPost post, BlogPostRequest req, MultipartFile thumbnail) {
        if (thumbnail != null && !thumbnail.isEmpty()) {
            if (post.getThumbnailPublicId() != null) cloudinaryService.delete(post.getThumbnailPublicId());
            CloudinaryService.UploadResult result = cloudinaryService.upload(thumbnail, "blog");
            post.setThumbnailUrl(result.secureUrl());
            post.setThumbnailPublicId(result.publicId());
        } else if (req.getThumbnailUrl() != null) {
            post.setThumbnailUrl(req.getThumbnailUrl());
            post.setThumbnailPublicId(req.getThumbnailPublicId());
        }
    }

    private void applyRelatedProducts(BlogPost post, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return;
        List<BlogPostProduct> items = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Long pid = productIds.get(i);
            productRepo.findById(pid).ifPresent(product -> {
                items.add(BlogPostProduct.builder()
                        .post(post)
                        .product(product)
                        .displayOrder(items.size())
                        .build());
            });
        }
        postProductRepo.saveAll(items);
        post.getRelatedProducts().clear();
        post.getRelatedProducts().addAll(items);
    }

    private String resolveSlug(String requestedSlug, String title, Long excludeId) {
        String base = (requestedSlug != null && !requestedSlug.isBlank()) ? requestedSlug : toSlug(title);
        String slug = base;
        int i = 2;
        while (postRepo.existsBySlug(slug)) {
            // nếu đang update và slug thuộc về chính bài này → OK
            if (excludeId != null) {
                Optional<BlogPost> existing = postRepo.findBySlug(slug);
                if (existing.isPresent() && existing.get().getId().equals(excludeId)) break;
            }
            slug = base + "-" + i++;
        }
        return slug;
    }

    private String resolveTagSlug(String requestedSlug, String name) {
        String base = (requestedSlug != null && !requestedSlug.isBlank()) ? toSlug(requestedSlug) : toSlug(name);
        if (base.isBlank()) {
            base = "tag-" + System.currentTimeMillis();
        }

        String slug = base;
        int i = 2;
        while (tagRepo.findBySlugIgnoreCase(slug).isPresent()) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    private BlogPostStatus parseStatus(String s) {
        if (s == null) return BlogPostStatus.DRAFT;
        try { return BlogPostStatus.valueOf(s.toUpperCase()); }
        catch (Exception e) { return BlogPostStatus.DRAFT; }
    }

    private String normalizeTagName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String toSlug(String text) {
        if (text == null) return "bai-viet-" + System.currentTimeMillis();
        return text.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("[đ]", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private BlogCategoryResponse toCategoryResponse(BlogCategory c) {
        return BlogCategoryResponse.builder()
                .id(c.getId()).name(c.getName()).slug(c.getSlug()).description(c.getDescription())
                .postCount(c.getPosts() == null ? 0L : (long) c.getPosts().size())
                .build();
    }

    private BlogPostResponse.TagInfo toTagInfo(BlogTag tag) {
        return BlogPostResponse.TagInfo.builder()
                .id(tag.getId())
                .name(tag.getName())
                .slug(tag.getSlug())
                .build();
    }

    private BlogPostResponse toResponse(BlogPost p, boolean includeContent) {
        // Related products
        List<BlogPostResponse.RelatedProductInfo> relatedList = new ArrayList<>();
        if (p.getRelatedProducts() != null) {
            for (BlogPostProduct bpp : p.getRelatedProducts()) {
                Product prod = bpp.getProduct();
                if (prod == null) continue;
                String imageUrl = prod.getProductImages() == null || prod.getProductImages().isEmpty()
                        ? null
                        : prod.getProductImages().iterator().next().getImageUrl();
                Double basePrice = null;
                relatedList.add(BlogPostResponse.RelatedProductInfo.builder()
                        .id(prod.getId()).name(prod.getName()).slug(prod.getSlug())
                        .imageUrl(imageUrl).basePrice(basePrice)
                        .build());
            }
        }

        return BlogPostResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .slug(p.getSlug())
                .excerpt(p.getExcerpt())
                .content(includeContent ? p.getContent() : null)
                .thumbnailUrl(p.getThumbnailUrl())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .viewCount(p.getViewCount())
                .publishedAt(p.getPublishedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .author(p.getAuthor() == null ? null : BlogPostResponse.AuthorInfo.builder()
                        .id(p.getAuthor().getId()).fullName(p.getAuthor().getFullName()).build())
                .category(p.getCategory() == null ? null : BlogPostResponse.CategoryInfo.builder()
                        .id(p.getCategory().getId()).name(p.getCategory().getName()).slug(p.getCategory().getSlug()).build())
                .tags(p.getTags() == null ? List.of() : p.getTags().stream().map(t ->
                        toTagInfo(t)
                ).collect(Collectors.toList()))
                .relatedProducts(relatedList)
                .build();
    }
}
