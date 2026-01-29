package com.zone.agri.dto.file;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FileControlDto {
    private Long fileControlId;
    private String objectId;
    private List<FileControlDetailDto> fileControlDetails = new ArrayList<>();
}
