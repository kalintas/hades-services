package com.hades.services.controller;

import com.hades.services.model.Earthquake;
import com.hades.services.service.EarthquakeService;
import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/earthquakes")
@RequiredArgsConstructor
public class EarthquakeController {

    private final EarthquakeService earthquakeService;

    @GetMapping
    public ResponseEntity<List<Earthquake>> getAll(@RequestParam(required = false) String search) {
        List<Earthquake> earthquakes;
        if (search != null && !search.isBlank()) {
            earthquakes = earthquakeService.search(search);
        } else {
            earthquakes = earthquakeService.getAll();
        }
        return ResponseEntity.ok(earthquakes);
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
