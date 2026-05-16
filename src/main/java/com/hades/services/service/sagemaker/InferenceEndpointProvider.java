package com.hades.services.service.sagemaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;

@Component
public class InferenceEndpointProvider {

    private static final Logger logger = LoggerFactory.getLogger(InferenceEndpointProvider.class);

    private final S3Client s3Client;
    private final String bucketName = "hades-general-bucket";
    private final String vlmKey = "vlm-endpoint";

    private String cachedVlmEndpoint;

    public InferenceEndpointProvider(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String getVlmEndpoint() {
        if (cachedVlmEndpoint != null) {
            return cachedVlmEndpoint;
        }

        try {
            logger.info("Fetching VLM endpoint from S3: s3://{}/{}", bucketName, vlmKey);
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(vlmKey)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            cachedVlmEndpoint = new String(objectBytes.asByteArray(), StandardCharsets.UTF_8).trim();
            
            logger.info("Successfully loaded VLM endpoint: {}", cachedVlmEndpoint);
            return cachedVlmEndpoint;
        } catch (Exception e) {
            logger.error("Failed to load VLM endpoint from S3.", e);
            throw new RuntimeException("Could not resolve VLM inference endpoint from S3", e);
        }
    }

    public void refreshVlm() {
        cachedVlmEndpoint = null;
        getVlmEndpoint();
    }
}
