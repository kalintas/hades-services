package com.hades.services.service;

import com.hades.services.model.PresignedUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;

@Service
public class AwsFileService {

    private final String bucketName;
    private final String contentDomain;
    private final Duration URL_EXPIRATION = Duration.ofMinutes(30);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    public AwsFileService(
            @Value("${aws.bucket}") String bucketName,
            @Value("${hades.content.domain}") String contentDomain,
            S3Client s3Client,
            S3Presigner s3Presigner) {
        this.bucketName = bucketName;
        this.contentDomain = contentDomain;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public String generateGetPresignedUrl(String filePath) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(URL_EXPIRATION)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public PresignedUrl generatePutPresignedUrl(String filePath, int maxContentLength) {
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .contentLength((long) maxContentLength)
                .key(filePath);

        PutObjectRequest putObjectRequest = putObjectRequestBuilder.build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String url = presignedRequest.url().toString();
        String publicUrl = String.format("https://%s/%s", contentDomain, filePath);
        return new PresignedUrl(url, URL_EXPIRATION, publicUrl);
    }

    public byte[] downloadFile(String filePath) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filePath)
                    .build();

            try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest)) {
                return s3Object.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read S3 object: " + filePath, e);
        }
    }

    public void uploadFile(String filePath, byte[] content) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        PutObjectResponse response = s3Client.putObject(
                putObjectRequest,
                RequestBody.fromBytes(content));

        System.out.println("Uploaded file to S3: " + filePath + ", ETag: " + response.eTag());
    }

    public void deleteFile(String filePath) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
        System.out.println("Deleted file from S3: " + filePath);
    }
}
