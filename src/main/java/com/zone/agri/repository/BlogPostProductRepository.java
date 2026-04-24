package com.zone.agri.repository;

import com.zone.agri.entity.BlogPostProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlogPostProductRepository extends JpaRepository<BlogPostProduct, Long> {
    void deleteAllByPostId(Long postId);
}
