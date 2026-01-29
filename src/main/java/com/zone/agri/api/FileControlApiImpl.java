package com.zone.agri.api;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.CommonProperties;
import com.zone.agri.dto.file.FileControlDetailDto;
import com.zone.agri.dto.file.FileControlDto;
import com.zone.agri.dto.file.S3FileDownloadDto;
import com.zone.agri.entity.FileControl;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.FileControlRepository;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class FileControlApiImpl implements FileControlApi {

  private static final Logger log = LoggerFactory.getLogger(FileControlApiImpl.class);
  private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern(
      "yyyyMMddHHmmss");

  private final FileControlRepository fileControlRepository;
  private final S3Client s3Client;
  private final String bucketName;
  private final String region;
  private final CommonProperties commonProperties;
  private final String s3formatFile;
  private final String s3formatImage;

  public FileControlApiImpl(FileControlRepository fileControlRepository,
      @Value("${cloud.s3.bucket-name:}") String bucketName,
      @Value("${cloud.s3.upload-dir:}") String uploadDir,
      @Value("${cloud.s3.region}") String region,
      S3Client s3Client,
      CommonProperties commonProperties) {
    this.fileControlRepository = Objects.requireNonNull(fileControlRepository,
        "fileControlRepository");
    this.bucketName = StringUtils.defaultString(bucketName);
    this.s3formatFile = StringUtils.removeEnd(uploadDir, "/") + "/%s/%010d/%05d/%s";
    this.s3formatImage = StringUtils.removeEnd(uploadDir, "/") + "/%s/%s/%010d/%05d/%s";
    this.region = Objects.requireNonNull(region, "region");
    this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
    this.commonProperties = Objects.requireNonNull(commonProperties, "commonProperties");
  }

  @Override
  @Transactional
  public Long saveFile(FileControlDto fileControlDto) {
    validateRequest(fileControlDto);

    final LocalDateTime now = LocalDateTime.now();
    final String timestamp = now.format(TS_FORMATTER);

    // Lấy danh sách hiện có và max detail no một lần để giảm truy vấn
    List<FileControl> currentEntities =
        fileControlRepository.findByFileControlId(fileControlDto.getFileControlId());
    Long maxDetailNo = Optional.ofNullable(
            fileControlRepository.findMaxDetailNoByFileControlId(fileControlDto.getFileControlId()))
        .orElse(0L);

    for (FileControlDetailDto detail : safeDetails(fileControlDto)) {
      if (isDelete(detail)) {
        // Đánh dấu xóa thực thể hiện có
        if (detail.getDetailNo() != null) {
          currentEntities.stream()
              .filter(e -> detail.getDetailNo().compareTo(e.getDetailNo()) == 0)
              .findFirst()
              .ifPresent(e -> {
                e.setDeleteFlag("1");
                fileControlRepository.save(e);
              });
        }
        continue;
      }

      // Thêm mới từ tmp
      if (StringUtils.isNotBlank(detail.getTmpPath())) {
        ensureHasFileName(detail);
        Path tmpFile = resolveTmpFile(detail.getTmpPath());

        maxDetailNo = maxDetailNo + 1;
        FileControl entity = buildInsertEntity(
            fileControlDto.getFileControlId(),
            maxDetailNo,
            fileControlDto.getObjectId(),
            createS3FileName(timestamp, detail),
            detail.getFileName(),
            now
        );

        // Lưu metadata trước để lấy fileControlId (nếu null)
        entity = fileControlRepository.save(entity);
        fileControlDto.setFileControlId(entity.getFileControlId());

        // Upload S3
        uploadToS3(entity.getFileId(), tmpFile);
      }
    }
    return fileControlDto.getFileControlId();
  }

  @Override
  @Transactional
  public Tuple2<Long, List<String>> saveImage(FileControlDto fileControlDto) {
    validateRequest(fileControlDto);

    final LocalDateTime now = LocalDateTime.now();
    List<FileControl> currentEntities =
        fileControlRepository.findByFileControlId(fileControlDto.getFileControlId());
    long maxDetailNo = Optional.ofNullable(
            fileControlRepository.findMaxDetailNoByFileControlId(fileControlDto.getFileControlId()))
        .orElse(0L);

    List<String> imageLinkList = new ArrayList<>();

    for (FileControlDetailDto detail : safeDetails(fileControlDto)) {
      if (isDelete(detail)) {
        if (detail.getDetailNo() != null) {
          currentEntities.stream()
              .filter(e -> detail.getDetailNo().compareTo(e.getDetailNo()) == 0)
              .findFirst()
              .ifPresent(e -> {
                e.setDeleteFlag("1");
                fileControlRepository.save(e);
              });
        }
        continue;
      }

      if (StringUtils.isNotBlank(detail.getTmpPath())) {
        ensureHasFileName(detail);
        Path tmpFile = resolveTmpFile(detail.getTmpPath());

        maxDetailNo = maxDetailNo + 1;
        FileControl entity = buildInsertImageEntity(
            fileControlDto.getFileControlId(),
            maxDetailNo,
            fileControlDto.getObjectId(),
            createS3ImageName(detail),
            sanitizeFileName(detail.getFileName()),
            now
        );

        entity = fileControlRepository.save(entity);
        fileControlDto.setFileControlId(entity.getFileControlId());

        uploadToS3(entity.getFileId(), tmpFile);
        imageLinkList.add(buildS3PublicUrl(entity.getFileId()));
      }
    }
    return Tuples.of(fileControlDto.getFileControlId(), imageLinkList);
  }

  @Override
  public FileControlDto getFileControlDto(Long fileControlId) {
    // Chưa có yêu cầu nghiệp vụ cụ thể -> giữ nguyên
    return null;
  }

  @Override
  public S3FileDownloadDto getS3Object(Long fileControlId, Long detailNo) {
    FileControl entity = fileControlRepository.findByFileControlIdAndDetailNo(fileControlId,
        detailNo);
    if (entity == null || StringUtils.equals(entity.getDeleteFlag(), "1")) {
      throw new NotFoundException(
          "File not found for fileControlId=" + fileControlId + ", detailNo=" + detailNo);
    }

    GetObjectRequest objectRequest = GetObjectRequest.builder()
        .key(entity.getFileId())
        .bucket(this.bucketName)
        .build();

    try {
      ResponseBytes<GetObjectResponse> objectBytes =
          s3Client.getObject(objectRequest, ResponseTransformer.toBytes());
      byte[] data = objectBytes.asByteArray();

      Path tmpDir = Paths.get(commonProperties.getTmpDir());
      Files.createDirectories(tmpDir);
      Path fileDownloaded = tmpDir.resolve(entity.getFileName());

      try (OutputStream os = new FileOutputStream(fileDownloaded.toFile())) {
        os.write(data);
      }
      log.info("Downloaded S3 object to tmp file: {}", fileDownloaded);
      return new S3FileDownloadDto(fileDownloaded.toFile(), entity.getFileName());
    } catch (S3Exception e) {
      log.error("S3 error when downloading key={} bucket={}: {}", entity.getFileId(), bucketName,
          e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage(), e);
      throw new NotFoundException("Cannot download file from S3");
    } catch (IOException e) {
      log.error("IO error when writing tmp file for key={}: {}", entity.getFileId(), e.getMessage(),
          e);
      throw new RuntimeException("Cannot write temporary file", e);
    }
  }

  // ---------- Helpers ----------

  private void validateRequest(FileControlDto dto) {
    if (dto == null) {
      throw new BadRequestException("Request body is required");
    }
    if (StringUtils.isBlank(dto.getObjectId())) {
      throw new BadRequestException("Object ID is required");
    }
    if (dto.getFileControlDetails() == null || dto.getFileControlDetails().isEmpty()) {
      throw new BadRequestException("At least one file detail is required");
    }
  }

  private List<FileControlDetailDto> safeDetails(FileControlDto dto) {
    return Optional.ofNullable(dto.getFileControlDetails()).orElseGet(ArrayList::new)
        .stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private boolean isDelete(FileControlDetailDto detail) {
    return StringUtils.equals(detail.getDeleteFlag(), "1");
  }

  private void ensureHasFileName(FileControlDetailDto detail) {
    if (StringUtils.isBlank(detail.getFileName())) {
      throw new BadRequestException("File name is required, detailNo=" + detail.getDetailNo());
    }
  }

  private Path resolveTmpFile(String tmpPath) {
    Path fullPath = Paths.get(commonProperties.getTmpDir() + tmpPath);
    if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
      throw new NotFoundException("Temporary file not found: " + fullPath);
    }
    return fullPath;
  }

  private void uploadToS3(String key, Path filePath) {
    try {
      String contentType = determineContentType(filePath);
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(this.bucketName)
          .key(key)
          .contentType(contentType)
          .build();
      s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath));
      log.debug("Uploaded to S3: s3://{}/{} with Content-Type: {}", bucketName, key, contentType);
    } catch (S3Exception e) {
      log.error("S3 error when uploading key={} bucket={}: {}", key, bucketName,
          e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage(), e);
      throw new RuntimeException("Failed to upload file to S3", e);
    }
  }

  private String buildS3PublicUrl(String key) {
    return String.format("https://%s.s3.%s.amazonaws.com/%s", this.bucketName, region, key);
  }

  private FileControl buildInsertEntity(Long fileControlId, Long detailNo,
      String objectId, String s3FileName, String fileName, LocalDateTime datetime) {
    FileControl entity = new FileControl();
    entity.setFileControlId(fileControlId);
    entity.setDetailNo(detailNo);
    entity.setObjectId(objectId);
    entity.setFileId(String.format(this.s3formatFile, objectId,
        Optional.ofNullable(fileControlId).orElse(0L), detailNo.intValue(), s3FileName));
    entity.setFileName(sanitizeFileName(fileName));
    entity.setDeleteFlag("0");
    return entity;
  }

  private FileControl buildInsertImageEntity(Long fileControlId, Long detailNo,
      String objectId, String s3FileName, String fileName, LocalDateTime datetime) {
    Long userId = AuthUtils.getUserDetail().getId();
    FileControl entity = new FileControl();
    entity.setFileControlId(fileControlId);
    entity.setDetailNo(detailNo);
    entity.setObjectId(objectId);
    entity.setFileId(String.format(this.s3formatImage, objectId, "u" + userId,
        Optional.ofNullable(fileControlId).orElse(0L), detailNo.intValue(), s3FileName));
    entity.setFileName(sanitizeFileName(fileName));
    entity.setDeleteFlag("0");
    return entity;
  }

  private String createS3FileName(String strDateTime, FileControlDetailDto detail) {
    String extension = FilenameUtils.getExtension(detail.getFileName());
    return strDateTime + (StringUtils.isBlank(extension) ? "" : "." + extension);
  }

  private String createS3ImageName(FileControlDetailDto detail) {
    // Ảnh giữ nguyên tên gốc
    return sanitizeFileName(detail.getFileName());
  }

  private String sanitizeFileName(String name) {
    if (name == null) {
      return null;
    }
    // Loại ký tự nguy hiểm, tránh path traversal
    String cleaned = name.replace("\\", "_").replace("/", "_");
    // Tránh tên trống sau khi làm sạch
    return StringUtils.defaultIfBlank(cleaned, "file");
  }

  private String determineContentType(Path path) {
    try {
      String contentType = Files.probeContentType(path);
      return StringUtils.defaultIfBlank(contentType, "application/octet-stream");
    } catch (IOException e) {
      log.warn("Could not determine content type for {}, defaulting to application/octet-stream",
          path, e);
      return "application/octet-stream";
    }
  }
}
