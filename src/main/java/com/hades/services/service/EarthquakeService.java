package com.hades.services.service;

import com.hades.services.model.Earthquake;
import com.hades.services.repository.EarthquakeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EarthquakeService {

    private final EarthquakeRepository earthquakeRepository;

    public Earthquake create(Earthquake earthquake) {
        return earthquakeRepository.save(earthquake);
    }

    public List<Earthquake> getAll() {
        return earthquakeRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Earthquake> search(String query) {
        return earthquakeRepository.findByNameContainingIgnoreCaseOrLocationContainingIgnoreCase(query, query);
    }

    public Optional<Earthquake> getById(UUID id) {
        return earthquakeRepository.findById(id);
    }

    public Earthquake update(UUID id, Earthquake updated) {
        return earthquakeRepository.findById(id).map(eq -> {
            eq.setName(updated.getName());
            eq.setMagnitude(updated.getMagnitude());
            eq.setLocation(updated.getLocation());
            eq.setDate(updated.getDate());
            eq.setStatus(updated.getStatus());
            eq.setCollapsed(updated.getCollapsed());
            eq.setDamaged(updated.getDamaged());
            eq.setBlocked(updated.getBlocked());
            eq.setImages(updated.getImages());
            return earthquakeRepository.save(eq);
        }).orElseThrow(() -> new RuntimeException("Earthquake not found"));
    }

    public void delete(UUID id) {
        earthquakeRepository.deleteById(id);
    }
}
