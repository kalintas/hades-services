package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Request payload for the hades-qwen3.5-vlm SageMaker endpoint.
 *
 * Matches the input_fn expected JSON:
 * {
 *   "image_b64":       "<base64 JPEG/PNG>",   // optional — null/empty for text-only
 *   "prompt":          "...",
 *   "history":         [{"role": "user", "content": "..."}, ...],
 *   "max_new_tokens":  512,
 *   "enable_thinking": false
 * }
 */
public record SageMakerVlmRequest(
        @JsonProperty("image_b64")       @JsonInclude(JsonInclude.Include.NON_EMPTY) String  imageB64,
        @JsonProperty("prompt")          String  prompt,
        @JsonProperty("history")         List<VlmHistoryEntry> history,
        @JsonProperty("max_new_tokens")  int     maxNewTokens,
        @JsonProperty("enable_thinking") boolean enableThinking
) {
    /** Convenience factory — no history, thinking disabled, 512 token limit. */
    public static SageMakerVlmRequest of(String imageB64, String prompt) {
        return new SageMakerVlmRequest(imageB64, prompt, Collections.emptyList(), 512, false);
    }
}
