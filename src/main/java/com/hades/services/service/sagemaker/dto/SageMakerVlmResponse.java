package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload from the hades-qwen3.5-vlm SageMaker endpoint.
 *
 * {
 *   "response":          "...",
 *   "thinking":          null,
 *   "inference_time_ms": 1234.5,
 *   "error":             null
 * }
 */
public record SageMakerVlmResponse(
        @JsonProperty("response")          String  response,
        @JsonProperty("thinking")          String  thinking,
        @JsonProperty("inference_time_ms") Double  inferenceTimeMs,
        @JsonProperty("error")             String  error
) {}
