package com.hades.services.model;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String description;

    // Bytes loaded so far
    private long totalFileBytes;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> fileIds = new HashSet<>();

    private String previewImageUrl;
    private String content; // Could be markdown or rich text containing file references

    private LocalDateTime createdAt = LocalDateTime.now();
    private UUID authorId;
}
