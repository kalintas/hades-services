package com.hades.services.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.hades.services.model.DroneImage;
import com.hades.services.model.User;
import com.hades.services.repository.UserRepository;
import com.hades.services.service.DroneImageService;
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
    private final UserRepository userRepository;

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
    public ResponseEntity<List<DroneImage>> getAll(
            @RequestParam(required = false) UUID earthquakeId,
            @RequestParam(required = false) UUID droneId) {
        List<DroneImage> images;
        if (earthquakeId != null) {
            images = droneImageService.getByEarthquake(earthquakeId);
        } else if (droneId != null) {
            images = droneImageService.getByDrone(droneId);
        } else {
            images = droneImageService.getAll();
        }
        return ResponseEntity.ok(images);
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
}
