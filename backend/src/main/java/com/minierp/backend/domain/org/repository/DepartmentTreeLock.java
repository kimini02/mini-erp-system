package com.minierp.backend.domain.org.repository;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** Shared by organization changes and employee changes that can invalidate a department head. */
@Repository @RequiredArgsConstructor
public class DepartmentTreeLock {
    private final SqlStore db;

    @Transactional(propagation=Propagation.MANDATORY)
    public void lock() {
        db.rows("SELECT id FROM departments WHERE parent_id IS NULL ORDER BY id FOR UPDATE",Map.of());
    }
}
