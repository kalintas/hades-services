package com.hades.services.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
@AllArgsConstructor
public class PresignedUrl {
    private String url;
    private Duration expiration;
    private String publicUrl;
}
