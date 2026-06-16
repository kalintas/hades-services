package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single turn in the conversation sent to the vLLM endpoint.
 *
 * {@code content} is intentionally typed as {@code Object} so it can hold:
 * <ul>
 *   <li>A plain {@code String} for text-only history turns</li>
 *   <li>A {@code List<VlmContentPart>} for the current user message when an image is attached</li>
 * </ul>
 * Jackson serialises both correctly without any extra configuration.
 */
public record VlmHistoryEntry(
        @JsonProperty("role")    String role,
        @JsonProperty("content") Object content   // String | List<VlmContentPart>
) {
    /** Text-only turn (used for history entries). */
    public static VlmHistoryEntry text(String role, String text) {
        return new VlmHistoryEntry(role, text);
    }

    /** Multimodal turn — image + text (used for the current user message). */
    public static VlmHistoryEntry multimodal(String role, List<VlmContentPart> parts) {
        return new VlmHistoryEntry(role, parts);
    }
}
