package com.hades.services.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    public enum ReportStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Link to the drone image this report is for
    private UUID droneImageId;

    // Associated earthquake and event info
    private UUID earthquakeId;
    private String eventName;

    // Drone info
    private UUID droneId;
    private String droneName;

    // Location and image info
    private String location;
    private String imageName;
    private String imageUrl;

    // Report content
    private String title;
    private String description;
    private String report;

    // Analysis metrics
    private Integer collapsedBuildings = 0;
    private Integer damagedStructures = 0;
    private Integer blockedRoads = 0;
    private Double severityScore = 0.0;

    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();
    private UUID authorId;

    public Report(UUID droneImageId, UUID earthquakeId, String eventName, UUID droneId, String droneName,
            String location, String imageName, String title, String report,
            Integer collapsedBuildings, Integer damagedStructures, Integer blockedRoads, Double severityScore) {
        this.droneImageId = droneImageId;
        this.earthquakeId = earthquakeId;
        this.eventName = eventName;
        this.droneId = droneId;
        this.droneName = droneName;
        this.location = location;
        this.imageName = imageName;
        this.title = title;
        this.report = report;
        this.collapsedBuildings = collapsedBuildings;
        this.damagedStructures = damagedStructures;
        this.blockedRoads = blockedRoads;
        this.severityScore = severityScore;
        this.status = ReportStatus.COMPLETED;
        this.createdAt = LocalDateTime.now();
    }
}
