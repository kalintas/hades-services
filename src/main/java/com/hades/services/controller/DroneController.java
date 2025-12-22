package com.hades.services.controller;

import com.hades.services.model.Drone;
import com.hades.services.service.DroneService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/drones")
public class DroneController {

    private final DroneService droneService;

    public DroneController(DroneService droneService) {
        this.droneService = droneService;
    }

    @GetMapping
    public ResponseEntity<List<Drone>> getAllDrones() {
        return ResponseEntity.ok(droneService.getAllDrones());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Drone> addDrone(@RequestBody Drone drone) {
        return ResponseEntity.ok(droneService.addDrone(drone));
    }
}
