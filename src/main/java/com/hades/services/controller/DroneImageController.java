package com.hades.services.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.hades.services.model.DroneImage;
import com.hades.services.model.User;
import com.hades.services.repository.UserRepository;
import com.hades.services.service.AwsFileService;
import com.hades.services.service.DroneImageService;
import com.hades.services.service.DroneService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class DroneImageController {

    private final DroneImageService droneImageService;
    private final DroneService droneService;
    private final UserRepository userRepository;
    private final AwsFileService awsFileService;

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("hades_session".equals(cookie.getName())) {
                    try {
                        FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(cookie.getValue());
                        String email = token.getEmail();
                        return userRepository.findByEmail(email)
                                .map(User::getId)
                                .orElse(null);
                    } catch (Exception e) {
                        System.err.println("Failed to verify token: " + e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(required = false) UUID earthquakeId,
            @RequestParam(required = false) UUID droneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        List<DroneImage> images;
        if (earthquakeId != null) {
            images = droneImageService.getByEarthquake(earthquakeId);
        } else if (droneId != null) {
            images = droneImageService.getByDrone(droneId);
        } else {
            images = droneImageService.getAll();
        }

        int totalElements = images.size();
        int start = page * size;
        int end = Math.min(start + size, totalElements);

        List<DroneImage> paged = (start < totalElements) ? images.subList(start, end) : List.of();

        // Add presigned URLs to responses
        List<Map<String, Object>> result = paged.stream().map(img -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", img.getId());
            map.put("earthquakeId", img.getEarthquakeId());
            map.put("droneId", img.getDroneId());
            map.put("neighborhood", img.getNeighborhood());
            map.put("fileName", img.getFileName());
            map.put("filePath", img.getFilePath());
            map.put("uploadedAt", img.getUploadedAt());
            map.put("status", img.getStatus());
            // Generate presigned URL for viewing
            try {
                String presignedUrl = awsFileService.generateGetPresignedUrl(img.getFilePath());
                map.put("imageUrl", presignedUrl);
            } catch (Exception e) {
                map.put("imageUrl", null);
            }
            return map;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("images", result);
        response.put("totalElements", totalElements);
        response.put("page", page);
        response.put("size", size);
        response.put("hasMore", end < totalElements);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DroneImage> getById(@PathVariable UUID id) {
        return droneImageService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("earthquakeId") UUID earthquakeId,
            @RequestParam("droneId") UUID droneId,
            @RequestParam("neighborhood") String neighborhood,
            HttpServletRequest request) {

        UUID userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            List<DroneImage> uploadedImages = new ArrayList<>();
            for (MultipartFile file : files) {
                DroneImage image = droneImageService.uploadImage(file, earthquakeId, droneId, neighborhood, userId);
                uploadedImages.add(image);
            }
            return ResponseEntity.ok(uploadedImages);
        } catch (Exception e) {
            System.err.println("Upload error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        droneImageService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ============= Drone Simulation Endpoints =============

    @GetMapping("/active-drones")
    public ResponseEntity<List<Map<String, Object>>> getActiveDrones() {
        List<Map<String, Object>> drones = droneService.getActiveDrones().stream()
                .map(drone -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", drone.getId());
                    map.put("name", drone.getName());
                    map.put("model", drone.getModel());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(drones);
    }

    @PostMapping("/drone-upload")
    public ResponseEntity<?> droneUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("earthquakeId") UUID earthquakeId,
            @RequestParam("droneId") UUID droneId,
            @RequestParam(value = "neighborhood", defaultValue = "Unknown") String neighborhood) {

        try {
            List<DroneImage> uploadedImages = new ArrayList<>();
            for (MultipartFile file : files) {
                // Use null for userId since this is a drone upload
                DroneImage image = droneImageService.uploadImage(file, earthquakeId, droneId, neighborhood, null);
                uploadedImages.add(image);
            }
            return ResponseEntity.ok(uploadedImages);
        } catch (Exception e) {
            System.err.println("Drone upload error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Drone upload failed: " + e.getMessage());
        }
    }
}
