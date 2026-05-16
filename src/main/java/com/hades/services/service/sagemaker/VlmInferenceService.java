package com.hades.services.service.sagemaker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.service.sagemaker.dto.SageMakerVlmRequest;
import com.hades.services.service.sagemaker.dto.SageMakerVlmResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

import java.util.Base64;

/**
 * Calls the hades-qwen3.5-vlm SageMaker endpoint.
 *
 * Endpoint name is read from {@code aws.sagemaker.endpoint.vlm} (env: AWS_SAGEMAKER_VLM_ENDPOINT).
 */
@Service
public class VlmInferenceService extends AbstractSageMakerService<SageMakerVlmRequest, SageMakerVlmResponse> {

    private final String endpointName;

    public VlmInferenceService(
            ObjectMapper objectMapper,
            SageMakerRuntimeClient sageMakerClient,
            @Value("${aws.sagemaker.endpoint.vlm}") String endpointName) {
        super(objectMapper, sageMakerClient);
        this.endpointName = endpointName;
    }

    /**
     * @param imageBytes raw image bytes (JPEG / PNG)
     * @param params     request params; the imageB64 field is ignored — imageBytes is always used
     */
    @Override
    public SageMakerVlmResponse infer(byte[] imageBytes, SageMakerVlmRequest params) {
        try {
            // imageBytes may be null for text-only messages (no image attached)
            String base64Image = (imageBytes != null && imageBytes.length > 0)
                    ? Base64.getEncoder().encodeToString(imageBytes)
                    : "";

            SageMakerVlmRequest requestPayload = new SageMakerVlmRequest(
                    base64Image,
                    params.prompt(),
                    params.history(),
                    params.maxNewTokens(),
                    params.enableThinking()
            );

            String jsonPayload     = objectMapper.writeValueAsString(requestPayload);
            String rawJsonResponse = invokeAwsEndpoint(endpointName, jsonPayload);

            return objectMapper.readValue(rawJsonResponse, SageMakerVlmResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process VLM inference", e);
        }
    }
}
