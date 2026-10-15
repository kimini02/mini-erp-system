package com.minierp.backend.domain.permission.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name="roles")
@Getter
public class Role {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private boolean isSystem;
}
