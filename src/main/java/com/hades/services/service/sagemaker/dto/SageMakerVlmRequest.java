package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Request payload for the OpenAI-compatible vLLM endpoint.
 *
 * POST /v1/chat/completions
 * {
 *   "model": "hades_adapter",
 *   "messages": [
 *     {"role": "user", "content": "previous text turn"},
 *     {"role": "assistant", "content": "..."},
 *     {
 *       "role": "user",
 *       "content": [
 *         {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}},
 *         {"type": "text", "text": "<current prompt>"}
 *       ]
 *     }
 *   ],
 *   "temperature": 0.1,
 *   "top_p": 0.9,
 *   "max_tokens": 2048,
 *   "extra_body": {
 *     "top_k": -1,
 *     "chat_template_kwargs": {"enable_thinking": false}
 *   }
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SageMakerVlmRequest(
        @JsonProperty("model")       String                model,
        @JsonProperty("messages")    List<VlmHistoryEntry> messages,
        @JsonProperty("temperature") Double                temperature,
        @JsonProperty("top_p")       Double                topP,
        @JsonProperty("max_tokens")  int                   maxTokens,
        @JsonProperty("extra_body")  Map<String, Object>   extraBody
) {
    /**
     * Convenience factory — text-only prompt, no history, thinking disabled, 512 token limit.
     */
    public static SageMakerVlmRequest of(String prompt) {
        return new SageMakerVlmRequest(
                "hades_adapter",
                List.of(VlmHistoryEntry.text("user", prompt)),
                0.1,
                0.9,
                512,
                Map.of("top_k", -1, "chat_template_kwargs", Map.of("enable_thinking", false))
        );
    }
}
