package com.hades.services.service.sagemaker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.service.VisionModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

public abstract class AbstractSageMakerService<ParameterType, ReturnType> implements VisionModelService<ParameterType, ReturnType> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final SageMakerRuntimeClient sageMakerClient;
    protected final ObjectMapper objectMapper;

    protected AbstractSageMakerService(ObjectMapper objectMapper, SageMakerRuntimeClient sageMakerClient) {
        this.objectMapper = objectMapper;
        this.sageMakerClient = sageMakerClient;
    }

    /**
     * The shared logic for hitting AWS SageMaker.
     */
    protected String invokeAwsEndpoint(String endpointName, String jsonPayload) {
        logger.debug("Invoking SageMaker endpoint: {}", endpointName);

        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                .endpointName(endpointName)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(jsonPayload))
                .build();

        try {
            InvokeEndpointResponse response = sageMakerClient.invokeEndpoint(request);
            // Return the raw string directly! No JsonNode conversion needed here anymore.
            return response.body().asUtf8String();
        } catch (Exception e) {
            logger.error("Error invoking SageMaker endpoint: {}", endpointName, e);
            throw new RuntimeException("SageMaker invocation failed for endpoint: " + endpointName, e);
        }
    }
}