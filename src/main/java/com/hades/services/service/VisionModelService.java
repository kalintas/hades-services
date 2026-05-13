package com.hades.services.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface VisionModelService<ParameterType, ReturnType> {
    /**
     * Sends an image and parameters to a model for inference.
     */
    ReturnType infer(byte[] imageBytes, ParameterType parameters);
}