package com.hades.services.repository;

import com.hades.services.model.Drone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DroneRepository extends JpaRepository<Drone, UUID> {
    List<Drone> findByNameContainingIgnoreCaseOrModelContainingIgnoreCase(String name, String model);

    List<Drone> findAllByOrderByCreatedAtDesc();

    List<Drone> findByStatus(Drone.DroneStatus status);
}
