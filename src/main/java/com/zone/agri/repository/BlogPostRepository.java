package com.zone.agri.repository;

import com.zone.agri.entity.BlogPost;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.BlogCategoryStatus;
import com.zone.agri.entity.enums.BlogPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    Optional<BlogPost> findBySlug(String slug);

    @Query("SELECT p FROM BlogPost p WHERE p.slug = :slug " +
           "AND p.status = :status " +
           "AND (p.category IS NULL OR p.category.status = :categoryStatus)")
    Optional<BlogPost> findPublishedBySlug(
        @Param("slug") String slug,
        @Param("status") BlogPostStatus status,
        @Param("categoryStatus") BlogCategoryStatus categoryStatus
    );

    boolean existsBySlug(String slug);

    @Query("SELECT p FROM BlogPost p WHERE p.status = :status " +
           "AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(COALESCE(p.excerpt, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(COALESCE(p.content, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (p.category IS NULL OR p.category.status = :categoryStatus) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId)")
    Page<BlogPost> findPublished(
        @Param("status") BlogPostStatus status,
        @Param("categoryStatus") BlogCategoryStatus categoryStatus,
        @Param("keyword") String keyword,
        @Param("categoryId") Long categoryId,
        Pageable pageable
    );

    @Query("SELECT p FROM BlogPost p WHERE " +
           "(:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(COALESCE(p.excerpt, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(COALESCE(p.content, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:authorId IS NULL OR p.author.id = :authorId)")
    Page<BlogPost> findAllFiltered(
        @Param("keyword") String keyword,
        @Param("status") BlogPostStatus status,
        @Param("categoryId") Long categoryId,
        @Param("authorId") Long authorId,
        Pageable pageable
    );

    @Query("SELECT DISTINCT p.author FROM BlogPost p WHERE p.author IS NOT NULL ORDER BY p.author.fullName ASC")
    List<User> findDistinctAuthors();

    @Modifying
    @Query("UPDATE BlogPost p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
