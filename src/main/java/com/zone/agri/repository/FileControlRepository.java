package com.zone.agri.repository;

import com.zone.agri.entity.FileControl;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileControlRepository extends JpaRepository<FileControl, Long> {

  FileControl findByFileControlIdAndDetailNo(Long fileControlId, Long detailNo);

  List<FileControl> findByFileControlId(Long fileControlId);

  @Query(nativeQuery = true)
  Long findMaxDetailNoByFileControlId(@Param("fileControlId") Long fileControlId);
}
