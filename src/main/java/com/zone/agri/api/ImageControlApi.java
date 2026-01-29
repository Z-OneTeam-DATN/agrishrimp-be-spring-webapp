package com.zone.agri.api;

import com.zone.agri.entity.Image;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

public interface ImageControlApi {

  public Image uploadImage(MultipartFile file);

  public void deleteImage(String key);

  public String getImageUrl(String key);

  public Optional<Image> getImage(Long id);
}
