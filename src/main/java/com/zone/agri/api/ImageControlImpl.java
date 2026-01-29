package com.zone.agri.api;

import com.zone.agri.common.CommonProperties;
import com.zone.agri.entity.Image;
import com.zone.agri.repository.ImageRepository;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class ImageControlImpl implements ImageControlApi {

  private static final Logger log = LoggerFactory.getLogger(ImageControlImpl.class);
  private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern(
      "yyyyMMddHHmmss");

  private final ImageRepository imageRepository;
  private final S3Client s3Client;
  private final String bucketName;
  private final String region;
  private final CommonProperties commonProperties;
  private final String s3format;

  public ImageControlImpl(ImageRepository imageRepository, S3Client s3Client,
      @Value("${cloud.s3.bucket-name:}") String bucketName,
      @Value("${cloud.s3.upload-dir:}") String uploadDir,
      @Value("${cloud.s3.region}") String region,
      CommonProperties commonProperties) {
    this.imageRepository = imageRepository;
    this.bucketName = StringUtils.defaultString(bucketName);
    this.s3format = StringUtils.removeEnd(uploadDir, "/") + "/%s/%010d/%s/%s";
    this.region = Objects.requireNonNull(region, "region");
    this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
    this.commonProperties = Objects.requireNonNull(commonProperties, "commonProperties");
  }


  @Override
  public Image uploadImage(MultipartFile file) {
    String s3Key = UUID.randomUUID() + "-" + file.getOriginalFilename();
    uploadToS3(s3Key, file);
    Image image = new Image();
    image.setS3Key(s3Key);
    image.setS3Url(getImageUrl(s3Key));
    image.setOriginalFilename(file.getOriginalFilename());
    image.setContentType(file.getContentType());
    image.setFileSize(file.getSize());
    imageRepository.save(image);
    return image;
  }

  @Override
  public void deleteImage(String key) {
    DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();
    s3Client.deleteObject(deleteObjectRequest);
  }

  @Override
  public String getImageUrl(String key) {
    GetUrlRequest request = GetUrlRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();
    return s3Client.utilities().getUrl(request).toExternalForm();
  }

  @Override
  public Optional<Image> getImage(Long id) {
    return imageRepository.findById(id);
  }

  private void uploadToS3(String key, MultipartFile file) throws S3Exception {
    try {
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(this.bucketName)
          .key(key)
          .contentType(file.getContentType())
          .build();
      s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
      log.debug("Uploaded to S3: s3://{}/{} with Content-Type: {}", bucketName, key,
          file.getContentType());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
