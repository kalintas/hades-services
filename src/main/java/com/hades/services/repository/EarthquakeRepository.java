package com.hades.services.repository;

import com.hades.services.model.Earthquake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EarthquakeRepository extends JpaRepository<Earthquake, UUID> {
    List<Earthquake> findByNameContainingIgnoreCaseOrLocationContainingIgnoreCase(String name, String location);

    List<Earthquake> findAllByOrderByCreatedAtDesc();
}
