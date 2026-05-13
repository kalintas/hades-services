package com.hades.services.service.sagemaker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SageMakerYoloRequest(
        @JsonProperty("image_b64") String imageB64,
        @JsonProperty("conf") Double conf,
        @JsonProperty("iou") Double iou,
        @JsonProperty("include_annotated_image") Boolean includeAnnotatedImage
) {}