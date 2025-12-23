package com.hades.services.service;

import com.hades.services.model.DroneImage;
import com.hades.services.model.Report;
import com.hades.services.repository.DroneImageRepository;
import com.hades.services.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final DroneImageRepository droneImageRepository;

    public Report createReport(Report report) {
        return reportRepository.save(report);
    }

    public List<Report> getAllReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc();
    }

    public Page<Report> getReportsPaginated(Pageable pageable) {
        return reportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<Report> getReportsWithFilters(String eventName, String status, String search, Pageable pageable) {
        String statusValue = null;
        if (status != null && !status.isEmpty()) {
            statusValue = status.toUpperCase();
        }

        return reportRepository.findWithFilters(
                eventName != null && !eventName.isEmpty() ? eventName : null,
                statusValue,
                search != null && !search.isEmpty() ? search : null,
                pageable);
    }

    public List<String> getDistinctEventNames() {
        return reportRepository.findDistinctEventNames();
    }

    public Optional<Report> getReportById(UUID id) {
        return reportRepository.findById(id);
    }

    public Optional<Report> getReportByDroneImageId(UUID droneImageId) {
        return reportRepository.findByDroneImageId(droneImageId);
    }

    public List<Report> getReportsByEarthquake(UUID earthquakeId) {
        return reportRepository.findByEarthquakeIdOrderByCreatedAtDesc(earthquakeId);
    }

    public boolean hasReportForImage(UUID droneImageId) {
        return reportRepository.existsByDroneImageId(droneImageId);
    }

    public List<DroneImage> getImagesWithoutReports() {
        List<DroneImage> allImages = droneImageRepository.findAllByOrderByUploadedAtDesc();

        return allImages.stream()
                .filter(image -> !reportRepository.existsByDroneImageId(image.getId()))
                .collect(Collectors.toList());
    }

    public void deleteReport(UUID id) {
        reportRepository.deleteById(id);
    }
}
