package com.hades.services.service.sagemaker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.service.VisionModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

public abstract class AbstractHttpInferenceService<ParameterType, ReturnType> implements VisionModelService<ParameterType, ReturnType> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;
    protected final InferenceEndpointProvider endpointProvider;

    protected AbstractHttpInferenceService(RestTemplate restTemplate, 
                                          ObjectMapper objectMapper, 
                                          InferenceEndpointProvider endpointProvider) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.endpointProvider = endpointProvider;
    }

    /**
     * Hits the Colab/ngrok REST API.
     */
    protected String invokeHttpEndpoint(String jsonPayload) {
        String endpointUrl = endpointProvider.getVlmEndpoint();
        logger.debug("Invoking HTTP endpoint: {}", endpointUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        try {
            return restTemplate.postForObject(endpointUrl, entity, String.class);
        } catch (Exception e) {
            logger.error("Error invoking HTTP endpoint: {}", endpointUrl, e);
            // Optional: retry logic or endpoint refresh logic could go here
            throw new RuntimeException("HTTP inference failed for URL: " + endpointUrl, e);
        }
    }
}
