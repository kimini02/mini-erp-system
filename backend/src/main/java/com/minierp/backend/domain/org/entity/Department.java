package com.minierp.backend.domain.org.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name="departments")
@Getter
public class Department {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column()
    private Long parentId;
    @Column()
    private Long headUserId;
    @Column()
    private Long workScheduleId;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean active;
}
