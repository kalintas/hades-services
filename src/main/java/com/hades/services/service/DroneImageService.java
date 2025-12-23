package com.hades.services.service;

import com.hades.services.model.DroneImage;
import com.hades.services.repository.DroneImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DroneImageService {

    private final DroneImageRepository droneImageRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public DroneImage uploadImage(MultipartFile file, UUID earthquakeId, UUID droneId, String neighborhood, UUID userId)
            throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName != null && originalFileName.contains(".")
                ? originalFileName.substring(originalFileName.lastIndexOf("."))
                : "";
        String uniqueFileName = UUID.randomUUID().toString() + extension;

        // Save file to disk
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath);

        // Save metadata to database
        DroneImage image = new DroneImage(
                earthquakeId,
                droneId,
                neighborhood,
                originalFileName,
                filePath.toString(),
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
            // Delete file from disk
            try {
                Files.deleteIfExists(Paths.get(image.getFilePath()));
            } catch (IOException e) {
                // Log error but continue with database deletion
                System.err.println("Failed to delete file: " + e.getMessage());
            }
            droneImageRepository.deleteById(id);
        });
    }
}
