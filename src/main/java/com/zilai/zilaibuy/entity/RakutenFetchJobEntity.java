package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "rakuten_fetch_job")
@Getter
@Setter
public class RakutenFetchJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", nullable = false, length = 200)
    private String keyword;

    @Column(name = "pages")
    private Integer pages;

    @Column(name = "items_saved")
    private Integer itemsSaved;

    @Column(name = "items_updated")
    private Integer itemsUpdated;

    @Column(name = "status", nullable = false, length = 16)
    private String status; // SUCCESS or FAILED

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;
}
