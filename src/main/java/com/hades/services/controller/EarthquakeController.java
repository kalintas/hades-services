package com.hades.services.controller;

import com.hades.services.model.Earthquake;
import com.hades.services.repository.DroneImageRepository;
import com.hades.services.service.EarthquakeService;
import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/earthquakes")
@RequiredArgsConstructor
public class EarthquakeController {

    private final EarthquakeService earthquakeService;
    private final DroneImageRepository droneImageRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll(@RequestParam(required = false) String search) {
        List<Earthquake> earthquakes;
        if (search != null && !search.isBlank()) {
            earthquakes = earthquakeService.search(search);
        } else {
            earthquakes = earthquakeService.getAll();
        }

        // Add image counts from drone_images table
        List<Map<String, Object>> result = new ArrayList<>();
        for (Earthquake eq : earthquakes) {
            Map<String, Object> eqMap = new HashMap<>();
            eqMap.put("id", eq.getId());
            eqMap.put("name", eq.getName());
            eqMap.put("magnitude", eq.getMagnitude());
            eqMap.put("location", eq.getLocation());
            eqMap.put("date", eq.getDate());
            eqMap.put("status", eq.getStatus());
            eqMap.put("collapsed", eq.getCollapsed());
            eqMap.put("damaged", eq.getDamaged());
            eqMap.put("blocked", eq.getBlocked());
            eqMap.put("createdAt", eq.getCreatedAt());
            // Get actual image count from drone_images table
            eqMap.put("images", droneImageRepository.countByEarthquakeId(eq.getId()));
            result.add(eqMap);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Earthquake> getById(@PathVariable UUID id) {
        return earthquakeService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @RolesAllowed("ADMIN")
    public ResponseEntity<Earthquake> create(@RequestBody Map<String, Object> payload) {
        Earthquake earthquake = new Earthquake(
                (String) payload.get("name"),
                Double.parseDouble(payload.get("magnitude").toString()),
                (String) payload.get("location"),
                LocalDate.parse((String) payload.get("date")));

        if (payload.containsKey("status")) {
            earthquake.setStatus(Earthquake.EarthquakeStatus.valueOf((String) payload.get("status")));
        }

        Earthquake saved = earthquakeService.create(earthquake);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ResponseEntity<Earthquake> update(@PathVariable UUID id, @RequestBody Earthquake earthquake) {
        try {
            Earthquake updated = earthquakeService.update(id, earthquake);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        earthquakeService.delete(id);
        return ResponseEntity.ok().build();
    }
}
