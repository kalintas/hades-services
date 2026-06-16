package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single part inside a multimodal message content array.
 *
 * Text part:  {"type": "text",      "text": "..."}
 * Image part: {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VlmContentPart(
        @JsonProperty("type")      String   type,
        @JsonProperty("text")      String   text,
        @JsonProperty("image_url") ImageUrl imageUrl
) {
    public record ImageUrl(@JsonProperty("url") String url) {}

    /** Factory for a plain text part. */
    public static VlmContentPart text(String text) {
        return new VlmContentPart("text", text, null);
    }

    /**
     * Factory for a base64-encoded image part.
     *
     * @param base64   base64-encoded image bytes (no prefix)
     * @param mimeType e.g. "image/jpeg" or "image/png"
     */
    public static VlmContentPart imageBase64(String base64, String mimeType) {
        return new VlmContentPart("image_url", null,
                new ImageUrl("data:" + mimeType + ";base64," + base64));
    }
}
