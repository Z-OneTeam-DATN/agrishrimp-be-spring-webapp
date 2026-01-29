package com.zone.agri.repository;

import com.zone.agri.entity.Image;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

  Optional<Image> findByS3Key(String s3Key);
}
