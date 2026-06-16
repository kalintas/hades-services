package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response payload from the OpenAI-compatible vLLM endpoint.
 *
 * {
 *   "id": "...",
 *   "choices": [
 *     {
 *       "index": 0,
 *       "message": {"role": "assistant", "content": "..."},
 *       "finish_reason": "stop"
 *     }
 *   ],
 *   "usage": { ... }
 * }
 */
public record SageMakerVlmResponse(
        @JsonProperty("id")      String         id,
        @JsonProperty("choices") List<Choice>   choices,
        @JsonProperty("usage")   Usage          usage
) {
    public record Choice(
            @JsonProperty("index")         int     index,
            @JsonProperty("message")       Message message,
            @JsonProperty("finish_reason") String  finishReason
    ) {}

    public record Message(
            @JsonProperty("role")              String role,
            @JsonProperty("content")           String content,
            @JsonProperty("reasoning_content") String reasoningContent
    ) {}

    public record Usage(
            @JsonProperty("prompt_tokens")     int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens")      int totalTokens
    ) {}
}
