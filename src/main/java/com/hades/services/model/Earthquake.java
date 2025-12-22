package com.hades.services.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "earthquakes")
@Getter
@Setter
@NoArgsConstructor
public class Earthquake {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double magnitude;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EarthquakeStatus status = EarthquakeStatus.PENDING;

    @Column(nullable = false)
    private Integer collapsed = 0;

    @Column(nullable = false)
    private Integer damaged = 0;

    @Column(nullable = false)
    private Integer blocked = 0;

    @Column(nullable = false)
    private Integer images = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Earthquake(String name, Double magnitude, String location, LocalDate date) {
        this.name = name;
        this.magnitude = magnitude;
        this.location = location;
        this.date = date;
        this.createdAt = LocalDateTime.now();
    }

    public enum EarthquakeStatus {
        PENDING, ACTIVE, COMPLETED
    }
}
