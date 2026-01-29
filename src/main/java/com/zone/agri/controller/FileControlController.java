package com.zone.agri.controller;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.CommonProperties;
import com.zone.agri.dto.file.FileControlDetailDto;
import com.zone.agri.dto.file.FileControlDto;
import com.zone.agri.dto.file.S3FileDownloadDto;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.service.FileControlService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileControlController {

  private final FileControlService fileControlService;
  private final CommonProperties commonProperties;

  private static final Pattern tmpPathPattern = Pattern.compile("^\\d{8}/(.+?)/\\d{17}/.+$");


  @PostMapping("/tmpUpload")
  public FileControlDetailDto tmpFileUpload(@RequestParam("file") MultipartFile file)
      throws IOException {
    return fileControlService.tmpFileUpload(file);
  }

  @PostMapping("/download")
  public void fileDownload(@RequestBody FileControlDto dto, HttpServletResponse response)
      throws IOException {
    if (dto == null || dto.getFileControlDetails() == null || dto.getFileControlDetails()
        .isEmpty()) {
      throw new BadRequestException("Invalid request: file details are missing.");
    }

    if (dto.getFileControlDetails().size() == 1) {
      FileControlDetailDto detail = dto.getFileControlDetails().get(0);

      if (StringUtils.isNoneEmpty(detail.getTmpPath(), detail.getFileName())) {
        Matcher matcher = tmpPathPattern.matcher(detail.getTmpPath());
        if (matcher.find() && StringUtils.equals(matcher.group(1),
            AuthUtils.getUserDetail().getId().toString())) {
          // Retrieve from temporary directory
          output(commonProperties.getTmpDir() + detail.getTmpPath(), detail.getFileName(),
              response);
          return;
        }
      } else if (dto.getFileControlId() != null && detail.getDetailNo() != null) {
        // Retrieve from S3
        S3FileDownloadDto s3FileDownloadDto = fileControlService.getS3Object(dto.getFileControlId(),
            detail.getDetailNo());

        if (s3FileDownloadDto == null || s3FileDownloadDto.getFile() == null) {
          throw new BadRequestException("S3 file not found for provided IDs.");
        }

        // Use the file from S3FileDownloadDto
        try (InputStream is = new FileInputStream(s3FileDownloadDto.getFile())) {
          output(is, s3FileDownloadDto.getFileName(), response);
        }
        return;
      }
    }
    throw new BadRequestException("Invalid file request: file not found or missing parameters.");
  }

  // Output method for local files
  private void output(String filePath, String fileName, HttpServletResponse response)
      throws IOException {
    File file = new File(filePath);
    if (!file.exists()) {
      throw new FileNotFoundException("File not found: " + filePath);
    }
    try (InputStream inputStream = new FileInputStream(file)) {
      prepareResponse(response, fileName, file.length());
      StreamUtils.copy(inputStream, response.getOutputStream());
    }
  }

  // Output method for InputStream (e.g., from S3 or other remote source)
  private void output(InputStream inputStream, String fileName, HttpServletResponse response)
      throws IOException {
    prepareResponse(response, fileName, -1);  // Use -1 if file size is unknown
    StreamUtils.copy(inputStream, response.getOutputStream());
  }

  // Helper method to set response headers
  private void prepareResponse(HttpServletResponse response, String fileName, long contentLength) {
    response.setContentType("application/octet-stream");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
    if (contentLength >= 0) {
      response.setContentLengthLong(contentLength);
    }
  }
}

