package com.minierp.backend.domain.permission.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermission.Key.class)
public class RolePermission {
    @Id
    private Long roleId;
    @Id
    private Long menuId;
    @Column(nullable = false)
    private boolean canView;
    @Column(nullable = false)
    private boolean canCreate;
    @Column(nullable = false)
    private boolean canUpdate;
    @Column(nullable = false)
    private boolean canDelete;
    @Column(length = 20)
    private String dataScope;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long roleId;
        private Long menuId;
    }
}
