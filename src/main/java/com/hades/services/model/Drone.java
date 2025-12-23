package com.hades.services.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "drones")
@Data
@NoArgsConstructor
public class Drone {

    public enum DroneStatus {
        ACTIVE, INACTIVE, MAINTENANCE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String model;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DroneStatus status = DroneStatus.ACTIVE;

    private Integer battery = 100;

    private Integer altitude = 0;

    private Integer imageCount = 0;

    private LocalDateTime lastUsed;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;

    public Drone(String name, String model, String serialNumber) {
        this.name = name;
        this.model = model;
        this.serialNumber = serialNumber != null ? serialNumber
                : "SN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.createdAt = LocalDateTime.now();
        this.lastUsed = LocalDateTime.now();
    }
}
