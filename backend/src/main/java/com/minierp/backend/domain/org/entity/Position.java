package com.minierp.backend.domain.org.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "positions")
@Getter
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(nullable = false)
    private int rankOrder;
}
