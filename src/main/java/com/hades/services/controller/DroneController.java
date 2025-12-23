package com.hades.services.controller;

import com.hades.services.model.Drone;
import com.hades.services.repository.DroneImageRepository;
import com.hades.services.service.DroneService;
import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/drones")
@RequiredArgsConstructor
public class DroneController {

    private final DroneService droneService;
    private final DroneImageRepository droneImageRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll(@RequestParam(required = false) String search) {
        List<Drone> drones;
        if (search != null && !search.isBlank()) {
            drones = droneService.search(search);
        } else {
            drones = droneService.getAll();
        }

        // Add image counts from drone_images table
        List<Map<String, Object>> result = new ArrayList<>();
        for (Drone drone : drones) {
            Map<String, Object> droneMap = new HashMap<>();
            droneMap.put("id", drone.getId());
            droneMap.put("name", drone.getName());
            droneMap.put("model", drone.getModel());
            droneMap.put("serialNumber", drone.getSerialNumber());
            droneMap.put("status", drone.getStatus());
            droneMap.put("battery", drone.getBattery());
            droneMap.put("altitude", drone.getAltitude());
            droneMap.put("lastUsed", drone.getLastUsed());
            droneMap.put("createdAt", drone.getCreatedAt());
            // Get actual image count from drone_images table
            droneMap.put("imageCount", droneImageRepository.countByDroneId(drone.getId()));
            result.add(droneMap);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Drone> getById(@PathVariable UUID id) {
        return droneService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @RolesAllowed("ADMIN")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> payload) {
        try {
            System.out.println("[DEBUG] Creating drone with payload: " + payload);

            Drone drone = new Drone(
                    (String) payload.get("name"),
                    (String) payload.get("model"),
                    (String) payload.get("serialNumber"));

            if (payload.containsKey("status")) {
                drone.setStatus(Drone.DroneStatus.valueOf((String) payload.get("status")));
            }
            if (payload.containsKey("battery")) {
                drone.setBattery(Integer.parseInt(payload.get("battery").toString()));
            }
            if (payload.containsKey("altitude")) {
                drone.setAltitude(Integer.parseInt(payload.get("altitude").toString()));
            }

            Drone saved = droneService.create(drone);
            System.out.println("[DEBUG] Drone created successfully: " + saved.getId());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to create drone: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ResponseEntity<Drone> update(@PathVariable UUID id, @RequestBody Drone drone) {
        try {
            Drone updated = droneService.update(id, drone);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        droneService.delete(id);
        return ResponseEntity.ok().build();
    }
}
