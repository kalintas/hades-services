package com.hades.services.service.sagemaker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.service.sagemaker.dto.SageMakerVlmRequest;
import com.hades.services.service.sagemaker.dto.SageMakerVlmResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * Calls the Colab/ngrok endpoint for VLM inference.
 */
@Service
public class VlmInferenceService extends AbstractHttpInferenceService<SageMakerVlmRequest, SageMakerVlmResponse> {

    public VlmInferenceService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            InferenceEndpointProvider endpointProvider) {
        super(restTemplate, objectMapper, endpointProvider);
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
            String rawJsonResponse = invokeHttpEndpoint(jsonPayload);

            return objectMapper.readValue(rawJsonResponse, SageMakerVlmResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process VLM inference via Colab", e);
        }
    }
}
