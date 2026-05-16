package com.hades.services.service.sagemaker.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Domain-level parameters for YOLO inference.
 * This class is decoupled from AWS-specific requirements.
 */
@Getter
@Setter
public class YoloParams {

    private Double confidence;
    private Double iou;
    private Boolean includeAnnotatedImage;

    // Default Constructor
    public YoloParams() {
        this.confidence = 0.25;
        this.iou = 0.45;
        this.includeAnnotatedImage = false;
    }

    // All-args Constructor
    public YoloParams(Double confidence, Double iou, Boolean includeAnnotatedImage) {
        this.confidence = confidence;
        this.iou = iou;
        this.includeAnnotatedImage = includeAnnotatedImage;
    }
}