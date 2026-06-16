package com.hades.services.service.sagemaker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.service.sagemaker.dto.SageMakerVlmRequest;
import com.hades.services.service.sagemaker.dto.SageMakerVlmResponse;
import com.hades.services.service.sagemaker.dto.VlmContentPart;
import com.hades.services.service.sagemaker.dto.VlmHistoryEntry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Calls the Colab/ngrok endpoint for VLM inference.
 * Endpoint: POST /v1/chat/completions  (OpenAI-compatible vLLM)
 */
@Service
public class VlmInferenceService extends AbstractHttpInferenceService<SageMakerVlmRequest, SageMakerVlmResponse> {

    public VlmInferenceService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            InferenceEndpointProvider endpointProvider) {
        super(restTemplate, objectMapper, endpointProvider);
    }

    @Override
    public SageMakerVlmResponse infer(byte[] imageBytes, SageMakerVlmRequest params) {
        try {
            String jsonPayload     = objectMapper.writeValueAsString(params);
            String rawJsonResponse = invokeHttpEndpoint(jsonPayload);

            return objectMapper.readValue(rawJsonResponse, SageMakerVlmResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process VLM inference via vLLM endpoint", e);
        }
    }

    /**
     * Assembles a {@link SageMakerVlmRequest} from raw parts.
     *
     * <p>History turns are mapped to plain text entries. The current user
     * message becomes either:
     * <ul>
     *   <li>A multimodal entry (image_url + text) when {@code imageBytes} is provided, or</li>
     *   <li>A plain text entry when there is no image.</li>
     * </ul>
     *
     * @param history        alternating user/assistant turns (oldest first)
     * @param prompt         current user message text
     * @param imageBytes     raw image bytes, or {@code null} / empty for text-only requests
     * @param maxTokens      token limit
     * @param enableThinking whether to enable chain-of-thought thinking
     */
    public SageMakerVlmRequest buildRequest(
            List<VlmHistoryEntry> history,
            String prompt,
            byte[] imageBytes,
            int maxTokens,
            boolean enableThinking) {

        List<VlmHistoryEntry> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }

        // Build the last user message — multimodal if an image is present
        if (imageBytes != null && imageBytes.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            List<VlmContentPart> parts = List.of(
                    VlmContentPart.imageBase64(b64, "image/jpeg"),
                    VlmContentPart.text(prompt)
            );
            messages.add(VlmHistoryEntry.multimodal("user", parts));
        } else {
            messages.add(VlmHistoryEntry.text("user", prompt));
        }

        Map<String, Object> extraBody = Map.of(
                "top_k", -1,
                "chat_template_kwargs", Map.of("enable_thinking", enableThinking)
        );

        return new SageMakerVlmRequest("hades_adapter", messages, 0.1, 0.9, maxTokens, extraBody);
    }
}
