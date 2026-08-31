package com.zone.agri.common;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    private static final int VIDEO_CHUNK_SIZE_BYTES = 6 * 1024 * 1024;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryService(cloudinary);
        ReflectionTestUtils.setField(cloudinaryService, "defaultFolder", "agrishrimp/products");
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    @Test
    void uploadUsesLargeUploadForVideos() throws Exception {
        when(uploader.uploadLarge(any(), any(Map.class), eq(VIDEO_CHUNK_SIZE_BYTES)))
            .thenReturn(Map.of(
                "secure_url", "https://cdn.example.com/video.mp4",
                "public_id", "temp/video-proof"
            ));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "proof.mp4",
            "video/mp4",
            "video-content".getBytes()
        );

        CloudinaryService.UploadResult result = cloudinaryService.upload(
            file,
            "temp",
            CloudinaryService.UploadMediaType.VIDEO
        );

        assertThat(result.secureUrl()).isEqualTo("https://cdn.example.com/video.mp4");
        assertThat(result.publicId()).isEqualTo("temp/video-proof");

        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).uploadLarge(any(), optionsCaptor.capture(), eq(VIDEO_CHUNK_SIZE_BYTES));
        verify(uploader, never()).upload(any(), any(Map.class));
        assertThat(optionsCaptor.getValue())
            .containsEntry("folder", "agrishrimp/products/temp")
            .containsEntry("resource_type", "video");
    }

    @Test
    void uploadUsesStandardUploadForImages() throws Exception {
        when(uploader.upload(any(), any(Map.class)))
            .thenReturn(Map.of(
                "secure_url", "https://cdn.example.com/image.png",
                "public_id", "temp/image-proof"
            ));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "proof.png",
            "image/png",
            "image-content".getBytes()
        );

        CloudinaryService.UploadResult result = cloudinaryService.upload(file, "temp");

        assertThat(result.secureUrl()).isEqualTo("https://cdn.example.com/image.png");
        assertThat(result.publicId()).isEqualTo("temp/image-proof");

        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(), optionsCaptor.capture());
        verify(uploader, never()).uploadLarge(any(), any(Map.class), anyInt());
        assertThat(optionsCaptor.getValue())
            .containsEntry("folder", "agrishrimp/products/temp")
            .containsEntry("resource_type", "image");
    }
}
