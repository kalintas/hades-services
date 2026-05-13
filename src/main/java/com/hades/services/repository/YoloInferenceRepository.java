package com.hades.services.repository;

import com.hades.services.model.YoloInference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface YoloInferenceRepository extends JpaRepository<YoloInference, UUID> {
}
