package com.zone.agri.repository;

import com.zone.agri.entity.BlogComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlogCommentRepository extends JpaRepository<BlogComment, Long> {

    List<BlogComment> findByPostIdAndParentIsNullOrderByCreatedAtAscIdAsc(Long postId);
}
