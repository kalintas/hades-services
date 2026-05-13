package com.hades.services.service.sagemaker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.core.dto.YoloParams;
import com.hades.services.service.sagemaker.dto.SageMakerYoloRequest;
import com.hades.services.service.sagemaker.dto.SageMakerYoloResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

import java.util.Base64;

@Service
public class YoloInferenceService extends AbstractSageMakerService<YoloParams, SageMakerYoloResponse> {

    private final String endpointName;

    public YoloInferenceService(
            ObjectMapper objectMapper,
            SageMakerRuntimeClient sageMakerClient,
            @Value("${aws.sagemaker.endpoint.yolo}") String endpointName) {
        super(objectMapper, sageMakerClient);
        this.endpointName = endpointName;
    }

    @Override
    public SageMakerYoloResponse infer(byte[] imageBytes, YoloParams params) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 1. Create your strongly-typed Request DTO
            SageMakerYoloRequest requestPayload = new SageMakerYoloRequest(
                    base64Image,
                    params.getConfidence(),
                    params.getIou(),
                    params.getIncludeAnnotatedImage());

            // 2. Serialize to JSON string for AWS
            String jsonPayload = objectMapper.writeValueAsString(requestPayload);

            // 3. Send to AWS
            String rawJsonResponse = invokeAwsEndpoint(endpointName, jsonPayload);

            // 4. Deserialize directly into your strong Response DTO
            return objectMapper.readValue(rawJsonResponse, SageMakerYoloResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process YOLO inference", e);
        }
    }
}