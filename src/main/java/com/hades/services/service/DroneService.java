package com.hades.services.service;

import com.hades.services.model.Drone;
import com.hades.services.repository.DroneRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DroneService {

    private final DroneRepository droneRepository;

    public DroneService(DroneRepository droneRepository) {
        this.droneRepository = droneRepository;
    }

    public List<Drone> getAllDrones() {
        return droneRepository.findAll();
    }

    public Drone addDrone(Drone drone) {
        if (droneRepository.findBySerialNumber(drone.getSerialNumber()).isPresent()) {
            throw new RuntimeException("Drone with serial number " + drone.getSerialNumber() + " already exists.");
        }
        return droneRepository.save(drone);
    }

    // Additional methods like updateStatus, delete, etc. can be added here
}
