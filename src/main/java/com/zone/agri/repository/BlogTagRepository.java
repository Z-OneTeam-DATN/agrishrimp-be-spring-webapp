package com.zone.agri.repository;

import com.zone.agri.entity.BlogTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlogTagRepository extends JpaRepository<BlogTag, Long> {
    Optional<BlogTag> findBySlug(String slug);
    Optional<BlogTag> findByName(String name);
    List<BlogTag> findAllByIdIn(List<Long> ids);
}
