package com.hades.services.repository;

import com.hades.services.model.DroneImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DroneImageRepository extends JpaRepository<DroneImage, UUID> {
    List<DroneImage> findByEarthquakeIdOrderByUploadedAtDesc(UUID earthquakeId);

    List<DroneImage> findByDroneIdOrderByUploadedAtDesc(UUID droneId);

    List<DroneImage> findByNeighborhoodContainingIgnoreCase(String neighborhood);

    List<DroneImage> findAllByOrderByUploadedAtDesc();

    long countByEarthquakeId(UUID earthquakeId);

    long countByDroneId(UUID droneId);
}
