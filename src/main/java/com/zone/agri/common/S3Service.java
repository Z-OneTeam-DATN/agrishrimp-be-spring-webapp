package com.zone.agri.common;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@AllArgsConstructor
public class S3Service {
    private final S3Client s3Client;

    public void uploadFile(String bucketName, String key, Path filePath) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.putObject(putObjectRequest, filePath);
    }

    // Hàm tải file xuống từ S3 và lưu vào thư mục local
    public void downloadFile(String bucketName, String key, String downloadFilePath) {
        Path path = Paths.get(downloadFilePath);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.getObject(getObjectRequest, path);
    }
}
