package com.hades.services.repository;

import com.hades.services.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByDroneImageId(UUID droneImageId);

    boolean existsByDroneImageId(UUID droneImageId);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByEarthquakeIdOrderByCreatedAtDesc(UUID earthquakeId);

    List<Report> findByStatusOrderByCreatedAtDesc(Report.ReportStatus status);

    // Paginated queries
    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Report> findByEarthquakeIdOrderByCreatedAtDesc(UUID earthquakeId, Pageable pageable);

    @Query(value = "SELECT * FROM reports r WHERE " +
            "(:eventName IS NULL OR r.event_name = :eventName) AND " +
            "(:status IS NULL OR r.status = CAST(:status AS VARCHAR)) AND " +
            "(:search IS NULL OR r.image_name ILIKE CONCAT('%', :search, '%') OR " +
            "r.location ILIKE CONCAT('%', :search, '%') OR " +
            "CAST(r.report AS TEXT) ILIKE CONCAT('%', :search, '%')) " +
            "ORDER BY r.created_at DESC", countQuery = "SELECT COUNT(*) FROM reports r WHERE " +
                    "(:eventName IS NULL OR r.event_name = :eventName) AND " +
                    "(:status IS NULL OR r.status = CAST(:status AS VARCHAR)) AND " +
                    "(:search IS NULL OR r.image_name ILIKE CONCAT('%', :search, '%') OR " +
                    "r.location ILIKE CONCAT('%', :search, '%') OR " +
                    "CAST(r.report AS TEXT) ILIKE CONCAT('%', :search, '%'))", nativeQuery = true)
    Page<Report> findWithFilters(
            @Param("eventName") String eventName,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT DISTINCT r.eventName FROM Report r WHERE r.eventName IS NOT NULL ORDER BY r.eventName")
    List<String> findDistinctEventNames();
}
