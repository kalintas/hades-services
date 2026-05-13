package com.hades.services.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "yolo_inferences")
@Getter
@Setter
@NoArgsConstructor
public class YoloInference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID messageId;

    @Column(columnDefinition = "TEXT")
    private String detectionsJson;

    private Integer imageWidth;
    private Integer imageHeight;
    private Double inferenceTimeMs;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
