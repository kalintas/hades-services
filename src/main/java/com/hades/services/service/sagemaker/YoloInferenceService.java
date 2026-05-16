package com.hades.services.service.sagemaker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.service.sagemaker.dto.YoloParams;
import com.hades.services.service.sagemaker.dto.SageMakerYoloRequest;
import com.hades.services.service.sagemaker.dto.SageMakerYoloResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

import java.util.Base64;

/**
 * Reverted to SageMaker as per user request.
 */
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

            SageMakerYoloRequest requestPayload = new SageMakerYoloRequest(
                    base64Image,
                    params.getConfidence(),
                    params.getIou(),
                    params.getIncludeAnnotatedImage());

            String jsonPayload = objectMapper.writeValueAsString(requestPayload);
            String rawJsonResponse = invokeAwsEndpoint(endpointName, jsonPayload);

            return objectMapper.readValue(rawJsonResponse, SageMakerYoloResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process YOLO inference via SageMaker", e);
        }
    }
}