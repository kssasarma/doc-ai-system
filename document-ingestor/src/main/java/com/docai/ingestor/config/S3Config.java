package com.docai.ingestor.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3-compatible client wiring for {@link com.docai.ingestor.application.service.S3DocumentStorageService}.
 * Points at MinIO in this deployment (custom endpoint + path-style access); pointing at real AWS
 * S3 instead is just an endpoint/credential change, nothing in the code path differs.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "ingestor.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3Config {

    @Value("${ingestor.storage.s3.endpoint:}")
    private String endpoint;

    @Value("${ingestor.storage.s3.region:us-east-1}")
    private String region;

    @Value("${ingestor.storage.s3.access-key}")
    private String accessKey;

    @Value("${ingestor.storage.s3.secret-key}")
    private String secretKey;

    @Value("${ingestor.storage.s3.path-style-access:false}")
    private boolean pathStyleAccess;

    @Value("${ingestor.storage.s3.bucket}")
    private String bucket;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build());

        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build());

        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    /** MinIO doesn't auto-create buckets — make the target bucket exist before anything uploads to it. */
    @Bean
    public ApplicationRunner ensureBucketExists(S3Client s3Client) {
        return (ApplicationArguments args) -> {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created object storage bucket: {}", bucket);
            }
        };
    }
}
