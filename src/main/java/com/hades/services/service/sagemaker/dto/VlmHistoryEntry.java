package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single turn in the conversation history sent to the VLM endpoint.
 *
 * Matches the history entry shape expected by inference.py:
 * {"role": "user"|"assistant", "content": "..."}
 */
public record VlmHistoryEntry(
        @JsonProperty("role")    String role,
        @JsonProperty("content") String content
) {}
