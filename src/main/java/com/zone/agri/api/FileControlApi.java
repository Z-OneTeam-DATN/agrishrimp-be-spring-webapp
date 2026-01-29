package com.zone.agri.api;

import com.zone.agri.dto.file.FileControlDto;
import com.zone.agri.dto.file.S3FileDownloadDto;
import java.util.List;
import reactor.util.function.Tuple2;

public interface FileControlApi {

  Long saveFile(FileControlDto fileControlDto);

  Tuple2<Long, List<String>> saveImage(FileControlDto fileControlDto);

  FileControlDto getFileControlDto(Long fileControlId);

  S3FileDownloadDto getS3Object(Long fileControlId, Long detailNo);
}
