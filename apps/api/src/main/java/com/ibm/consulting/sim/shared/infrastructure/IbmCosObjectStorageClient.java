package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.domain.ObjectStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Real IBM Cloud Object Storage adapter using the S3-compatible API with
 * HMAC credentials. Enabled when app.cos.mock-mode=false.
 */
@Component
@ConditionalOnProperty(name = "app.cos.mock-mode", havingValue = "false")
public class IbmCosObjectStorageClient implements ObjectStorageClient {

    private static final Logger log = LoggerFactory.getLogger(IbmCosObjectStorageClient.class);

    private final S3Client s3Client;
    private final String bucket;

    public IbmCosObjectStorageClient(
            @Value("${app.cos.endpoint}") String endpoint,
            @Value("${app.cos.access-key}") String accessKey,
            @Value("${app.cos.secret-key}") String secretKey,
            @Value("${app.cos.region}") String region,
            @Value("${app.cos.bucket}") String bucket) {
        this.bucket = bucket;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Override
    public String upload(String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
        log.info("Uploaded object to IBM COS: bucket={}, key={}", bucket, key);
        return "cos://" + bucket + "/" + key;
    }
}
