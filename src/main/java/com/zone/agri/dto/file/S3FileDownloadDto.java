package com.zone.agri.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.File;

@Data
@AllArgsConstructor
public class S3FileDownloadDto {
    private File file;
    private String fileName;
}

