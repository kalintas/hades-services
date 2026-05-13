package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SageMakerYoloDetection(
        @JsonProperty("class_id") Integer classId,
        @JsonProperty("class_name") String className,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("bbox") List<Integer> bbox,
        // Note: If you don't need to parse the RLE mask in Java, you can omit it,
        // or type it as com.fasterxml.jackson.databind.JsonNode to just pass it through.
        @JsonProperty("mask_rle") Object maskRle
) {}