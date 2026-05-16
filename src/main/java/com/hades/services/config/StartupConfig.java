package com.hades.services.config;

import com.hades.services.service.sagemaker.InferenceEndpointProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StartupConfig {

    private static final Logger logger = LoggerFactory.getLogger(StartupConfig.class);

    @Bean
    public CommandLineRunner logVlmEndpoint(InferenceEndpointProvider provider) {
        return args -> {
            try {
                String endpoint = provider.getVlmEndpoint();
                logger.info("**********************************************************");
                logger.info("VLM Inference Endpoint (Colab) loaded: {}", endpoint);
                logger.info("**********************************************************");
            } catch (Exception e) {
                logger.error("**********************************************************");
                logger.error("WARNING: Could not load VLM endpoint from S3 at startup.");
                logger.error("Please ensure 's3://hades-general-bucket/vlm-endpoint' exists.");
                logger.error("**********************************************************");
            }
        };
    }
}
