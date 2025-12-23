package com.hades.services.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresignedUrlRequest {
    private String folder;
    private long fileSize;
}
