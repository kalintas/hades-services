package com.hades.services.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "drones")
public class Drone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String serialNumber;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String status; // e.g., "IDLE", "ACTIVE", "MAINTENANCE" (Can be enum later)

    public Drone() {
    }

    public Drone(String serialNumber, String model, String status) {
        this.serialNumber = serialNumber;
        this.model = model;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
