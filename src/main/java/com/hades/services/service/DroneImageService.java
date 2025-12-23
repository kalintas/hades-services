package com.hades.services.service;

import com.hades.services.model.DroneImage;
import com.hades.services.repository.DroneImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DroneImageService {

    private final DroneImageRepository droneImageRepository;
    private final AwsFileService awsFileService;

    private static final String S3_PREFIX = "drone-images/";

    public DroneImage uploadImage(MultipartFile file, UUID earthquakeId, UUID droneId, String neighborhood, UUID userId)
            throws IOException {
        // Generate unique filename
        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName != null && originalFileName.contains(".")
                ? originalFileName.substring(originalFileName.lastIndexOf("."))
                : "";
        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String s3Key = S3_PREFIX + uniqueFileName;

        // Upload file to S3
        awsFileService.uploadFile(s3Key, file.getBytes());

        // Save metadata to database
        DroneImage image = new DroneImage(
                earthquakeId,
                droneId,
                neighborhood,
                originalFileName,
                s3Key,
                file.getSize(),
                file.getContentType(),
                userId);

        return droneImageRepository.save(image);
    }

    public List<DroneImage> getAll() {
        return droneImageRepository.findAllByOrderByUploadedAtDesc();
    }

    public List<DroneImage> getByEarthquake(UUID earthquakeId) {
        return droneImageRepository.findByEarthquakeIdOrderByUploadedAtDesc(earthquakeId);
    }

    public List<DroneImage> getByDrone(UUID droneId) {
        return droneImageRepository.findByDroneIdOrderByUploadedAtDesc(droneId);
    }

    public Optional<DroneImage> getById(UUID id) {
        return droneImageRepository.findById(id);
    }

    public void updateStatus(UUID id, DroneImage.ImageStatus status) {
        droneImageRepository.findById(id).ifPresent(image -> {
            image.setStatus(status);
            droneImageRepository.save(image);
        });
    }

    public void delete(UUID id) {
        droneImageRepository.findById(id).ifPresent(image -> {
            // Delete file from S3
            try {
                awsFileService.deleteFile(image.getFilePath());
            } catch (Exception e) {
                // Log error but continue with database deletion
                System.err.println("Failed to delete file from S3: " + e.getMessage());
            }
            droneImageRepository.deleteById(id);
        });
    }
}
