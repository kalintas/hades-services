package com.hades.services.repository;

import com.hades.services.model.Drone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DroneRepository extends JpaRepository<Drone, UUID> {
    Optional<Drone> findBySerialNumber(String serialNumber);
}
