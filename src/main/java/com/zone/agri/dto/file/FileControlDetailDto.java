package com.zone.agri.dto.file;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileControlDetailDto {
    private Long detailNo;
    private String tmpPath;
    private String fileName;
    private String deleteFlag;

    private LocalDateTime createDatetime;

    private String createUserCode;

    private boolean fileNameDifferenceFlag;
}

