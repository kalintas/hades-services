package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SageMakerYoloResponse(
        @JsonProperty("detections") List<SageMakerYoloDetection> detections,
        @JsonProperty("image_width") Integer imageWidth,
        @JsonProperty("image_height") Integer imageHeight,
        @JsonProperty("inference_time_ms") Double inferenceTimeMs,
        @JsonProperty("annotated_image_b64") String annotatedImageB64,
        @JsonProperty("error") String error
) {}