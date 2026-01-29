package com.zone.agri.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.zone.agri.common.CommonProperties;
import com.zone.agri.dto.file.FileControlDetailDto;
import com.zone.agri.dto.file.FileControlDto;
import com.zone.agri.entity.FileControl;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.dto.user.UserDetail;
import com.zone.agri.repository.FileControlRepository;
import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.common.AuthUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileControlApiImplTest {

  @Mock
  private FileControlRepository fileControlRepository;

  @Mock
  private S3Client s3Client;

  @Mock
  private CommonProperties commonProperties;

  private FileControlApiImpl fileControlApi;

  private MockedStatic<AuthUtils> authUtilsMock;
  private Path tempFile;

  @BeforeEach
  void setUp() throws IOException {
    // Mock static utils
    authUtilsMock = mockStatic(AuthUtils.class);

    // Mock AuthUtils to provide a user detail
    UserDetail userDetail = UserDetail.builder().id(1L).build();
    authUtilsMock.when(AuthUtils::getUserDetail).thenReturn(userDetail);

    // Create a temporary file for upload tests
    Path tempDir = Files.createTempDirectory("test-uploads");
    tempFile = Files.createTempFile(tempDir, "test", ".txt");
    when(commonProperties.getTmpDir()).thenReturn(tempDir.toString() + "/");

    // Initialize the API implementation with mocked dependencies
    fileControlApi = new FileControlApiImpl(
        fileControlRepository,
        "test-bucket",
        "uploads",
        "us-east-1",
        s3Client,
        commonProperties
    );
  }

  @AfterEach
  void tearDown() throws IOException {
    authUtilsMock.close();
    Files.deleteIfExists(tempFile);
    Files.deleteIfExists(tempFile.getParent());
  }

  @Test
  void saveFile_shouldThrowBadRequestException_whenObjectIdIsMissing() {
    FileControlDto dto = new FileControlDto();
    dto.setFileControlDetails(Collections.singletonList(new FileControlDetailDto()));

    assertThrows(BadRequestException.class, () -> fileControlApi.saveFile(dto));
  }

  @Test
  void saveFile_shouldSaveNewFile_whenTmpPathIsValid() {
    FileControlDetailDto detail = new FileControlDetailDto();
    detail.setTmpPath(tempFile.getFileName().toString());
    detail.setFileName("test.txt");

    FileControlDto dto = new FileControlDto();
    dto.setObjectId("testObject");
    dto.setFileControlDetails(Collections.singletonList(detail));

    FileControl savedEntity = new FileControl();
    savedEntity.setFileControlId(1L);
    savedEntity.setFileId("uploads/testObject/0000000001/00001/mocked.txt");

    when(fileControlRepository.findMaxDetailNoByFileControlId(any())).thenReturn(0L);
    when(fileControlRepository.save(any(FileControl.class))).thenReturn(savedEntity);

    Long fileControlId = fileControlApi.saveFile(dto);

    assertEquals(1L, fileControlId);
    verify(fileControlRepository, times(1)).save(any(FileControl.class));
    verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void saveFile_shouldMarkFileForDeletion_whenDeleteFlagIsSet() {
    FileControlDetailDto detail = new FileControlDetailDto();
    detail.setDetailNo(1L);
    detail.setDeleteFlag("1");

    FileControlDto dto = new FileControlDto();
    dto.setFileControlId(1L);
    dto.setObjectId("testObject");
    dto.setFileControlDetails(Collections.singletonList(detail));

    FileControl existingEntity = new FileControl();
    existingEntity.setFileControlId(1L);
    existingEntity.setDetailNo(1L);
    existingEntity.setDeleteFlag("0");

    when(fileControlRepository.findByFileControlId(1L)).thenReturn(
        Collections.singletonList(existingEntity));

    fileControlApi.saveFile(dto);

    verify(fileControlRepository, times(1)).save(existingEntity);
    assertEquals("1", existingEntity.getDeleteFlag());
  }
}