package com.hades.services.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
public class AwsConfig {

    @Value("${aws.accessKey}")
    private String accessKey;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }

    /**
     * SageMaker client configured for long-running VLM inference:
     *  - 300-second socket (read) timeout  → matches botocore read_timeout=300
     *  - Retries disabled                  → matches botocore retries={'max_attempts': 0}
     *
     * Without this, the default AWS SDK timeout (~60 s) causes the backend to
     * give up mid-inference and fire duplicate requests.
     */
    @Bean
    public SageMakerRuntimeClient sageMakerRuntimeClient() {

        // 1. Increase network layer timeouts to 30 minutes to survive VLM cold starts
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .socketTimeout(Duration.ofMinutes(30))     // <-- Increased to 30 minutes
                .connectionTimeout(Duration.ofSeconds(10))
                .build();

        // 2. Increase application layer (SDK) timeouts and disable retries
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMinutes(30))        // <-- ADDED: Total request timeout
                .apiCallAttemptTimeout(Duration.ofMinutes(30)) // <-- ADDED: Single attempt timeout
                .retryPolicy(RetryPolicy.none())               // Kept: No silent retries
                .build();

        return SageMakerRuntimeClient.builder()
                .region(Region.of("us-east-1"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .httpClient(httpClient)
                .overrideConfiguration(overrideConfig)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                // Prevents crashes if Python sends extra JSON fields we didn't map in our DTOs
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
