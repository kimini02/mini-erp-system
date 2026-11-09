package com.minierp.backend.domain.user.repository;

import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsByUserRole(UserRole userRole);
    boolean existsByLoginId(String loginId);
    boolean existsByUserEmail(String userEmail);
    Optional<User> findByLoginId(String loginId);
    Optional<User> findByUserEmail(String userEmail);
    List<User> findByUserRole(UserRole userRole);
}
