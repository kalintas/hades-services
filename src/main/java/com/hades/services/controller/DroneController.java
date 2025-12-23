package com.hades.services.controller;

import com.hades.services.model.Drone;
import com.hades.services.model.User;
import com.hades.services.repository.DroneImageRepository;
import com.hades.services.service.DroneService;
import com.hades.services.service.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
    private final UserService userService;

    private Optional<User> getCurrentUser(HttpServletRequest request) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies == null)
                return Optional.empty();

            String token = null;
            for (Cookie cookie : cookies) {
                if ("hades_session".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
            if (token == null)
                return Optional.empty();

            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
            return userService.findByFirebaseUid(decodedToken.getUid());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

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
            droneMap.put("createdBy", drone.getCreatedBy());
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
    @RolesAllowed({ "ADMIN", "MANAGER" })
    public ResponseEntity<?> create(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        try {
            Optional<User> currentUserOpt = getCurrentUser(request);
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            User currentUser = currentUserOpt.get();

            System.out.println("[DEBUG] Creating drone with payload: " + payload + " by user: " + currentUser.getId());

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

            // Set createdBy to track ownership
            drone.setCreatedBy(currentUser.getId());

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
    @RolesAllowed({ "ADMIN", "MANAGER" })
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Drone drone, HttpServletRequest request) {
        try {
            Optional<User> currentUserOpt = getCurrentUser(request);
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            User currentUser = currentUserOpt.get();

            // Check ownership for MANAGERs
            if (currentUser.getRole().name().equals("MANAGER")) {
                Optional<Drone> existingDrone = droneService.getById(id);
                if (existingDrone.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                if (!currentUser.getId().equals(existingDrone.get().getCreatedBy())) {
                    return ResponseEntity.status(403).body("You can only edit drones you created");
                }
            }

            Drone updated = droneService.update(id, drone);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({ "ADMIN", "MANAGER" })
    public ResponseEntity<?> delete(@PathVariable UUID id, HttpServletRequest request) {
        try {
            Optional<User> currentUserOpt = getCurrentUser(request);
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            User currentUser = currentUserOpt.get();

            // Check ownership for MANAGERs
            if (currentUser.getRole().name().equals("MANAGER")) {
                Optional<Drone> existingDrone = droneService.getById(id);
                if (existingDrone.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                if (!currentUser.getId().equals(existingDrone.get().getCreatedBy())) {
                    return ResponseEntity.status(403).body("You can only delete drones you created");
                }
            }

            droneService.delete(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
