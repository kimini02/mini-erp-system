package com.minierp.backend.domain.org.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "holidays")
@Getter
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private java.time.LocalDate holidayDate;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private boolean substitute;
}
