package com.zone.agri.service;

import com.zone.agri.api.FileControlApi;
import com.zone.agri.common.CommonProperties;
import com.zone.agri.dto.file.FileControlDetailDto;
import com.zone.agri.dto.file.S3FileDownloadDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RequiredArgsConstructor
@Service
public class FileControlService {

    private final FileControlApi fileControlApi;
    private final CommonProperties commonProperties;

    public FileControlDetailDto tmpFileUpload(MultipartFile file) throws IOException {
        FileControlDetailDto dto = new FileControlDetailDto();
        // 元ファイル名を設定
        dto.setFileName(file.getOriginalFilename());
        String tmpDir = commonProperties.createUserTmpDir();
        String extension = FilenameUtils.getExtension(dto.getFileName());
        Path tmpPath = Files.createTempFile(Paths.get(tmpDir), "tmpfile",
                (StringUtils.isEmpty(extension) ? "" : "." + extension));

        dto.setTmpPath(tmpPath.toString().replace("\\", "/").replace(commonProperties.getTmpDir(), ""));
        try (BufferedInputStream bis = new BufferedInputStream(file.getInputStream());
             BufferedOutputStream bos =
                     new BufferedOutputStream(new FileOutputStream(tmpPath.toString()))) {
            byte[] data = new byte[1024];
            int len;
            while ((len = bis.read(data)) != -1) {
                bos.write(data, 0, len);
            }
            bos.flush();
        }
        return dto;
    }

    public S3FileDownloadDto getS3Object(Long fileControlId, Long detailNo) {
        return fileControlApi.getS3Object(fileControlId, detailNo);
    }
}

