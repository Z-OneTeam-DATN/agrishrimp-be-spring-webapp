package com.zone.agri.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3Config {

  @Value("${cloud.aws.accessKey}")
  private String accessKey;

  @Value("${cloud.aws.secretKey}")
  private String secretKey;

  @Value("${cloud.aws.region}")
  private String region;

  @Bean
  public S3Client amazonS3() {
    if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(secretKey)) {
      return software.amazon.awssdk.services.s3.S3Client.builder()
          .region(Region.of(region.isEmpty() ? "ap-northeast-1" : region))
          .endpointOverride(java.net.URI.create("http://localhost:4566"))
          .credentialsProvider(StaticCredentialsProvider.create(
              AwsBasicCredentials.create("dummy", "dummy")))
          .serviceConfiguration(
              S3Configuration.builder().pathStyleAccessEnabled(true).build())
          .build();
    }
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
  }
}
