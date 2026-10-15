package com.minierp.backend.domain.org.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name="work_schedules")
@Getter
public class WorkSchedule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private java.time.LocalTime startTime;
    @Column(nullable = false)
    private java.time.LocalTime endTime;
    @Column(nullable = false)
    private int lateGraceMin;
    @Column(nullable = false)
    private boolean isDefault;
}
