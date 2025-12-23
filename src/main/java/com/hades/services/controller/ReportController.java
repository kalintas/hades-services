package com.hades.services.controller;

import com.hades.services.model.DroneImage;
import com.hades.services.model.Drone;
import com.hades.services.model.Earthquake;
import com.hades.services.model.Report;
import com.hades.services.repository.DroneRepository;
import com.hades.services.repository.EarthquakeRepository;
import com.hades.services.service.AwsFileService;
import com.hades.services.service.DroneImageService;
import com.hades.services.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final DroneImageService droneImageService;
    private final AwsFileService awsFileService;
    private final DroneRepository droneRepository;
    private final EarthquakeRepository earthquakeRepository;

    /**
     * Get all reports with pagination and filters
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Report> reportPage = reportService.getReportsWithFilters(eventName, status, search, pageable);

        List<Map<String, Object>> enrichedReports = reportPage.getContent().stream()
                .map(this::enrichReport)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("reports", enrichedReports);
        response.put("currentPage", reportPage.getNumber());
        response.put("totalPages", reportPage.getTotalPages());
        response.put("totalElements", reportPage.getTotalElements());
        response.put("hasMore", reportPage.hasNext());

        return ResponseEntity.ok(response);
    }

    /**
     * Get distinct event names for filtering dropdown
     */
    @GetMapping("/events")
    public ResponseEntity<List<String>> getEventNames() {
        return ResponseEntity.ok(reportService.getDistinctEventNames());
    }

    /**
     * Get a single report by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReportById(@PathVariable UUID id) {
        return reportService.getReportById(id)
                .map(report -> ResponseEntity.ok(enrichReport(report)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new report
     */
    @PostMapping
    public ResponseEntity<?> createReport(@RequestBody CreateReportRequest request) {
        try {
            DroneImage droneImage = droneImageService.getById(request.droneImageId)
                    .orElse(null);

            if (droneImage == null) {
                return ResponseEntity.badRequest().body("Drone image not found");
            }

            if (reportService.hasReportForImage(request.droneImageId)) {
                return ResponseEntity.badRequest().body("Report already exists for this image");
            }

            Earthquake earthquake = earthquakeRepository.findById(droneImage.getEarthquakeId()).orElse(null);
            Drone drone = droneRepository.findById(droneImage.getDroneId()).orElse(null);

            Report report = new Report(
                    request.droneImageId,
                    droneImage.getEarthquakeId(),
                    earthquake != null ? earthquake.getName() : "Unknown Event",
                    droneImage.getDroneId(),
                    drone != null ? drone.getName() : "Unknown Drone",
                    request.location != null ? request.location : droneImage.getNeighborhood(),
                    droneImage.getFileName(),
                    request.title,
                    request.report,
                    request.collapsedBuildings,
                    request.damagedStructures,
                    request.blockedRoads,
                    request.severityScore);

            Report savedReport = reportService.createReport(report);
            return ResponseEntity.ok(enrichReport(savedReport));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to create report: " + e.getMessage());
        }
    }

    /**
     * Get drone images that don't have reports yet
     */
    @GetMapping("/pending-images")
    public ResponseEntity<List<Map<String, Object>>> getPendingImages() {
        List<DroneImage> images = reportService.getImagesWithoutReports();

        List<Map<String, Object>> enrichedImages = images.stream().map(image -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", image.getId());
            map.put("earthquakeId", image.getEarthquakeId());
            map.put("droneId", image.getDroneId());
            map.put("neighborhood", image.getNeighborhood());
            map.put("fileName", image.getFileName());
            map.put("filePath", image.getFilePath());
            map.put("uploadedAt", image.getUploadedAt());

            try {
                String imageUrl = awsFileService.generateGetPresignedUrl(image.getFilePath());
                map.put("imageUrl", imageUrl);
            } catch (Exception e) {
                map.put("imageUrl", null);
            }

            earthquakeRepository.findById(image.getEarthquakeId()).ifPresent(earthquake -> {
                map.put("earthquakeName", earthquake.getName());
                map.put("earthquakeLocation", earthquake.getLocation());
                map.put("earthquakeMagnitude", earthquake.getMagnitude());
            });

            droneRepository.findById(image.getDroneId()).ifPresent(drone -> {
                map.put("droneName", drone.getName());
            });

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(enrichedImages);
    }

    /**
     * Delete a report
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID id) {
        reportService.deleteReport(id);
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> enrichReport(Report report) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", report.getId());
        map.put("droneImageId", report.getDroneImageId());
        map.put("earthquakeId", report.getEarthquakeId());
        map.put("eventName", report.getEventName());
        map.put("droneId", report.getDroneId());
        map.put("droneName", report.getDroneName());
        map.put("location", report.getLocation());
        map.put("imageName", report.getImageName());
        map.put("title", report.getTitle());
        map.put("report", report.getReport());
        map.put("collapsedBuildings", report.getCollapsedBuildings());
        map.put("damagedStructures", report.getDamagedStructures());
        map.put("blockedRoads", report.getBlockedRoads());
        map.put("severityScore", report.getSeverityScore());
        map.put("status", report.getStatus().name().toLowerCase());
        map.put("createdAt", report.getCreatedAt());

        if (report.getDroneImageId() != null) {
            droneImageService.getById(report.getDroneImageId()).ifPresent(image -> {
                try {
                    String imageUrl = awsFileService.generateGetPresignedUrl(image.getFilePath());
                    map.put("imageUrl", imageUrl);
                } catch (Exception e) {
                    map.put("imageUrl", null);
                }
            });
        }

        return map;
    }

    public static class CreateReportRequest {
        public UUID droneImageId;
        public String title;
        public String location;
        public String report;
        public Integer collapsedBuildings;
        public Integer damagedStructures;
        public Integer blockedRoads;
        public Double severityScore;
    }
}
