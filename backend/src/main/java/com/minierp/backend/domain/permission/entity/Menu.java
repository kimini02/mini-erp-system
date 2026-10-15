package com.minierp.backend.domain.permission.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name="menus")
@Getter
public class Menu {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 100)
    private String menuGroup;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false, length = 10)
    private String allowedActions;
    @Column(nullable = false)
    private boolean scopeApplicable;
}
