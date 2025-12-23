package com.hades.services.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "drone_images")
@Data
@NoArgsConstructor
public class DroneImage {

    public enum ImageStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID earthquakeId;

    @Column(nullable = false)
    private UUID droneId;

    @Column(nullable = false)
    private String neighborhood;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String filePath;

    private Long fileSize;

    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageStatus status = ImageStatus.PENDING;

    private String analysisResult;

    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(nullable = false)
    private UUID uploadedBy;

    public DroneImage(UUID earthquakeId, UUID droneId, String neighborhood, String fileName, String filePath,
            Long fileSize, String mimeType, UUID uploadedBy) {
        this.earthquakeId = earthquakeId;
        this.droneId = droneId;
        this.neighborhood = neighborhood;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
    }
}
