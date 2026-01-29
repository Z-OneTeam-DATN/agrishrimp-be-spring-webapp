package com.zone.agri.dto.file;

import com.zone.agri.entity.Image;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ImageDto {

  private Long id;
  private String fileName;
  private String imageUrl;

  public ImageDto(Image entity) {
    if (entity != null) {
      this.id = entity.getId();
      this.fileName = entity.getOriginalFilename();
      this.imageUrl = entity.getS3Url();
    }
  }
}
