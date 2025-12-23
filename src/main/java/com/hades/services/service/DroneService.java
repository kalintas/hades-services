package com.hades.services.service;

import com.hades.services.model.Drone;
import com.hades.services.repository.DroneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DroneService {

    private final DroneRepository droneRepository;

    public Drone create(Drone drone) {
        return droneRepository.save(drone);
    }

    public List<Drone> getAll() {
        return droneRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Drone> getActiveDrones() {
        return droneRepository.findByStatus(Drone.DroneStatus.ACTIVE);
    }

    public List<Drone> search(String query) {
        return droneRepository.findByNameContainingIgnoreCaseOrModelContainingIgnoreCase(query, query);
    }

    public Optional<Drone> getById(UUID id) {
        return droneRepository.findById(id);
    }

    public Drone update(UUID id, Drone updated) {
        return droneRepository.findById(id).map(drone -> {
            drone.setName(updated.getName());
            drone.setModel(updated.getModel());
            drone.setStatus(updated.getStatus());
            drone.setBattery(updated.getBattery());
            drone.setAltitude(updated.getAltitude());
            drone.setImageCount(updated.getImageCount());
            drone.setLastUsed(LocalDateTime.now());
            return droneRepository.save(drone);
        }).orElseThrow(() -> new RuntimeException("Drone not found"));
    }

    public void delete(UUID id) {
        droneRepository.deleteById(id);
    }
}
